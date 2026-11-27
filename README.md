# SubaruBridge

An experimental, device-local Android workspace for the legacy SUBARU STARLINK
infotainment interface. Build your own HTML dashboard and integrations without
redistributing the discontinued STARLINK application.

**Status:** original Android + Rust implementation, under development. The older
private research adapter displayed HTML on a Fujitsu Ten FB856UM. That result
does **not** establish compatibility of this new independent transport. Its next
milestone is a parked-car display, touch and reconnect test.

## What is here

- Original paired Bluetooth Classic RFCOMM listener; no root is needed for the app.
- A dependency-free Rust core for bounded SLIP, IPv4, ICMP echo, TCP and HTTP framing.
- Original local HTTP routes, battery/clock diagnostics, private result journal.
- A script-free HTML dashboard with three themes, two layouts and editable templates.
- Phone preview, Android component tests and native protocol regressions.
- A stable C interface for shared protocol work.
- Android Music, Maps, Phone, Messages and Notifications dashboard cards with explicit permission and confirmation boundaries.
- A native iOS companion source project and unsigned simulator build checks.
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

1. Follow [the build guide](docs/build.md) for Android Studio/CLI and the pinned toolchain.
2. Install your locally built APK. Open SubaruBridge and use the phone preview first.
3. Pair the Android phone using the car's normal Bluetooth settings.
4. With the car parked, close other STARLINK listener apps, open SubaruBridge, grant Nearby devices permission, wait for Ready, and open **Apps â†’ STARLINK** on the head unit.
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
