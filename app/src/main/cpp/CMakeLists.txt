cmake_minimum_required(VERSION 3.10.2)
project(spatial_audio)

# For 16KB page size compatibility on Android 15+
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=65536")

add_library(spatial_audio SHARED
        SpatialAudioEngine.cpp
        SpatialAudioEngine_JNI.cpp
)

find_package(oboe REQUIRED CONFIG)

target_link_libraries(spatial_audio oboe::oboe log)
