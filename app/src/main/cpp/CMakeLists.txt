cmake_minimum_required(VERSION 3.22.1)
project(pikafish)

# Recursively collect all Pikafish .cpp files (src/ has subdirs: nnue/, nnue/features/, etc.)
file(GLOB_RECURSE PIKAFISH_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/pikafish/src/*.cpp")

# Exclude x86-only platform-specific directories
list(FILTER PIKAFISH_SOURCES EXCLUDE REGEX ".*/universal/.*")

# Exclude main.cpp — it defines main() which is invalid in a shared library
list(FILTER PIKAFISH_SOURCES EXCLUDE REGEX ".*/main\\.cpp$")

# JNI glue layer
list(APPEND PIKAFISH_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/pikafish_jni.cpp")

# Build as a shared library loaded by System.loadLibrary("pikafish")
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
    -fno-rtti
    ${PIKAFISH_64BIT_FLAG}
    # Disable zstd's x86-64 BMI2 asm path — the .S file is not assembled by
    # the Android Gradle CMake integration, so the asm loop symbols are missing.
    # ZSTD_DISABLE_ASM is the correct override: portability_macros.h checks
    # !defined(ZSTD_DISABLE_ASM) before setting ZSTD_ENABLE_ASM_X86_64_BMI2=1.
    -DZSTD_DISABLE_ASM
)

target_include_directories(pikafish PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/pikafish/src"
)

# Link required Android libraries
find_library(log-lib log)
target_link_libraries(pikafish ${log-lib})
