---
layout: default
title: Verified results
---

# Verified results — 0.51-dev

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
