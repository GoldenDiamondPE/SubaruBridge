# SubaruBridge basic iPhone companion — 0.2.0

This is the first iOS source branch alongside Android, targeting iOS 17 or later.
**Apple SDK verification runs in the macOS CI job; physical iPhone and car validation remain required.** The Windows host can prepare the source,
but the Mac is needed for Swift tests, Apple SDK validation and simulator work.
Device packaging is separate from simulator builds. Development signing uses
your own Apple account and a profile permitting the intended phone. Signing
material and prebuilt IPAs are excluded from this source.

The companion now includes Music, Maps, Phone, Messages and Notifications cards.
Apple Music library playback uses the native player; Spotify opens through its
normal link. Maps opens a favorite destination, calling uses the iPhone prompt,
and SMS uses MessageUI with user confirmation. The Notifications page requests
permission explicitly and schedules an app-owned local test. System-wide iPhone notification
access, Spotify remote control and an iPhone-to-Pixel transport are not implemented.
See [the capability guide](../docs/apps.md).

The starter contains:

- A SwiftUI dashboard and three themes loaded from Android's editable dashboard
  configuration, with native display, local touch, phone-data and results screens.
- A bounded observation model with retry/conflict checks and an incremental SLIP
  framing component. Framing does not implement Bluetooth, IP, TCP or STARLINK.
- Explicit local-only status. Local UI taps are not described as car requests.
- An on-demand list of accessories and protocol names that iOS exposes through
  External Accessory. No accessory protocol strings or identities are fabricated.
- Ten Swift component tests, four native UI journeys, a local Swift package and a
  checked-in Xcode project. CI builds and launches its own dedicated simulator.
- An original icon generator and privacy manifest for app-owned preferences.

## Mac build

Keep this in a new folder such as `~/Developer/SubaruBridge-iOS`, preserving the
existing Mac repository. With Xcode 15 or newer selected and its first-run setup
completed:

```sh
cd ios
bash scripts/mac-verify.sh
open SubaruBridge.xcodeproj
```

The script generates the original icon, runs `swift test` and builds for the simulator without device signing.
Choose an installed iPhone simulator in Xcode to launch the app. Physical iPhone
installation needs a signing team and normal device trust/developer setup; no
Apple account credentials or signing material are included here.

Run the native UI suite separately:

```sh
bash scripts/test-basic-ui.sh
```

It creates an isolated iPhone 15 Plus simulator named SubaruBridge Basic QA,
clears only this app on that simulator, then tests app navigation, disabled
actions, favorite-number validation, keyboard dismissal, notification denial and
the four-screen local check/export. Its screenshot and test result bundle stay
under ignored `build/`. No real call or SMS is sent.

For a signed device export after normal Xcode account/device setup:

```sh
DEVELOPMENT_TEAM=YOUR_TEAM_ID bash scripts/package-device.sh signed
```

Check that the embedded profile permits your phone. An IPA is not installed by
opening it in Files. Use Xcode's normal device installation and enable Developer
Mode when iOS requests it. The explicit `unsigned` packaging mode produces a file
named `requires-signing`; it cannot be installed as-is. Never commit certificates,
profiles, credentials or signed exports.

The copied JSON resource is in
`Sources/SubaruBridgeCore/Resources/dashboard.json`. The canonical Android UI
configuration remains `android/headunit-ui/dashboard.json`; keep this copy in
sync until a common build-resource pipeline is added. No Android code was replaced.

## Transport decision still required

Android's observed STARLINK path is vendor RFCOMM, SLIP, IP, TCP and HTTP.
Apple documents Core Bluetooth Classic as GATT over BR/EDR; that is not evidence
that its API exposes this RFCOMM service. External Accessory communication
depends on the accessory's supported protocols and manufacturer authorization.
The first iPhone test should inspect what the car actually exposes, using the
normal pairing/authentication path, before implementing a direct transport.

An iPhone-to-Pixel companion is a separate option if direct authorized access
does not fit. Standard car audio/calling and app data transport must be tested
separately. The starter does not intercept system-wide messages or calls, relay
audio, bypass the retired service, claim certification, or modify the car.

Primary references:

- [External Accessory](https://developer.apple.com/documentation/externalaccessory/)
- [Core Bluetooth Classic explanation](https://developer.apple.com/videos/play/wwdc2019/901/)
- [Accessory protocol declaration](https://developer.apple.com/documentation/bundleresources/information-property-list/uisupportedexternalaccessoryprotocols)

## Verification and remaining work

The Mac passed the ten component tests, all four UI journeys, an Apple SDK build
and development archive/export. The owner reported successful physical iPhone
launch and local notification delivery. This does not verify music-library
playback, real calls/messages, car connectivity or an iPhone-to-Pixel transport.

Next, test permission grant/denial and real library selection on a phone; open
directions, cancel the phone and message prompts, and inspect the car's normally
exposed accessories while parked. Keep logs private. Taurus media through the
Pixel is developed as a separate Android accessory experiment rather than
pretending the native iOS library player controls other apps.
