cmake_minimum_required(VERSION 3.16)
project(logistics_ui_tests LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

set(QTCLIENT_ROOT ${CMAKE_CURRENT_LIST_DIR}/..)

find_package(Qt6 REQUIRED COMPONENTS Widgets Network Test)

enable_testing()

# Include source dir for #include "Models.h" etc.
include_directories(${QTCLIENT_ROOT}/src)

# --- Model Tests ---
qt_add_executable(test_models
    test_models.cpp
)

target_link_libraries(test_models PRIVATE Qt6::Widgets Qt6::Network Qt6::Test)
add_test(NAME test_models COMMAND test_models)

# --- Theme Tests ---
qt_add_executable(test_theme
    test_theme.cpp
)

target_link_libraries(test_theme PRIVATE Qt6::Widgets Qt6::Test)
add_test(NAME test_theme COMMAND test_theme)

# --- ApiClient Tests ---
qt_add_executable(test_apiclient
    test_apiclient.cpp
    ${QTCLIENT_ROOT}/src/ApiClient.h
    ${QTCLIENT_ROOT}/src/ApiClient.cpp
    ${QTCLIENT_ROOT}/src/Models.h
)

target_link_libraries(test_apiclient PRIVATE Qt6::Widgets Qt6::Network Qt6::Test)
add_test(NAME test_apiclient COMMAND test_apiclient)

# --- GanttChart Tests ---
qt_add_executable(test_gantt
    test_gantt.cpp
    ${QTCLIENT_ROOT}/src/widgets/GanttChart.h
    ${QTCLIENT_ROOT}/src/widgets/GanttChart.cpp
)

target_link_libraries(test_gantt PRIVATE Qt6::Widgets Qt6::Test)
add_test(NAME test_gantt COMMAND test_gantt)

# --- MapWidget Tests ---
qt_add_executable(test_mapwidget
    test_mapwidget.cpp
    ${QTCLIENT_ROOT}/src/widgets/MapWidget.h
    ${QTCLIENT_ROOT}/src/widgets/MapWidget.cpp
)

target_link_libraries(test_mapwidget PRIVATE Qt6::Widgets Qt6::Network Qt6::Test)
add_test(NAME test_mapwidget COMMAND test_mapwidget)
