#!/usr/bin/env bash
# Build standalone distribution packages (EXE for Windows, AppImage for Linux, WASM for Web)
#
# Prerequisites (one-time setup):
#   pip install aqtinstall                  # or: python3 -m venv ~/.venv-aqt && pip install aqtinstall
#   aqt install-qt windows desktop 6.8.0 win64_mingw -m qtcharts -O ~/Qt
#   aqt install-qt linux   desktop 6.8.0 linux_gcc_64 -m qtcharts -O ~/Qt   # host moc/rcc/uic
#   aqt install-qt all_os  wasm 6.8.0 wasm_singlethread -O ~/Qt            # WebAssembly target
#   git clone --depth=1 https://github.com/emscripten-core/emsdk ~/emsdk
#   ~/emsdk/emsdk install 3.1.56 && ~/emsdk/emsdk activate 3.1.56
#   # Windows JDK (as JRE): download from https://adoptium.net/temurin/releases/?version=26&os=windows
#   # Linux JRE via jlink (see build_jre_linux below)
#   # appimagetool + linuxdeploy in ~/.local/bin (see fetch_appimage_tools below)
#
# Usage:
#   ./package.sh                # interactive prompt
#   ./package.sh linux          # build AppImage
#   ./package.sh windows        # build Windows dist
#   ./package.sh web            # build Web (WASM) dist
#   ./package.sh all            # build all
set -euo pipefail
cd "$(dirname "$0")"

TARGET="${1:-}"
ROOT="$(pwd)"
QT_WIN=~/Qt/6.8.0/mingw_64
QT_HOST=~/Qt/6.8.0/gcc_64
QT_WASM=~/Qt/6.8.0/wasm_singlethread
TOOLCHAIN="$ROOT/launcher/mingw-toolchain.cmake"
DIST_WIN="dist/windows"
DIST_LINUX="dist/linux"
DIST_WEB="dist/web"
APPDIR="AppDir"
APPIMAGE_NAME="LogisticsManager-x86_64.AppImage"

info()  { echo -e "\033[0;32m[INFO]\033[0m $*"; }
step()  { echo -e "\033[0;34m==>\033[0m $*"; }
warn()  { echo -e "\033[0;33m[WARN]\033[0m $*"; }
die()   { echo -e "\033[0;31m[ERROR]\033[0m $*" >&2; exit 1; }

# ─────────────────────────────────────────────────────────────
# 0. Build backend uber-jar (shared by both targets)
# ─────────────────────────────────────────────────────────────
build_backend() {
    step "Building server uber-jar..."
    (cd server && ./mvnw -q package -DskipTests)
    JAR=$(ls server/target/scheduler-backend-*.jar 2>/dev/null | grep -v original | head -1)
    [ -z "$JAR" ] && die "No uber-jar found!"
    info "JAR: $JAR"
    echo "$JAR"
}

# ─────────────────────────────────────────────────────────────
# 1. Linux native build (qtclient + launcher)
# ─────────────────────────────────────────────────────────────
build_linux_native() {
    step "Building qtclient (Linux)..."
    (cd qtclient && cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j"$(nproc)") \
        || die "qtclient (Linux) build failed."
    step "Building launcher (Linux)..."
    (cd launcher && cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j"$(nproc)") \
        || die "Launcher (Linux) build failed."
}

# ─────────────────────────────────────────────────────────────
# 2. Windows cross build (qtclient + launcher) via MinGW + aqt Qt6
# ─────────────────────────────────────────────────────────────
build_windows_cross() {
    [ -f "$TOOLCHAIN" ] || die "Toolchain not found: $TOOLCHAIN"
    [ -d "$QT_WIN" ]    || die "Windows Qt6 not found at $QT_WIN (run: aqt install-qt windows desktop 6.8.0 win64_mingw -m qtcharts -O ~/Qt)"
    [ -d "$QT_HOST" ]   || die "Host Qt6 not found at $QT_HOST (run: aqt install-qt linux desktop 6.8.0 linux_gcc_64 -m qtcharts -O ~/Qt)"
    step "Building qtclient (Windows, cross)..."
    (cd qtclient && cmake -B build-win -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DCMAKE_BUILD_TYPE=Release \
        && cmake --build build-win -j"$(nproc)") || die "qtclient (Windows) build failed."
    step "Building launcher (Windows, cross)..."
    (cd launcher && cmake -B build-win -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DCMAKE_BUILD_TYPE=Release \
        && cmake --build build-win -j"$(nproc)") || die "Launcher (Windows) build failed."
}

# ─────────────────────────────────────────────────────────────
# 2b. WASM build (qtclient for WebAssembly) via emsdk + Qt6 wasm
# ─────────────────────────────────────────────────────────────
build_wasm() {
    [ -d "$QT_WASM" ] || die "WASM Qt6 not found at $QT_WASM (run: aqt install-qt all_os wasm 6.8.0 wasm_singlethread -O ~/Qt)"
    [ -f ~/emsdk/emsdk_env.sh ] || die "emsdk not found at ~/emsdk (run: git clone https://github.com/emscripten-core/emsdk ~/emsdk && ~/emsdk/emsdk install 3.1.56 && ~/emsdk/emsdk activate 3.1.56)"
    # Activate emsdk in this shell
    source ~/emsdk/emsdk_env.sh
    step "Building qtclient (WASM)..."
    (cd qtclient && rm -rf build-wasm \
        && "$QT_WASM/bin/qt-cmake" -B build-wasm -DCMAKE_BUILD_TYPE=Release -DQT_HOST_PATH="$QT_HOST" \
        && cmake --build build-wasm -j"$(nproc)") || die "qtclient (WASM) build failed."
}

# ─────────────────────────────────────────────────────────────
# 2c. Assemble Web dist (copy WASM output to server resources, rebuild JAR)
# ─────────────────────────────────────────────────────────────
assemble_web() {
    step "Assembling Web dist..."
    rm -rf "$DIST_WEB" && mkdir -p "$DIST_WEB"

    # Copy WASM build artifacts into server resources (so they get packaged into the JAR)
    local WEB_RES="server/src/main/resources/web"
    rm -f "$WEB_RES"/index.html "$WEB_RES"/logistics_frontend.{html,js,wasm} "$WEB_RES"/qtloader.js
    cp qtclient/build-wasm/bin/logistics_frontend.html "$WEB_RES"/index.html
    cp qtclient/build-wasm/bin/logistics_frontend.js  "$WEB_RES"/
    cp qtclient/build-wasm/bin/logistics_frontend.wasm "$WEB_RES"/
    cp qtclient/build-wasm/bin/qtloader.js             "$WEB_RES"/ 2>/dev/null || true
    info "WASM artifacts copied to $WEB_RES (index.html = WASM entry)"

    # Rebuild JAR with WASM resources embedded
    step "Rebuilding server JAR with WASM resources..."
    (cd server && ./mvnw -q package -DskipTests) || die "Server rebuild failed."
    JAR=$(ls server/target/scheduler-backend-*.jar 2>/dev/null | grep -v original | head -1)
    [ -z "$JAR" ] && die "No uber-jar found after rebuild!"

    # Assemble dist: JAR + Linux JRE
    cp "$JAR" "$DIST_WEB"/scheduler-backend.jar
    cp -r jre/linux "$DIST_WEB"/jre 2>/dev/null || warn "Linux JRE not found; run ./package.sh linux first or ensure jre/linux exists."

    info "Web dist ready: $DIST_WEB  ($(du -sh "$DIST_WEB" | cut -f1))"
    info "To run: cd $DIST_WEB && jre/bin/java --enable-native-access=ALL-UNNAMED -jar scheduler-backend.jar"
    info "Then open http://localhost:8080 in a browser."
}

# ─────────────────────────────────────────────────────────────
# 3. Ensure JREs are present
# ─────────────────────────────────────────────────────────────
ensure_jre_linux() {
    if [ -x "jre/linux/bin/java" ] && jre/linux/bin/java --list-modules 2>/dev/null | grep -q "java.net.http"; then
        info "Linux JRE OK."
        return
    fi
    step "Building minimal Linux JRE via jlink..."
    JMODS=$(find /usr/lib/jvm -name "jmods" -type d 2>/dev/null | head -1)
    [ -n "$JMODS" ] || die "No JDK jmods found under /usr/lib/jvm (install a JDK 26)."
    rm -rf jre/linux
    jlink --module-path "$JMODS" \
          --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.net,jdk.unsupported \
          --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
          --output jre/linux
    info "Linux JRE built ($(du -sh jre/linux | cut -f1))."
}

ensure_jre_windows() {
    if [ -x "jre/windows/bin/java.exe" ] && WINEDEBUG=-all wine jre/windows/bin/java.exe --list-modules 2>/dev/null | grep -q "java.net.http"; then
        info "Windows JRE OK."
        return
    fi
    warn "Windows JRE missing or incomplete. Download Temurin JDK 26 (Windows x64) from"
    warn "  https://adoptium.net/temurin/releases/?version=26&os=windows&arch=x64&package=jdk"
    warn "and extract into: $ROOT/jre/windows  (so jre/windows/bin/java.exe exists)."
    die "Aborting: no Windows JRE."
}

# ─────────────────────────────────────────────────────────────
# 4. Assemble Windows dist + windeployqt
# ─────────────────────────────────────────────────────────────
assemble_windows() {
    local JAR="$1"
    step "Assembling Windows dist..."
    rm -rf "$DIST_WIN" && mkdir -p "$DIST_WIN"
    cp qtclient/build-win/bin/logistics_frontend.exe "$DIST_WIN"/
    cp launcher/build-win/logistics_launcher.exe "$DIST_WIN"/
    cp "$JAR" "$DIST_WIN"/scheduler-backend.jar
    cp -r jre/windows "$DIST_WIN"/jre

    if command -v wine >/dev/null 2>&1 && [ -x "$QT_WIN/bin/windeployqt.exe" ]; then
        step "Running windeployqt (via wine)..."
        (cd "$DIST_WIN" && WINEDEBUG=-all wine "$QT_WIN/bin/windeployqt.exe" \
            --release --no-translations --no-system-d3d-compiler --no-opengl-sw --compiler-runtime \
            logistics_frontend.exe logistics_launcher.exe) || warn "windeployqt had issues; manual DLL deployment may be needed."
        # windeployqt does not always detect Qt6Charts; copy it explicitly if linked.
        cp "$QT_WIN/bin/Qt6Charts.dll" "$DIST_WIN"/ 2>/dev/null || true
    else
        warn "wine or windeployqt.exe not found; skipping Qt DLL deployment."
        warn "Run windeployqt on a Windows host, or copy Qt6*.dll from $QT_WIN/bin manually."
    fi
    info "Windows dist ready: $DIST_WIN  ($(du -sh "$DIST_WIN" | cut -f1))"
}

# ─────────────────────────────────────────────────────────────
# 5. Assemble Linux AppImage
# ─────────────────────────────────────────────────────────────
fetch_appimage_tools() {
    mkdir -p ~/.local/bin
    local need=0
    [ -x ~/.local/bin/appimagetool ] || need=1
    [ -x ~/.local/bin/linuxdeploy ]  || need=1
    if [ "$need" -eq 0 ]; then info "AppImage tools present."; return; fi
    step "Downloading appimagetool + linuxdeploy..."
    curl -fsSL -o ~/.local/bin/appimagetool \
        "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    curl -fsSL -o ~/.local/bin/linuxdeploy \
        "https://github.com/linuxdeploy/linuxdeploy/releases/download/continuous/linuxdeploy-x86_64.AppImage"
    chmod +x ~/.local/bin/appimagetool ~/.local/bin/linuxdeploy
    info "AppImage tools installed to ~/.local/bin."
}

assemble_linux_appimage() {
    local JAR="$1"
    step "Assembling AppDir..."
    fetch_appimage_tools
    rm -rf "$APPDIR"
    mkdir -p "$APPDIR/usr/bin" "$APPDIR/usr/lib" \
             "$APPDIR/usr/share/applications" \
             "$APPDIR/usr/share/icons/hicolor/256x256/apps"

    cp qtclient/build/bin/logistics_frontend "$APPDIR/usr/bin/"
    cp launcher/build/logistics_launcher     "$APPDIR/usr/bin/"
    cp "$JAR" "$APPDIR/usr/bin/scheduler-backend.jar"
    cp -r jre/linux "$APPDIR/usr/bin/jre"

    # Desktop entry + icon
    cat > "$APPDIR/usr/share/applications/logistics_manager.desktop" << 'DESKTOP'
[Desktop Entry]
Type=Application
Name=Logistics Manager
Name[zh_CN]=自由快递人车辆调度系统
Comment=Vehicle scheduling for freelance courier logistics
Exec=logistics_launcher
Icon=logistics_manager
Terminal=true
Categories=Office;
StartupNotify=true
DESKTOP
    cp qtclient/resources/icon/厢式货车.png \
       "$APPDIR/usr/share/icons/hicolor/256x256/apps/logistics_manager.png"
    # appimagetool looks for these at the AppDir root too
    cp "$APPDIR/usr/share/applications/logistics_manager.desktop" "$APPDIR/"
    cp "$APPDIR/usr/share/icons/hicolor/256x256/apps/logistics_manager.png" "$APPDIR/"

    # Qt plugins (platforms/styles/imageformats/iconengines/networkinformation/tls)
    local PLUG=/usr/lib/qt6/plugins
    mkdir -p "$APPDIR/usr/lib/qt6/plugins"/{platforms,styles,imageformats,iconengines,networkinformation,tls}
    for d in platforms styles imageformats iconengines networkinformation tls; do
        cp "$PLUG/$d"/*.so "$APPDIR/usr/lib/qt6/plugins/$d"/ 2>/dev/null || true
    done

    # Deploy Qt + system libs with linuxdeploy (skip strip on host to avoid the
    # old-strip-vs-new-binutils .relr.dyn errors; we strip manually below).
    step "Deploying Qt/system libraries (linuxdeploy)..."
    export PATH="$HOME/.local/bin:$PATH"
    linuxdeploy --appdir "$APPDIR" --plugin qt \
        --desktop-file "$APPDIR/usr/share/applications/logistics_manager.desktop" \
        --icon-file "$APPDIR/usr/share/icons/hicolor/256x256/apps/logistics_manager.png" \
        2>&1 | grep -viE "Strip call failed|unknown type \[0x13\]|Unable to recognise" || true

    # AppRun (overrides any linuxdeploy-generated one to set Qt plugin paths)
    cat > "$APPDIR/AppRun" << 'APPRUN'
#!/bin/bash
HERE="$(dirname "$(readlink -f "${0}")")"
export LD_LIBRARY_PATH="${HERE}/usr/lib:${HERE}/usr/lib/qt6/plugins/platforms:${LD_LIBRARY_PATH}"
export QT_PLUGIN_PATH="${HERE}/usr/lib/qt6/plugins"
export QT_QPA_PLATFORM_PLUGIN_PATH="${HERE}/usr/lib/qt6/plugins/platforms"
cd "${HERE}/usr/bin"
exec "${HERE}/usr/bin/logistics_launcher" "$@"
APPRUN
    chmod +x "$APPDIR/AppRun"

    step "Packaging AppImage..."
    rm -f "$ROOT/$APPIMAGE_NAME"
    ~/.local/bin/appimagetool "$APPDIR" "$ROOT/$APPIMAGE_NAME"
    chmod +x "$ROOT/$APPIMAGE_NAME"
    info "AppImage ready: $APPIMAGE_NAME ($(du -sh "$ROOT/$APPIMAGE_NAME" | cut -f1))"
}

# ─────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────
main() {
    # Pick build target: from CLI arg or interactive prompt.
    if [ -z "$TARGET" ]; then
        if [ -t 0 ]; then
            echo "请选择要构建的目标:"
            echo "  1) linux    - 构建 Linux AppImage"
            echo "  2) windows  - 构建 Windows 发行版 (交叉编译)"
            echo "  3) web      - 构建 Web 版 (WASM)"
            echo "  4) all      - 全部构建"
            printf "输入选择 [1/2/3/4] (默认 4): "
            read -r choice
            case "${choice:-4}" in
                1|linux)   TARGET=linux ;;
                2|windows) TARGET=windows ;;
                3|web)     TARGET=web ;;
                4|all)     TARGET=all ;;
                *) die "无效选择: $choice" ;;
            esac
        else
            TARGET=all
        fi
    fi

    # Validate target
    case "$TARGET" in
        linux|windows|web|all) ;;
        *) die "Usage: $0 {linux|windows|web|all}" ;;
    esac

    case "$TARGET" in
        linux)
            JAR=$(build_backend)
            build_linux_native
            ensure_jre_linux
            assemble_linux_appimage "$JAR"
            ;;
        windows)
            JAR=$(build_backend)
            build_windows_cross
            ensure_jre_windows
            assemble_windows "$JAR"
            ;;
        web)
            build_wasm
            ensure_jre_linux
            assemble_web
            ;;
        all)
            JAR=$(build_backend)
            build_linux_native
            build_windows_cross
            build_wasm
            ensure_jre_linux
            ensure_jre_windows
            assemble_linux_appimage "$JAR"
            assemble_windows "$JAR"
            assemble_web
            ;;
    esac
    step "Done."
}

main "$@"
