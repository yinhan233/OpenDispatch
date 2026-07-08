# CMake toolchain for MinGW-w64 cross compile (Qt6 from aqt)
# Used by package.sh to build Windows binaries on Linux.
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

set(CMAKE_C_COMPILER   x86_64-w64-mingw32-gcc)
set(CMAKE_CXX_COMPILER x86_64-w64-mingw32-g++)
set(CMAKE_RC_COMPILER  x86_64-w64-mingw32-windres)

set(CMAKE_FIND_ROOT_PATH /home/yinhan/Qt/6.8.0/mingw_64)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)

# Host Qt (Linux 6.8.0) provides moc/rcc/uic that run on the build machine.
# Target Qt (Windows mingw 6.8.0) provides libs/headers. Versions match.
set(QT_HOST_PATH /home/yinhan/Qt/6.8.0/gcc_64 CACHE PATH "Host Qt for tools")

# Force the *Tools packages to come from the host Qt so AUTOMOC uses the
# Linux moc/rcc/uic (the target moc.exe cannot run on Linux).
set(Qt6CoreTools_DIR     /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6CoreTools     CACHE PATH "")
set(Qt6GuiTools_DIR      /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6GuiTools      CACHE PATH "")
set(Qt6WidgetsTools_DIR  /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6WidgetsTools  CACHE PATH "")
set(Qt6NetworkTools_DIR  /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6NetworkTools  CACHE PATH "")
set(Qt6ChartsTools_DIR   /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6ChartsTools   CACHE PATH "")
set(Qt6LinguistTools_DIR /home/yinhan/Qt/6.8.0/gcc_64/lib/cmake/Qt6LinguistTools CACHE PATH "")

# Aggressively ignore every system cmake prefix so only the target Qt under
# ~/Qt is discovered. Host Qt (system /usr, /opt/cuda) has a different version
# and its *Tools packages break cross-configuration.
set(CMAKE_IGNORE_PATH
    /usr/lib/cmake
    /usr/share/cmake
    /usr
    /opt/cuda
    /opt/cuda/lib/cmake
)
set(CMAKE_SYSTEM_IGNORE_PATH ${CMAKE_IGNORE_PATH})
set(CMAKE_PREFIX_PATH /home/yinhan/Qt/6.8.0/mingw_64)

# Qt6 component dirs (target libs/headers)
set(Qt6_DIR          /home/yinhan/Qt/6.8.0/mingw_64/lib/cmake/Qt6          CACHE PATH "")
set(Qt6Core_DIR      /home/yinhan/Qt/6.8.0/mingw_64/lib/cmake/Qt6Core      CACHE PATH "")
set(Qt6Gui_DIR       /home/yinhan/Qt/6.8.0/mingw_64/lib/cmake/Qt6Gui       CACHE PATH "")
set(Qt6Widgets_DIR   /home/yinhan/Qt/6.8.0/mingw_64/lib/cmake/Qt6Widgets   CACHE PATH "")
set(Qt6Network_DIR   /home/yinhan/Qt/6.8.0/mingw_64/lib/cmake/Qt6Network   CACHE PATH "")
set(Qt6Charts_DIR    /home/yinhan/Qt/6.8.0/mingw_64/lib/cmake/Qt6Charts    CACHE PATH "")
