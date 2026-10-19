cmake_minimum_required(VERSION 3.22.1)
project(statuscompressor C)

# Built by scripts/build-x264.sh.
set(X264_DIR ${CMAKE_CURRENT_SOURCE_DIR}/x264/${ANDROID_ABI})

add_library(x264 STATIC IMPORTED)
set_target_properties(x264 PROPERTIES IMPORTED_LOCATION ${X264_DIR}/lib/libx264.a)

add_library(sc264 SHARED x264_jni.c)
target_include_directories(sc264 PRIVATE ${X264_DIR}/include)
target_compile_options(sc264 PRIVATE -O3)
target_link_libraries(sc264 x264 m)
# 16 KB page alignment, required for new apps on Android 15+ devices.
target_link_options(sc264 PRIVATE -Wl,-z,max-page-size=16384)
