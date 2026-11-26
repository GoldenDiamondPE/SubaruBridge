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
2. The **Rust core** status should say ready. No Bluetooth permissions are needed
   for the phone preview.
3. The dashboard loads under **Preview · JavaScript off**. Use Check to visit
   Display, Touch, Phone and Results. Send three touches, read phone data and
   save the result. Change themes and layouts under Style, then reload.
4. With the app open, run the HTTP regression against that selected device:

```sh
python3 scripts/test_starlink_http.py --serial YOUR_SERIAL --output .local/http-test
```

The script forwards only a local ADB port, removes it afterward and checks 51
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

## Parked-car test

1. Park, keep the vehicle stationary and use normal accessory power.
2. Pair the Android phone using the head unit's ordinary Bluetooth setup.
3. Close other STARLINK or research listener apps. In SubaruBridge press
   **Start car connection**, allow Nearby devices and notifications, and wait
   for **Ready: waiting for paired car Bluetooth**.
4. Connect the car's Bluetooth and choose **Apps → STARLINK** on the head unit.
5. Record exactly what appears. If the dashboard opens, visit Display, Touch,
   Phone and Results. Send three separate touches, read phone data, save the
   result and photograph the results screen. Wait for each response.
6. Disconnect and reconnect Bluetooth once. Record whether the dashboard reloads.
7. Stop the car connection on the phone. Record head-unit model/software, Android
   version, app revision, whether each screen worked and any error text.

The app records confirmed events in its private
`files/subarubridge-diagnostics/events.jsonl`. Use **Export private test report**
on the phone to save its own event journal and build/status information in Downloads.
This works without a PC or root. It includes no raw Bluetooth/USB packets.
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
