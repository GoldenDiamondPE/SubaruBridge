---
layout: default
title: Android 0.59 update
---

# Android 0.59: standalone connection and clearer screens

Install the APK from the project's GitHub release. Normal use needs neither a
computer nor the discontinued STARLINK app. Pair the Pixel with the Subaru,
open SubaruBridge, allow Nearby devices and open Apps → STARLINK while parked.

## Connection fixes

- Correct a real factory-address rejection: earlier car captures use head-unit
  `192.168.0.1`, whereas the old stack accepted only the synthetic bench client's
  `192.168.1.2`. Pin a validated peer for each Bluetooth link and address all TCP
  and ICMP replies correctly. Keep the bench compatible without accepting
  arbitrary addresses or allowing an active link's peer to change.
- Recover failed listener opens, accepts and ended sessions in the foreground
  service, including when Home is closed. Retry with a bounded delay.
- Wait through Bluetooth off/on changes without depending on an Activity.
- Persist explicit Pause, including the notification's Pause button; Resume
  clears it. System service restarts respect that saved choice.
- Distinguish listener ready, Bluetooth connected and actual STARLINK requests.
  Requests alone do not establish display or touch success.
- Keep a phone-preview bind failure independent of the car listener. Use another
  loopback port if the preferred one is occupied; preview authentication,
  same-origin checks and phone self-checks follow the selected port.
- Renew a bounded active-session power lease for longer connections, and prevent
  a timer from reacquiring it after the session ends.
- Handle foreground-service and Nearby permission failures without an unhandled
  startup exception. Keep the recorder's partial results when startup fails.

## Readability

Home now puts connection status and the four daily destinations first. Pairing
steps are short, controls retain useful touch targets and supporting labels are
larger. Filled controls adapt their background for readable text. Technical
status remains in Tools behind an expand control. Setup and Garage use the same
window colors and respect system bars and keyboard insets.

The full [Android ownership update](ownership-058.md) is included: local receipts,
PDFs, complete/encrypted backups, reviewed restore, verified reference data,
reminder fixes and fuller summaries. iOS setup and the experimental
[iPhone/Pixel route](iphone-pixel-subaru.md) are tracked separately; this is not
a claim that iPhone notifications or call audio already pass through the Pixel.

## Parked-car test

1. Home should say Ready to connect. Start **Record & diagnose → Start recording**.
2. Open **Apps → STARLINK** on the car and check that the dashboard loads.
3. Use **Connection check**: display, three phone-confirmed touches, phone data,
   then save a result. Photograph errors or a screen that stops responding.
4. Leave it connected for two minutes. Disconnect/reconnect once and open
   STARLINK again. Record the outcome instead of resetting the head unit.
5. **Stop and save → Export private diagnostics ZIP**. The ZIP appears in
   Android **Files → Downloads** as `subarubridge-report-<timestamp>.zip`.

Physical-car results are still required. Automated transports and simulators
exercise app behavior; they do not verify the factory head unit.

The owner's 0.58 car report showed a Bluetooth connection, 896 received bytes,
zero transmitted bytes and the factory install-STARLINK error. No car HTTP
request reached the app. The archive contained metadata, not raw packets, so it
cannot independently identify those 896 bytes. Comparing the earlier factory
captures exposed the address bug; synthetic regressions failed before its fix
and pass afterward. A fresh physical test must verify the corrected build.
Reports now distinguish Bluetooth HTTP from phone preview/self-check requests.
