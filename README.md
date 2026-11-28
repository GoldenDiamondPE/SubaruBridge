# SubaruBridge

An experimental, device-local Android workspace for the legacy SUBARU STARLINK
infotainment interface. Build your own HTML dashboard and integrations without
redistributing the discontinued STARLINK application.

**Status:** original Android + Rust implementation, under development. The older
private research adapter displayed HTML on a Fujitsu Ten FB856UM. That result
does **not** establish compatibility of this new independent transport. Its next
milestone is a parked-car display, touch and reconnect test.

The 0.58 factory test exposed a startup failure. Version 0.59 corrects a
capture-confirmed head-unit address rejection, with failing-before/passing-after
regressions and Android JNI checks. The corrected APK still requires a new
physical-car test; see [the fix and test directions](docs/android-059.md).

## What is here

- Original paired Bluetooth Classic RFCOMM listener; no root is needed for the app.
- An original Rust core for bounded SLIP, IPv4, ICMP echo, TCP and HTTP framing.
- Original local HTTP routes, battery/clock diagnostics, private result journal.
- One Android app for Garage, the dashboard, integrations and on-device recording;
  no laptop arming helper is needed. See [the unified app guide](docs/unified-android.md).
- A script-free HTML dashboard with three themes, two layouts and editable templates.
- Phone preview, Android component tests and native protocol regressions.
- A stable C interface for shared protocol work.
- Android Music, Maps, Phone, Messages and Notifications dashboard cards with explicit permission and confirmation boundaries.
- A native iOS companion with Home, Garage, dashboard preview, diagnostic recording,
  phone actions, local notifications and device packaging. See [iOS](ios/README.md).
- Native Android and iOS Garage screens for vehicle profiles, mileage, maintenance, services,
  fill-ups, expenses, manual trips, local reminders and explicit backups.
  Both platforms use the same Rust calculations, versioned records and backup format.
- Generic Android music-player selection and an experimental paired-iPhone media
  adapter for testing Taurus through Apple's public Media Service.

This repository contains the complete standalone STARLINK-path app and its build
sources. It contains no
legacy STARLINK APK, decompiled code, vendor SDK, extracted art, firmware, keys,
private captures or app binaries. Earlier MirrorLink, Raspberry Pi and scrcpy
experiments remain preserved separately in the development tree. This repository
does not implement a direct iPhone-to-car transport, full CarPlay feature parity or MirrorLink
certification. See [dashboard apps and limits](docs/apps.md) for the implemented adapters. It is not affiliated with Subaru, Fujitsu Ten, Apple or Google.

## Build and use

For ownership tracking, open **Home → Garage → Add vehicle**. No car connection,
location access or account is required. See [the Garage guide](docs/garage.md)
for recording approximate history, correcting readings and keeping a backup.
Android 0.58 adds private receipts/PDFs, complete encrypted backups with conflict
review, verified severe-service notes, offline specifications and fuel/mileage
summaries. See [ownership release details](docs/ownership-058.md).
Android 0.59 adds background connection recovery, saved Pause, independent
loopback-port recovery and clearer phone screens. See [the update and parked-car
test](docs/android-059.md). Download the APK and iOS signing-required IPA from
[GitHub Releases](https://github.com/GoldenDiamondPE/SubaruBridge/releases).
The [iPhone/Pixel guide](docs/iphone-pixel-subaru.md) separates experimental music
controls from notification research and factory audio.

1. Follow [the build guide](docs/build.md) for Android Studio/CLI and the pinned toolchain.
2. Install your locally built APK. Open SubaruBridge and use the phone preview first.
   **Home → Record & diagnose** contains phone self-checks, the private recorder,
   saved event timelines and the optional developer view.
3. Pair the Android phone using the car's normal Bluetooth settings.
4. With the car parked, close other STARLINK listener apps, open SubaruBridge, grant Nearby devices permission, wait for Ready, and open **Apps → STARLINK** on the head unit.
5. Follow [the test guide](docs/testing.md). A successful laptop test does not prove car compatibility.

The [customization guide](docs/customization.md) explains themes, layouts and new
application routes. The [architecture guide](docs/architecture.md) describes the
transport boundary and its current limits. Published documentation is available
at [the project site](https://goldendiamondpe.github.io/SubaruBridge/).

For iPhone music through the Pixel, follow [the phone bench test](docs/iphone-media-testing.md)
before a car test. See [feature status](FEATURES.md) and [the roadmap](ROADMAP.md)
for implemented adapters, outstanding verification and the integrated ownership plan.

No internet backend or paid API is used by the included screens. Keep vehicle
firmware, accessory authentication and driving restrictions unchanged. Features
requiring credentials are outside the included implementation.

## Contribute

Send small, reproducible fixes with synthetic tests. Report the app revision,
Android version, head-unit model/software and the failing step. Do not attach
unredacted captures, device identifiers, vendor APKs or decompiled material to
public issues or pull requests. See [CONTRIBUTING.md](CONTRIBUTING.md) and
[the publication policy](PUBLICATION-POLICY.md).

Licensed under GPLv3; see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
