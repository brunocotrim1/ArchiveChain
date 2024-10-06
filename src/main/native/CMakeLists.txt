cmake_minimum_required(VERSION 3.12)
project(por_binding)

# Find Python and Pybind11
find_package(pybind11 REQUIRED)
find_package(OpenSSL REQUIRED)

pybind11_add_module(${PROJECT_NAME} pywrap.cpp)

target_link_libraries(${PROJECT_NAME} PRIVATE OpenSSL::Crypto)

