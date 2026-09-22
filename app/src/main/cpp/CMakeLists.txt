cmake_minimum_required(VERSION 3.22.1)
project(pikafish)

# Recursively collect all Pikafish .cpp files (src/ has subdirs: nnue/, nnue/features/, etc.)
file(GLOB_RECURSE PIKAFISH_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/pikafish/src/*.cpp")

# Build as a shared library named libpikafish.so so the Android package manager
# places it in nativeLibraryDir (where we can extract and chmod +x it).
add_library(pikafish SHARED ${PIKAFISH_SOURCES})

# IS_64BIT must be set explicitly for 64-bit ABIs — Pikafish's Makefile sets
# this flag to enable __int128 (u128) bitboard support.
if(CMAKE_SIZEOF_VOID_P EQUAL 8)
    set(PIKAFISH_64BIT_FLAG "-DIS_64BIT")
else()
    set(PIKAFISH_64BIT_FLAG "")
endif()

target_compile_options(pikafish PRIVATE
    -std=c++17
    -O3
    -DNDEBUG
    -DNNUE_EMBEDDING_OFF
    -fno-exceptions
    -fno-rtti
    ${PIKAFISH_64BIT_FLAG}
)

target_include_directories(pikafish PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/pikafish/src"
)

# Link required Android libraries
find_library(log-lib log)
target_link_libraries(pikafish ${log-lib})
