# whisper-android

Standalone Android library that wraps whisper.cpp (JNI + CMake) for on-device
speech-to-text. Extracted from Enclave's `:whisper-android` module (Linear
ENC-46) so Enclave and Bloviate can share the engine, with neither app
depending on the other's codebase.

## Pinned native version

`third_party/whisper.cpp` is a git submodule pinned at
**`0c7be696371069e0baa2515ed7aa08d5a5473aa6`**, whisper.cpp **1.9.5**
(`v1.9.5-2-g0c7be696`), ggml **0.26.0**.

This is the commit Enclave actually had checked out. Enclave's CMakeLists
carried stale version strings (WHISPER 1.8.4 / GGML 0.9.8 / commit fc67457).
The strings in `whisper-android/src/main/cpp/CMakeLists.txt` were corrected
to match the real sources during extraction (ENC-51).

Cold clone note: run `git submodule update --init` after cloning. The native
build skips itself when the submodule is not checked out, so a clone without
it still configures.

## Build facts

- compileSdk 37, minSdk 34, NDK 28.2.13676358, CMake 3.22.1, C++17
- ABI: `arm64-v8a` only
- CMake args: `-DGGML_OPENMP=OFF`, `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`

## Layout

| Module | What it is |
| --- | --- |
| `whisper-android/` | The core library. It has no Hilt and no app-framework dependencies beyond AndroidX core and coroutines. Public API: `WhisperEngine` / `WhisperParams` (implemented by `JniWhisperInference`) and `AudioRecorder` (16 kHz mono mic capture as a `Flow<FloatArray>`) |
| `whisper-android-hilt/` | Optional Hilt bindings (`WhisperModule` provides a singleton `WhisperEngine`). Only consumers that use Hilt need it |
| `third_party/whisper.cpp/` | The pinned upstream submodule |

## Consumption

Coordinates: `com.shippopotamuslabs:whisper-android` (and
`com.shippopotamuslabs:whisper-android-hilt`), version in `gradle.properties`
(`VERSION_NAME`, currently `0.1.0-SNAPSHOT`).

### Composite build (development, how Enclave consumes it)

Vendor this repo as a submodule and substitute the module with the included
build:

```kotlin
// settings.gradle.kts of the consuming project
includeBuild("third_party/whisper-android") {
    dependencySubstitution {
        substitute(module("com.shippopotamuslabs:whisper-android"))
            .using(project(":whisper-android"))
        substitute(module("com.shippopotamuslabs:whisper-android-hilt"))
            .using(project(":whisper-android-hilt"))
    }
}
```

```kotlin
// consuming module
implementation("com.shippopotamuslabs:whisper-android")
```

### Maven publishing (status)

The library used to ship as a published Maven artifact. No publishing
target is configured today. Consume the library through the composite
build above.

## Permissions

The library manifest declares `RECORD_AUDIO`. The consuming app still has to
request it at runtime before collecting `AudioRecorder.audioChunks()`.

## Checks

```bash
./gradlew :whisper-android:testDebugUnitTest
./gradlew :whisper-android:assembleDebug
```
