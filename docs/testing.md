---
layout: default
title: Test and report results
---

# Test and report results

Use the smallest test that can answer the question. The new independent app
must earn its own car result; previous successes through the legacy research
runtime do not certify it.

## Phone and emulator

1. Install your APK and open SubaruBridge.
2. Home shows the connection status and prepares Bluetooth after permission setup.
   Tools contains the Rust status. Bluetooth permission is not needed for preview.
3. The dashboard loads under **Preview · JavaScript off**. Use Check to visit
   Display, Touch, Phone and Results. Send three touches, read phone data and
   save the result. Change themes and layouts under Appearance, then reload.
4. With the app open, run the HTTP regression against that selected device:

```sh
python3 scripts/test_starlink_http.py --serial YOUR_SERIAL --output .local/http-test
```

The script forwards only a local ADB port, removes it afterward and checks 61
cases, including a timestamp/absolute launcher POST on one HTTP connection,
form retries, rejected malformed inputs, saved results and all appearance choices.
It changes test state and restores Midnight/grid. It does not contact a car.

For device-side JNI tests install the generated Android test APK and run:

```sh
adb -s YOUR_SERIAL shell am instrument -w org.subarubridge.app.test/org.mlpi.pixelprobe.starlink.BridgeCoreSmoke
```

Result code `-1` means success. The Rust native tests also check SLIP splitting,
checksums, reordering, retransmission, sequence wrapping and bounded flows.
They are targeted regressions, not a TCP conformance certification.

## Unified recorder tests

`BridgeCoreSmoke` runs `RecorderChecks` in an isolated cache directory. It checks
duplicate starts, bounded/sanitized notes, rejection of unsafe identifiers and
active deletion, saved ZIP contents, process-interruption recovery, the one-MiB
limit, twelve-session retention, journal rotation, oversized-event preservation,
symlink rejection, phone self-check results and the native recorder screen. It
also renders the packaged adaptive/themed icon to a test-cache PNG for review.
It does not replace the owner's live recordings or Garage.

For a device journey, open Home → Record & diagnose, start a recording, add a
note clearly stating that this is a phone-only bench test, leave the app and lock
the phone, then return and stop/save. Inspect the timeline and ZIP, including the
saved note and phone samples. Test notification denial, force-stop interruption
and a filled journal separately. Exact head-unit results still require the car.

## Garage tests

`BridgeCoreSmoke` also runs isolated Garage storage/form checks. It uses synthetic
cache files, with a process-local store injection for native UI forms. No intent,
HTTP request or external app can select that test store. Tests do not seed or
replace the owner's real garage. They cover restart, interrupted/failed writes,
concurrent writers, corruption/unknown-schema preservation, validation rejection,
backup idempotence/conflicts, units/money, export escaping and reminder filtering.

Run the shared tests using `cargo +1.90.0 test --manifest-path rust/Cargo.toml --locked`.
The Garage schema/calc suite has 44 cases plus 6 analytics cases; the existing
8 media and 18 protocol cases remain included (76 shared tests). For manual
checks, use an emulator and a clearly labeled test
vehicle. Log an odometer, approximate service, full/partial fill, expense and
completed trip. Restart the app, inspect history and export/import a backup.
Verify the native system file picker and actual notification delivery separately
from component tests. Extended idle/reboot reminder delivery remains outstanding.

Native checks also exercise missing/repeated daylight-saving hours and explicit
offset round-trips. Analytics screens are checked for read-only execution. A
physical car is not required for any ownership calculation or storage test.

Run the same Rust cases directly on the phone/emulator ABI with:

```sh
python3 scripts/test_android_core.py --sdk "$ANDROID_HOME" --serial YOUR_SERIAL --abi arm64-v8a --output .local/native-arm64
```

Use `--abi x86_64` for an x86-64 emulator. The utility verifies the selected
device ABI, builds each test executable with the pinned NDK, runs it in a fresh
temporary shell directory, checks the actual test-result lines and removes only
the transferred files. It uses no root or vehicle connection. Output can contain
local build paths and must remain private. Configure the pinned Rust/Cargo
environment from the build guide first.

## Parked-car test

1. Park, keep the vehicle stationary and use normal accessory power.
2. Pair the Android phone using the head unit's ordinary Bluetooth setup.
3. Close other STARLINK or research listener apps. In SubaruBridge open the app, allow Nearby devices, and wait
   for **Ready to connect**.
4. Connect the car's Bluetooth and choose **Apps → STARLINK** on the head unit.
5. Record exactly what appears. If the dashboard opens, choose Check and visit Display, Touch,
   Phone and Results. Send three separate touches, read phone data, save the
   result and photograph the results screen. Wait for each response.
6. Disconnect and reconnect Bluetooth once. Record whether the dashboard reloads.
7. Pause the connection in Tools on the phone. Record head-unit model/software, Android
   version, app revision, whether each screen worked and any error text.

The app records confirmed events in its private
`files/subarubridge-diagnostics/events.jsonl`. Use **Export private report**
on the phone to save its own event journal and build/status information in Downloads.
This works without a PC or root. The report also includes connection-stage and
byte counters plus a bounded history of known HTTP route categories/statuses.
It labels phone-preview requests separately from Bluetooth requests. This helps
distinguish no app-channel connection, received bytes without HTTP, and an HTTP
reply without needing a laptop. Request bodies, headers, addresses and raw
Bluetooth/USB packets are not included.
`adb shell run-as org.subarubridge.app`
can read this file from your own debug build after the test. A full HCI capture
requires separate OS support; the app cannot grant itself that access. The Android
Linux Terminal VM does not automatically expose the host Bluetooth stack.
Capture privately when needed, and publish only reviewed summaries.

## What evidence proves

| Observation | What it establishes |
| --- | --- |
| Phone preview renders | Local routes and browser markup work on that WebView |
| Rust tests pass | Their synthetic protocol cases pass on the tested ABI |
| Windows Bluetooth peer receives HTTP | Real radio/socket/native transport works with that peer |
| Head unit displays dashboard | Car bootstrap and HTML delivery progressed |
| Confirmed touches increase | Car form submissions reached Android and returned |
| Tile stays gray/error appears | A failure remains; capture the stage before assigning a cause |

No packet ACK alone proves descriptor acceptance. The earlier MirrorLink gray-tile
and Pi-host USB EPIPE observations concern a separate path and remain distinct
from this Bluetooth STARLINK work.


## Dashboard apps: parked-car follow-up

Use this after the independent transport and basic touch test pass.

1. On the Pixel open **Apps & integrations**. Grant notification access through
   Android's screen if you want music, directions or notification reads. Enable
   message/general sharing only for the categories you want. Set favorites if
   needed. Keep the Pixel unlocked during these checks.
2. Start Spotify or another compatible music app on the Pixel, then pair normal
   Bluetooth media/call profiles with the Subaru. On the dashboard open Music,
   send Pause/Play and refresh. Record actual audio and the music app's response;
   an accepted request alone does not establish playback.
3. Start Google Maps navigation on the Pixel. Open Maps on the dashboard and
   refresh. Expect notification direction text, not a live embedded map.
4. Generate an ordinary test notification, for example a timer alert. Open
   Notifications and refresh. Test ongoing notifications separately with the
   relevant switch enabled. Android can withhold sensitive content.
5. Prepare a favorite call or new SMS. Nothing is placed or sent until the phone
   opens its normal app and the user completes the action. Use a consenting test
   recipient if performing a real call/message test.
6. For a current message notification offering Reply, prepare the quick reply,
   review it on the Pixel and confirm once. Record actual receipt independently.
7. Test Bluetooth reconnect, then save **Tools → Export private report**. Note
   the failing step, exact screen text and whether the phone was locked. Keep
   photos and reports private until reviewed.

The [capability guide](apps.md) lists the implemented adapters and remaining limits.
The [iPhone bridge plan](iphone-bridge-plan.md) separates the next physical iPhone
experiments from the simulator evidence.

## Android ownership 0.58

BridgeCoreSmoke now runs OwnershipDataChecks using private synthetic fixtures.
It checks photos/PDFs, complete archives, encrypted multi-chunk round-trips,
wrong passwords, corrupted/truncated encrypted files, path traversal, migration,
future storage preservation, stale restore review and the native encrypted
restore journey. The test has no intent-based fixture injection in production.

The optional `-e reminderProbe prepare|idle|reboot|finish` instrumented stages
save and restore reminder preferences without seeding owner maintenance. Prepare
posts a test notification and schedules the normal daily check. Idle sends the
actual private receiver while a developer has placed the device in deep idle.
Reboot verifies the system boot receiver after ordinary unlock. Always run finish
and restore device idle/power settings. This checks accelerated receiver recovery,
not overnight scheduler delivery. Run only on the explicitly selected device.

`-e ownershipPreview true` captures six real native screens from an isolated
sample garage into private test-cache PNGs. No demonstration records enter the
owner's garage. Original manufacturer PDFs, exports and device logs remain
excluded from public source.

## Android connection and UI 0.59

`BridgeCoreSmoke -e connectionOnly true` exercises original connection logic with
isolated, process-local transports. The cases include failed listener open/accept,
unpaired rejection, ended sessions, Bluetooth off/on with the Activity finished,
permission loss, cancellation during open/accept, renewable bounded power leases,
preview-port collision, a real Android foreground-service lifecycle and persisted
notification Pause followed by explicit Resume. No synthetic transport is selected
through an Intent, network route or user setting.

The complete Android 17/API 37 emulator suite passes alongside Garage storage,
encrypted restore, receipts, app-owned recording, real synthetic MediaSession and
notification/RemoteInput fixtures. These are app tests, not head-unit evidence.
The 0.58 physical Pixel ownership, deep-idle receiver and reboot checks remain
recorded in [the ownership release](ownership-058.md). A fresh physical 0.59 car
test is still required. See [the parked-car directions](android-059.md).

Four factory-address regressions supplement the eighteen existing protocol
cases. They check the factory SYN options, HTTP exchange and response checksums,
ICMP return address, invalid-source rejection and peer pinning across reconnect.
The three factory-dependent tests fail against the old bench-only stack and pass
after correction. `FactoryLinkChecks` also sends a synthetic factory-address
handshake and GET through the actual Android Rust/JNI/router boundary. No private
packet bytes or proprietary source are included in the fixtures.
