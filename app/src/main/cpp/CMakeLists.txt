cmake_minimum_required(VERSION 3.22.1)
project(pikafish)

# Collect all Pikafish .cpp source files
file(GLOB PIKAFISH_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/pikafish/src/*.cpp")

# Build as an executable named libpikafish.so so the Android package manager
# places it in nativeLibraryDir (where we can extract and chmod +x it).
add_library(pikafish SHARED ${PIKAFISH_SOURCES})

target_compile_options(pikafish PRIVATE
    -std=c++17
    -O3
    -DNDEBUG
    -DNNUE_EMBEDDING_OFF
    -fno-exceptions
    -fno-rtti
)

target_include_directories(pikafish PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/pikafish/src"
)

# Link required Android libraries
find_library(log-lib log)
target_link_libraries(pikafish ${log-lib})
