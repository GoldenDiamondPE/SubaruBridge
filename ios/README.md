# SubaruBridge iOS starter

This is the first iOS source branch alongside Android, targeting iOS 17 or later.
**Apple SDK verification runs in the macOS CI job; physical iPhone and car validation remain required.** The Windows host can prepare the source,
but the Mac is needed for Swift tests, Apple SDK validation and simulator work.
This source is not a signed installable IPA or a verified Subaru connection.

The companion now includes Music, Maps, Phone, Messages and Notifications cards.
Apple Music library playback uses the native player; Spotify opens through its
normal link. Maps opens a favorite destination, calling uses the iPhone prompt,
and SMS uses MessageUI with user confirmation. System-wide iPhone notification
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
- Ten Swift component tests, a local Swift package, a checked-in Xcode
  app project, and an unsigned simulator-build script. CI runs the component suite and unsigned simulator build.

## Mac build

Keep this in a new folder such as `~/Developer/SubaruBridge-iOS`, preserving the
existing Mac repository. With Xcode 15 or newer selected and its first-run setup
completed:

```sh
cd ios
bash scripts/mac-verify.sh
open SubaruBridge.xcodeproj
```

The script runs `swift test` and builds for the simulator without device signing.
Choose an installed iPhone simulator in Xcode to launch the app. Physical iPhone
installation needs a signing team and normal device trust/developer setup; no
Apple account credentials or signing material are included here.

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

## Next verified milestones

1. Recover authorized Mac access and inventory macOS, CPU, Xcode, SDKs and runtimes.
2. Run the written core tests and fix actual compiler/test failures.
3. Launch the simulator and verify configuration, navigation, three touches,
   phone data, local export and failure states.
4. Sign for the owner's iPhone and inspect app-accessible accessories during a
   parked-car pairing test. Preserve logs privately.
5. Choose an evidenced direct or Pixel-companion transport before promising
   a head-unit display or adding music/calling/message integrations.
