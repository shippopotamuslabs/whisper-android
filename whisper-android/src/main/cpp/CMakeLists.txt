cmake_minimum_required(VERSION 3.22.1)
project("whisper-jni" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_ANDROID_STL_TYPE c++_shared)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)
set(GGML_DIR ${WHISPER_DIR}/ggml)
set(WHISPER_VERSION 1.9.5)
set(GGML_VERSION 0.26.0)
set(GGML_COMMIT 0c7be696)

# ggml.c includes ggml-version.h, which upstream ggml generates from
# ggml-version.h.in via its own CMake build. This build compiles the ggml
# sources directly, so generate the header here from the pinned versions
# above (without this, the native build fails on the 1.9.5 sources).
set(GGML_BUILD_COMMIT ${GGML_COMMIT})
configure_file(
    ${GGML_DIR}/src/ggml-version.h.in
    ${CMAKE_CURRENT_BINARY_DIR}/generated/ggml-version.h
    @ONLY
)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fPIC -march=armv8.2-a+dotprod+fp16")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fPIC -march=armv8.2-a+dotprod+fp16")

add_library(whisper-jni SHARED
    whisper-jni.cpp
    ${WHISPER_DIR}/src/whisper.cpp
    # New in whisper.cpp 1.9.5 (was missing from Enclave's stale source list):
    # self-stubs to a no-op on non-Apple platforms.
    ${WHISPER_DIR}/src/aneforge/whisper-aneforge.cpp
    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml.cpp
    ${GGML_DIR}/src/ggml-alloc.c
    ${GGML_DIR}/src/ggml-backend.cpp
    # New in ggml 0.26.0 (was missing from Enclave's stale source list).
    ${GGML_DIR}/src/ggml-backend-meta.cpp
    ${GGML_DIR}/src/ggml-backend-dl.cpp
    ${GGML_DIR}/src/ggml-backend-reg.cpp
    ${GGML_DIR}/src/ggml-opt.cpp
    ${GGML_DIR}/src/ggml-threading.cpp
    ${GGML_DIR}/src/ggml-quants.c
    ${GGML_DIR}/src/gguf.cpp
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
    ${GGML_DIR}/src/ggml-cpu/binary-ops.cpp
    ${GGML_DIR}/src/ggml-cpu/hbm.cpp
    ${GGML_DIR}/src/ggml-cpu/ops.cpp
    ${GGML_DIR}/src/ggml-cpu/quants.c
    ${GGML_DIR}/src/ggml-cpu/repack.cpp
    ${GGML_DIR}/src/ggml-cpu/traits.cpp
    ${GGML_DIR}/src/ggml-cpu/unary-ops.cpp
    ${GGML_DIR}/src/ggml-cpu/vec.cpp
    # New in ggml 0.26.0 (was missing from Enclave's stale source list).
    ${GGML_DIR}/src/ggml-cpu/tiled/tiled.cpp
    ${GGML_DIR}/src/ggml-cpu/arch/arm/quants.c
    ${GGML_DIR}/src/ggml-cpu/arch/arm/repack.cpp
)

target_include_directories(whisper-jni PRIVATE
    ${CMAKE_CURRENT_BINARY_DIR}/generated
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${GGML_DIR}/include
    ${GGML_DIR}/src
    ${GGML_DIR}/src/ggml-cpu
)

target_compile_definitions(whisper-jni PRIVATE
    GGML_USE_CPU
    GGML_VERSION="${GGML_VERSION}"
    GGML_COMMIT="${GGML_COMMIT}"
    WHISPER_VERSION="${WHISPER_VERSION}"
    _GNU_SOURCE
)

target_link_libraries(whisper-jni
    log
    android
    dl
)

set_target_properties(whisper-jni PROPERTIES
    LINK_OPTIONS "-Wl,--gc-sections"
)
