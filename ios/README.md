# SubaruBridge iOS 0.57.0

The native iPhone app now brings Home, Garage, dashboard preview and diagnostic
tools into one app. It targets iOS 17 and later and preserves the existing bundle
identifier, favorite settings and local check results when upgrading.

## Included

- Native vehicle profiles and switching; manual/approximate odometer readings
  and audited corrections; manufacturer-scoped or custom maintenance schedules.
- Multi-item service visits with uncertain mileage ranges, optional dates,
  provider/DIY details, costs, parts and warranty notes.
- Full/partial/missed fuel logs, expenses, completed manual trips, reconciled
  mileage estimates and monthly/yearly recorded distance and cost charts.
- Opt-in local maintenance notifications, configurable lead times and snoozes.
- Android-compatible additive JSON backup/restore, selected-vehicle CSV and an
  escaped printable HTML service history. Conflicts preserve existing records.
- The same original Rust engine and schema used by Android. Private atomic local
  storage is protected by iOS file protection and excluded from automatic backup.
  Export your own backup before removing the app; exports are unencrypted.
- Original head-unit templates in a loopback-only WebKit preview with JavaScript
  disabled. Native HTML links, touch-test forms and appearance settings work
  through the app's HTTP handler, without a PC helper.
- Bounded diagnostic metadata recording, private notes and event markers,
  interrupted-run recovery, timelines, explicit deletion and private ZIP export.
  Limits: ten minutes or one MiB per session, newest twelve sessions retained.
  iOS saves recording when the app backgrounds or locks. This is not continuous
  background recording, raw Bluetooth capture or a vehicle event data recorder.
- Existing Apple Music library player, maps links, system calling/message
  prompts, app-owned local notifications and accessory inventory.
- Original blue bridge-and-road icon matching Android.

The Garage and phone features work without connecting to a car or creating an
account. Estimates, uncertain history and missing records stay explicit. No
live odometer, vehicle health diagnosis, automatic GPS, OBD-II, attachments or
encrypted backup is claimed by this release.

## Platform and car limits

Android's observed STARLINK path uses Classic RFCOMM with SLIP/IP/TCP/HTTP. The
shared Rust engine is built for iPhone, but the iOS accessory data channel and
its framing have not been verified. Accessory inspection sends no experimental
messages. No protocol names, accessory identities or authentication are invented.
The preview and phone self-check are not physical Subaru results.

Ordinary iOS apps do not expose another app's global notification inbox or
generic media sessions. Taurus control through the Pixel remains the separate
Android AMS experiment. The native iOS library player does not control Taurus.
Audio and normal hands-free calling still use the phone/car's supported pairing.

## Build on the Mac

Install Xcode with an available iOS simulator, complete its normal first-run
setup, and install Rust 1.90.0 through rustup. No vendor runtime is required.

```sh
rustup toolchain install 1.90.0 --profile minimal
cd ios
bash scripts/mac-verify.sh
bash scripts/test-basic-ui.sh
open SubaruBridge.xcodeproj
```

The scripts build the original Rust static library for the selected Apple
architecture, generate the icon, run Swift tests and build the native app.
Keep CARGO_HOME and RUSTUP_HOME available to Xcode if you use nonstandard tool
locations. The Rust Xcode build phase also supports ordinary IDE builds.

The native UI script uses a dedicated **SubaruBridge Unified QA** iPhone 15 Plus
simulator. It resets only the app on that simulator. The Garage fixture path and
reset arguments exist only in Debug simulator builds. Release and physical-phone
builds do not include that reset path. Logs and result bundles stay under build/.

For a signed IPA, configure your own Xcode signing account and registered phone:

```sh
DEVELOPMENT_TEAM=YOUR_TEAM_ID bash scripts/package-device.sh signed
```

The embedded profile must permit the intended phone and remain valid. An IPA
does not install by opening it in Files. Use the supported Xcode/device or signing
workflow. The explicit unsigned packaging mode produces a requires-signing file.
Never publish credentials, private keys, profiles or private diagnostic exports.

## Resource ownership and verification

Android's original editable dashboard remains canonical. After changing its
configuration/templates, run `python3 scripts/sync_ios_headunit.py`; CI checks
that the packaged iOS copy matches. No extracted STARLINK application material
is distributed.

The Mac passed 32 Swift component tests, 76 shared Rust regressions, an Apple
SDK simulator build and all six native UI journeys: app cards, favorites,
notification denial, recorder/dashboard forms, Garage saving/history/relaunch
and the local four-screen check. The Garage test confirms multiple maintenance
items, an unknown service date and a preserved 70,000–74,000-mile service range.
The native Files export picker is checked separately from the shared backup
round-trip tests. A development-signed arm64 IPA was archived and exported;
signature, profile, version, resources and transfer checksum were verified.
The generated icon is also checked after Apple's PNG optimization.
Physical iPhone validation of this new Garage build and direct Subaru
display/touch/reconnect remain required. The owner's earlier basic iPhone launch
and notification result does not certify these newer features.

Primary references: [External Accessory](https://developer.apple.com/documentation/externalaccessory/),
[accessory protocol declarations](https://developer.apple.com/documentation/bundleresources/information-property-list/uisupportedexternalaccessoryprotocols),
[background execution](https://developer.apple.com/documentation/uikit/extending-your-app-s-background-execution-time).
