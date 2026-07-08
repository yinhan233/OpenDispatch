cmake_minimum_required(VERSION 3.16)
project(logistics_launcher_only LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 REQUIRED COMPONENTS Core Network)
# Core includes QThread, QProcess, etc.

add_executable(logistics_launcher_only main.cpp)
target_link_libraries(logistics_launcher_only PRIVATE Qt6::Core Qt6::Network)
