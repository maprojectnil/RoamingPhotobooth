cmake_minimum_required(VERSION 3.22.1)

project("roamingphotobooth")

# Nonaktifkan contoh/testing bawaan libusb-cmake, kita cuma butuh library-nya
set(LIBUSB_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LIBUSB_BUILD_TESTING OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_SOURCE_DIR}/../libusb-src ${CMAKE_BINARY_DIR}/libusb-build)

add_library(ptpbridge SHARED
        src/main/cpp/ptp_bridge.cpp
)

find_library(log-lib log)

target_link_libraries(ptpbridge usb-1.0 ${log-lib})