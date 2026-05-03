cmake_minimum_required(VERSION 3.22.1)
project("scouty_llama_jni")

if (NOT DEFINED LLAMA_CPP_ROOT OR LLAMA_CPP_ROOT STREQUAL "")
    message(FATAL_ERROR "LLAMA_CPP_ROOT must point to a llama.cpp checkout")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_ROOT} llama.cpp)

add_library(scouty_llama_jni SHARED scouty_llama_jni.cpp)

target_include_directories(
    scouty_llama_jni
    PRIVATE
    ${LLAMA_CPP_ROOT}/include
    ${LLAMA_CPP_ROOT}/ggml/include
)

target_compile_options(
    scouty_llama_jni
    PRIVATE
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
    -O3
)

target_link_options(
    scouty_llama_jni
    PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)

target_link_libraries(
    scouty_llama_jni
    PRIVATE
    android
    log
    llama
)
