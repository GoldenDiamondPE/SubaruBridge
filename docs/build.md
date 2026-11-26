---
layout: default
title: Build your own app
---

# Build your own app

The public app builds from original source and does not use the old STARLINK APK.
It runs on Android 11/API 30 or later with arm64-v8a or x86_64 native libraries.
Classic Bluetooth hardware is needed for a physical car test; the emulator tests
the UI, JNI and local HTTP paths.

## Pinned tools

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 9.4.1 |
| Android Gradle Plugin | 9.2.0 |
| Android SDK platform | 36 |
| Android NDK | 28.2.13676358 |
| Rust | 1.90.0 |
| Python | 3.11 or later, standard library only |

Install Android Studio and the SDK/NDK through its SDK Manager or the official
[Android CLI](https://developer.android.com/tools/android-cli). Install Gradle
from [Gradle](https://gradle.org/install/) and Rust from
[rustup](https://rustup.rs/). No downloaded tool binaries are in this repository.
Accept the SDK license terms using your local installation.

## Set up and build

```sh
git clone https://github.com/GoldenDiamondPE/SubaruBridge.git
cd SubaruBridge
rustup toolchain install 1.90.0 --profile minimal
rustup target add --toolchain 1.90.0 aarch64-linux-android x86_64-linux-android
```

Set `JAVA_HOME` to JDK 17, `ANDROID_HOME` to the SDK and put `cargo`, `gradle`
and SDK `platform-tools` on PATH. For example in Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:MLPI_BUILD_PYTHON = 'C:\path\to\python.exe'
gradle -p android --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx3g' assembleDebug assembleDebugAndroidTest
```

On Linux/macOS use `export` to set these variables; `python3` is the default.
The build reads `sdk.dir` from `android/local.properties` if SDK environment
variables are absent. Rust's NDK linker is configured by the included build script.
On the development Windows machine, component builds fit within 16 GB RAM;
this does not imply a full Android OS build fits in that memory.

In Android Studio open `android/`, choose JDK 17, and select the installed SDK
if prompted. Build tasks generate the UI and both Rust Android static libraries
before linking the small JNI adapter. Android Studio can use its Gradle settings
to select a compatible installed Gradle distribution.

The debug APK is `android/build/outputs/apk/debug/SubaruBridge-debug.apk`.
Debug builds use your own default Android debug key. No project signing key is
distributed. Never use a research key or APK as a public signing dependency.

```sh
android install --device=YOUR_SERIAL android/build/outputs/apk/debug/SubaruBridge-debug.apk
```

ADB's `install -r` also works. The public package is `org.subarubridge.app`;
it can coexist with the private combined research build. Only one app should
listen on the STARLINK Bluetooth UUID at a time.

## Native tests

On a Linux/macOS host run `cargo test` from `rust/`. On Windows, host tests need
a compatible MSVC linker, or use the NDK to build tests for an Android emulator:

```sh
python3 scripts/build_android_rust.py --sdk YOUR_SDK --abi x86_64 --tests
```

Push the printed `protocol-*` executable with `adb push`, set mode 700, and run
it in `/data/local/tmp/`. This checks Android's actual native ABI without a full
ROM build. Use the test guide for app-level and physical Bluetooth checks.
