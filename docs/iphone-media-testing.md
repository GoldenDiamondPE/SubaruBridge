---
layout: default
title: iPhone media test
---

# Test Taurus through the Pixel

This is a phone bench test first. A successful phone test does not establish
Subaru compatibility. Android 0.54 contains the experimental client; no Taurus
modification, account credentials or replacement iPhone build is required.

## Before the car

1. Open SubaruBridge on the Pixel. Allow Nearby devices if requested, enable
   Bluetooth, and leave the car connection **Ready**. Keep both phones unlocked.
2. Pair the iPhone and Pixel normally in the phones' Bluetooth settings. If the
   pairing cannot complete, record that result; do not change accessory identities.
3. Open Taurus on the iPhone and start a track you are allowed to play. Check that
   the iPhone's own Now Playing controls show its title and Play/Pause state.
4. In SubaruBridge, open **Apps & integrations**. Enable **Music controls**, choose
   **iPhone media · experimental**, and tap **Connect paired iPhone media**.
   Choose your iPhone. Confirm any normal Bluetooth authorization prompt.
5. Wait for the status. Record the exact message if it times out, cannot discover
   Apple Media Service, or returns a GATT error. These results concern the
   phone-to-phone connection, not the Subaru.
6. Open the Pixel's dashboard preview, then **Music**. Compare title, artist,
   album, state and position with the iPhone. Shortened text should be marked.
7. Try one offered control at a time: Pause, refresh and verify the iPhone paused;
   then Play, Next and Previous when offered. Record missing controls rather than
   assuming a button exists. Do not infer audio delivery from a successful request.
8. Switch to a different iPhone music app. An old Music-page form must be rejected
   if the reported player changed. Refresh to use the new player's controls.
9. Disconnect iPhone media. Refresh Music: old metadata and controls must disappear.
   Reconnect and confirm that old forms remain invalid.
10. Select Android music again. The Android player must work independently; a
    failed iPhone connection must never silently control it.

## Parked Subaru follow-up

Only after the phone bench works, pair the iPhone for the car's normal media and
hands-free audio, keep the Pixel's STARLINK connection, and test whether this
particular head unit permits both roles. Verify actual sound, Pause/Play and
reconnection independently. Leave factory restrictions and firmware intact.
Do not test calls or messages as part of the media check.

## Results to return privately

- Android build and both OS versions; Taurus version.
- Which pairing and connection steps completed; exact status/error.
- Whether title, artist, state and each offered command matched the iPhone.
- Whether the iPhone exposed AMS; whether a shortened-text marker appeared.
- Whether disconnect cleared content and stale commands were refused.
- For the separate car test: audio source, head-unit outcome and port if used.

Keep screenshots containing track history, paired-device names or notification
content private. This adapter's status includes accepted/rejected update counts;
the private report also includes its last GATT result and public characteristic
property bits. It does not export raw Bluetooth attributes or song history.
Export the report before force-stopping the app; the media status is held in memory.

A Classic Bluetooth pairing alone may be insufficient: AMS needs a working LE
bond and access to the iPhone's GATT database. If this fails, the next isolated
experiment is normal BLE accessory pairing and service-solicitation advertising,
not a change to Taurus or the Subaru firmware. That pairing mode is not included
in this first client.

Reference: [Apple Media Service](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Introduction/Introduction.html).
