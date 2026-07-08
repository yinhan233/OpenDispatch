cmake_minimum_required(VERSION 3.16)
project(logistics_ui LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

find_package(Qt6 REQUIRED COMPONENTS Widgets Network)

# qt_add_executable works for both native and WASM (generates .html + qtloader.js for wasm)
qt_add_executable(logistics_ui
    src/main.cpp
    src/Models.h
    src/Theme.h
    src/ApiClient.h
    src/ApiClient.cpp
    src/MainWindow.h
    src/MainWindow.cpp
    src/MapKeyDialog.h
    src/MapKeyDialog.cpp
    src/tabs/OrdersTab.h
    src/tabs/OrdersTab.cpp
    src/tabs/VehiclesTab.h
    src/tabs/VehiclesTab.cpp
    src/tabs/LocationsTab.h
    src/tabs/LocationsTab.cpp
    src/tabs/ScheduleTab.h
    src/tabs/ScheduleTab.cpp
    src/tabs/AnimationTab.h
    src/tabs/AnimationTab.cpp
    src/widgets/GanttChart.h
    src/widgets/GanttChart.cpp
    src/widgets/MapWidget.h
    src/widgets/MapWidget.cpp
    resources/resources.qrc
)

target_link_libraries(logistics_ui PRIVATE
    Qt6::Widgets
    Qt6::Network
)

target_include_directories(logistics_ui PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

if(EMSCRIPTEN)
    # Asyncify: enables QDialog::exec() and QMessageBox::question() return values
    # on WebAssembly (which otherwise can't have nested event loops).
    target_link_options(logistics_ui PRIVATE -sASYNCIFY -Os)
    # Increase initial memory (default 50MB may be too small for Qt Widgets)
    set_target_properties(logistics_ui PROPERTIES QT_WASM_INITIAL_MEMORY "150MB")
elseif(WIN32)
    target_link_options(logistics_ui PRIVATE -mwindows)
endif()

install(TARGETS logistics_ui RUNTIME DESTINATION bin)
