# CMake toolchain for Qt6 WebAssembly (Emscripten)
# Used by package.sh to build WASM binaries for browser access.
#
# This file is a thin wrapper. The real toolchain is provided by Qt:
#   ~/Qt/6.8.0/wasm_singlethread/lib/cmake/Qt6/qt.toolchain.cmake
# which is auto-loaded when using qt-cmake. This file exists so that
# plain cmake -DCMAKE_TOOLCHAIN_FILE=... also works.
#
# Prerequisites:
#   source ~/emsdk/emsdk_env.sh   # sets emcc/em++ in PATH
#   Qt 6.8.0 wasm_singlethread installed via aqt at ~/Qt/6.8.0/wasm_singlethread
#   Qt 6.8.0 gcc_64 (host tools) at ~/Qt/6.8.0/gcc_64

set(CMAKE_SYSTEM_NAME Emscripten)
set(CMAKE_SYSTEM_PROCESSOR wasm32)

# Let Qt's own toolchain handle compiler paths via emcc/em++ in PATH.
# We just point to the Qt WASM installation.
set(CMAKE_FIND_ROOT_PATH /home/yinhan/Qt/6.8.0/wasm_singlethread)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)

# Host Qt (Linux 6.8.0) provides moc/rcc/uic that run on the build machine.
set(QT_HOST_PATH /home/yinhan/Qt/6.8.0/gcc_64 CACHE PATH "Host Qt for tools")

# Force host tools so AUTOMOC/AUTORCC use Linux-native moc/rcc/uic.
set(Qt6CoreTools_DIR     /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6CoreTools     CACHE PATH "")
set(Qt6GuiTools_DIR      /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6GuiTools      CACHE PATH "")
set(Qt6WidgetsTools_DIR  /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6WidgetsTools  CACHE PATH "")
set(Qt6NetworkTools_DIR  /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6NetworkTools  CACHE PATH "")

# Target Qt (wasm) cmake dirs.
set(Qt6_DIR          /home/yinhan/Qt/6.8.0/wasm_singlethread/lib/cmake/Qt6          CACHE PATH "")
set(Qt6Core_DIR      /home/yinhan/Qt/6.8.0/wasm_singlethread/lib/cmake/Qt6Core      CACHE PATH "")
set(Qt6Gui_DIR       /home/yinhan/Qt/6.8.0/wasm_singlethread/lib/cmake/Qt6Gui       CACHE PATH "")
set(Qt6Widgets_DIR   /home/yinhan/Qt/6.8.0/wasm_singlethread/lib/cmake/Qt6Widgets   CACHE PATH "")
set(Qt6Network_DIR   /home/yinhan/Qt/6.8.0/wasm_singlethread/lib/cmake/Qt6Network   CACHE PATH "")

# Ignore system cmake prefixes (system Qt is 6.11, we need 6.8).
set(CMAKE_IGNORE_PATH
    /usr/lib/cmake
    /usr/share/cmake
    /usr
    /opt/cuda
)
set(CMAKE_SYSTEM_IGNORE_PATH ${CMAKE_IGNORE_PATH})
set(CMAKE_PREFIX_PATH /home/yinhan/Qt/6.8.0/wasm_singlethread)
