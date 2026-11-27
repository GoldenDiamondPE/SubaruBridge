---
layout: default
title: Verified results
---

# Verified results

## Android 0.57 consolidation — 2026-11-27

The existing Android Garage, media adapters and independent STARLINK dashboard
now share the original app ID with an on-device recorder and developer view.
The old private apps are not bundled or required. Earlier protocol experiments
remain preserved separately; this is not a claim of complete MirrorLink, AirPlay,
CarPlay or iPhone parity.

| Check | Observed result |
| --- | --- |
| Android builds and lint | Both ABIs and test APK build; lint passes with no errors (existing advisory warnings remain) |
| Pixel 6 and x86-64 emulator component suites | Core, Garage, Apple-media decoder, Android integration, recorder and native navigation suites pass |
| HTTP contract | 61/61 on Pixel and 61/61 on emulator |
| Recorder regressions | Duplicate start, note sanitization, safe IDs, active-delete rejection, ZIP scope, interruption, byte/count limits and journal rotation pass |
| Phone-only recorder journey on Pixel | Start, private bench-test note, background operation, stop/save and actual Downloads ZIP verified; interrupted/saved outcomes stay distinct |
| Screen-off emulator recording | Fifteen passive samples over 70.7 seconds; bounded wake lock held during the test and released after the UI stop/save |
| Real Windows-to-Pixel Bluetooth bench | Five complete HTTP 200 responses, with earlier listener apps disabled |
| Wireshark cross-check | 157 RFCOMM frames in the test interval; 107 reconstructed IPv4 packets decode all five successful HTTP responses |
| Packaged launcher icon | Original adaptive icon rendered from the installed APK; round/themed resource available and reviewed |
| Private-data preservation | Older Lab/research data and current app records backed up before duplicates were disabled; no app data was cleared |
| Publication boundary | Ten regression cases pass; original-source audit excludes vendor applications, SDKs, captures, credentials and unreviewed binaries |

The radio peer is a synthetic Windows client, not the Subaru. The vehicle's
independent bootstrap, display, input, audio coexistence and reconnection remain
hardware checks. iPhone/Taurus playback and direct iOS transport are not verified
by these Android component tests. iOS development remains paused.

## Earlier results — 0.51-dev

Recorded 2026-11-26. Raw evidence remains private; only results are summarized here.

| Test | Result |
| --- | --- |
| Clean source-only Android build, JDK 17 / Gradle 9.4.1 / NDK 28.2 | Passed; both Android ABIs and test APK build |
| Rust protocol regressions on x86_64 Android emulator | 18 passed |
| JNI lifetime and local-route device tests | Passed on Pixel 6 and x86_64 emulator; 64 handle lifetimes each |
| Local HTTP contract tests | 51 passed on Pixel, 51 on emulator |
| Real Windows-to-Pixel Bluetooth transport | 112 complete HTTP responses, eight saved four-screen walkthroughs, no failed requests in this test |
| Radio reconnect and full app-process restart | Passed in that Bluetooth test |
| Legacy STARLINK applications | Stopped during the independent transport test |
| Interactive emulator with JavaScript disabled | Four screens, three confirmed touches, phone-data read and saved result verified |
| Phone-only private report export | ZIP integrity and own diagnostic contents verified |
| Publication-boundary and inventory regressions | Nine tests passed |
| Final Bluetooth keep-alive check | Timestamp, launcher POST and version succeeded on one TCP connection |
| Final closed-connection burst | 32 further HTTP responses completed |
| Offline transport metadata | Radio counters and source labels verified; bootstrap body excluded |

The radio run lasted approximately 128 seconds. It used a synthetic Windows peer,
not a Subaru emulator or the actual car. The final build also adds rejection of
control characters in paths and correct parsing of comma-separated Connection
tokens; those changes have targeted device regressions.
The app-owned offline diagnostics were then tested with a further 35 real-radio
transactions and a three-file private ZIP export. The byte counters were nonzero,
and synthetic bootstrap-body markers were absent from the saved metadata.

The deeper tests reproduced and fixed overlapping TCP reassembly and premature
active-flow exhaustion after completed closes. A listener-start intent delivered
to an existing activity is now handled, and the private test recorder checks the
listener's actual readiness rather than process existence.

**Still required:** run the independent app against the actual FB856UM head unit.
Verify its bootstrap, dashboard, three confirmed touches, saved result, appearance
changes and Bluetooth reconnect. Earlier car HTML delivery through a different
research runtime is not proof that this new app passes those checks.

This is a development preview. It is not feature parity with the discontinued
STARLINK app, and music, calls, messages, navigation and an iOS application are
not implemented by this release.


## 0.53 dashboard app development

- Android app and instrumentation builds complete for arm64 and x86-64.
- Emulator integration fixtures reached a real Android MediaSession callback and
  delivered exactly one confirmed RemoteInput reply to a synthetic receiver.
  Repeated and stale confirmations were rejected. No real SMS or call was sent.
- Intent construction, Maps query encoding, general-notification bounds and
  filtering, disabled-sharing cleanup, existing-notification reseeding and private
  report exclusion have targeted component checks.
- The local-route suite passes 61 checks on both the emulator and physical Pixel.
  Personal preview routes require an app-private session.
- Pause, leave and launcher-reopen reached Ready on the emulator.
- The legacy browser preview renders the dashboard at 800 by 480 without scripts.
- macOS CI passes ten Swift component tests, an unsigned Apple SDK simulator
  build and a cold iPhone-simulator launch. See the
  [iOS check run](https://github.com/GoldenDiamondPE/SubaruBridge/actions/runs/45930187043).

These are component and bench observations. Actual Spotify account playback,
Google Maps notifications, Bluetooth audio, phone/SMS delivery, physical iPhone
behavior and the new independent transport on the Subaru still need separate
verification. The earlier private research adapter's car result is not a result
for this implementation.

## 0.54 Android and Apple media foundation

- Both Android ABIs compile. Android CLI identifies the artifact as 0.54-dev / 54.
- The 26 Rust tests pass on Windows x86-64, native Apple-silicon macOS, the x86-64
  Android emulator and the physical arm64 Pixel. Eight cover the new AMS decoder;
  the 18 existing protocol regressions remain passing.
- The same original Rust library compiles for the arm64 iPhone target. This is a
  component build, not a new iPhone app or hardware transport test.
- Android instrumentation on Pixel and emulator passes decoder/JNI lifetime,
  metadata/capability/session cleanup and MediaSession tests, including controls
  reaching a real synthetic Android session. Stale targets and replay are rejected.
- The script-free local HTTP suite passes 61 checks on each Android device.
- Notification access was initially off on the Pixel. After normal on-screen
  permission setup, its full Android integration suite passed.

The iPhone GATT client, normal LE pairing, Taurus playback and simultaneous car
audio/data remain **hardware verification required**. No Taurus APK/IPA, patched
player code, audio stream, credential or personal capture is part of this source.

## 0.55–0.56 integrated Garage and ownership summaries

Recorded 2026-11-27. Tests use isolated fixtures or explicitly synthetic emulator
records. They do not add fictional history to the owner's phone Garage.

| Check | Observed result |
| --- | --- |
| Shared Rust core | 76 cases passed on Windows, native Apple-silicon macOS, physical arm64 Pixel and x86-64 Android emulator: 44 ownership, 6 analytics, 8 Apple-media decoder and 18 protocol cases |
| iPhone architecture | The same original Rust core compiled for arm64 iPhone; this is not a native iOS Garage screen or an iPhone transport result |
| Android build | 0.56-dev / version code 56, both ABIs and test APK built; inspected and installed with Android CLI |
| Full Android component/UI fixtures | Passed on Pixel and emulator: atomic persistence, interrupted/rejected writes, future/corrupt/deep JSON preservation, backup/conflict handling, concurrent saves, escaped exports, time-zone/unit/locale conversion, native forms and read-only summaries |
| Existing integrations | Existing JNI lifetime, media callbacks, confirmed synthetic notification reply, stale/replay rejection, Maps/call/SMS intent construction and notification bounds remain passing; no real call or SMS was sent |
| Script-free HTTP contracts | 61 passed on the final build on each Android device |
| Normal emulator UI | Created a demo vehicle and confirmed reading, saved one two-item service visit, recorded a completed manual trip, and viewed estimated mileage and the native summary chart |
| System file picker | Exported the demo Garage JSON and imported the same file; the saved revision and record counts did not change and the two-item visit was not duplicated |
| Local reminders | A counts-only maintenance notification appeared for the sample overdue records; its daily alarm was registered and restored after emulator reboot; reminders were switched off afterward |
| Visual inspection | Reviewed native phone screenshots and fixed unthemed navigation/chart controls plus content scrolling under the system status bar |
| Public design demo | Original source templates rendered at desktop and phone widths; theme/layout selection, app navigation, browser-only play/pause, safe directions feedback and screenshot selection were checked |
| Publication boundary | Ten regressions passed, including exact-byte enforcement for the individually reviewed synthetic screenshots; proprietary/private material stays excluded |

The 18 normal-condition manufacturer rules retain their official Subaru table
source and explicit applicability. Severe-condition footnotes, critical technical
specifications and parts compatibility are not guessed. Unknown service history
remains unknown; costs and mileage explain their recorded coverage.

**Still outstanding:** independent physical Subaru bootstrap, rendering, touch
and reconnect; iPhone/Taurus GATT playback; extended physical-phone idle reminder
timing; native iOS Garage; automatic GPS trips; read-only OBD-II/live telemetry;
receipt attachments and encrypted backups. The website is a design preview, not
a connected vehicle test or a claim of complete CarPlay/STARLINK feature parity.
