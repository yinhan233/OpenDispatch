cmake_minimum_required(VERSION 3.16)
project(logistics_frontend LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

find_package(Qt6 REQUIRED COMPONENTS Widgets Network Charts)

add_executable(logistics_frontend
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

target_link_libraries(logistics_frontend PRIVATE
    Qt6::Widgets
    Qt6::Network
    Qt6::Charts
)

target_include_directories(logistics_frontend PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

install(TARGETS logistics_frontend RUNTIME DESTINATION bin)
