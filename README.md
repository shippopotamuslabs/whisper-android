# whisper-android

Standalone Android library wrapping [whisper.cpp](https://github.com/ggml-org/whisper.cpp)
(JNI + CMake) for on-device speech-to-text. Extracted from Enclave's `:whisper-android`
module (Linear ENC-46) so the engine can be shared by Enclave and Bloviate without
either app depending on the other's codebase.

## Pinned native version

`third_party/whisper.cpp` is a git submodule pinned at
**`0c7be696371069e0baa2515ed7aa08d5a5473aa6`** — whisper.cpp **1.9.5**
(`v1.9.5-2-g0c7be696`), ggml **0.26.0**.

This is the commit Enclave actually had checked out. Enclave's CMakeLists carried
stale version strings (WHISPER 1.8.4 / GGML 0.9.8 / commit fc67457); the strings in
`whisper-android/src/main/cpp/CMakeLists.txt` were corrected to match the real
sources during extraction (ENC-51).

## Build facts

- compileSdk 37, minSdk 34, NDK 28.2.13676358, CMake 3.22.1, C++17
- ABI: `arm64-v8a` only
- CMake args: `-DGGML_OPENMP=OFF`, `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`
- The native build is skipped automatically when the submodule is not checked out
  (`git submodule update --init`).

## Layout

- `whisper-android/` — the core library (no Hilt, no app-framework dependencies
  beyond AndroidX core + coroutines). Public API: `WhisperEngine` / `WhisperParams`
  (implemented by `JniWhisperInference`) and `AudioRecorder` (16 kHz mono mic
  capture as a `Flow<FloatArray>`).
- `whisper-android-hilt/` — optional Hilt bindings (`WhisperModule` provides a
  singleton `WhisperEngine`). Only needed by consumers that use Hilt.
- `third_party/whisper.cpp/` — pinned upstream submodule.

## Consumption

Coordinates: `com.shippopotamuslabs:whisper-android` (and
`com.shippopotamuslabs:whisper-android-hilt`), version in `gradle.properties`
(`VERSION_NAME`).

### Composite build (development — how Enclave consumes it)

Vendor this repo as a submodule and substitute the module with the included build:

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

### Maven (GitLab Package Registry)

```bash
CI_JOB_TOKEN=<token> ./gradlew publishReleasePublicationToGitLabRepository
```

Consumers add the registry with a read token:

```kotlin
maven {
    url = uri("https://gitlab.com/api/v4/projects/87770779/packages/maven")
    credentials(HttpHeaderCredentials::class) {
        name = "Job-Token" // or "Private-Token" with a personal access token
        value = <token>
    }
    authentication { create<HttpHeaderAuthentication>("header") }
}
```

## Permissions

The library manifest declares `RECORD_AUDIO`; the consuming app must still request
it at runtime before collecting `AudioRecorder.audioChunks()`.

## Checks

```bash
./gradlew :whisper-android:testDebugUnitTest
./gradlew :whisper-android:assembleDebug
```
