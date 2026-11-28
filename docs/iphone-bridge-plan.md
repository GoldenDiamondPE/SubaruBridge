---
layout: default
title: iPhone bridge experiments
---

# iPhone to Subaru: next experiments

For the current implementation and platform research, see the updated
[iPhone → Pixel → Subaru guide](iphone-pixel-subaru.md). Android 0.59 retains the
optional AMS client. iOS 0.59 provides native setup guidance; ANCS forwarding and
audio repeating are not implemented.

The native iOS companion now builds and launches in a simulator. Direct car
transport and an authenticated iPhone-to-Pixel app link are not implemented.
Keep the Android RFCOMM transport, iPhone app actions and factory Bluetooth audio
as separate components when testing.

## Notification path worth evaluating

An ordinary iPhone app cannot read other apps' system-wide notifications. Apple
also documents the Apple Notification Center Service (ANCS), which exposes many
iOS notifications to authorized Bluetooth LE accessories. This is a different
interface from an iOS app's UserNotifications framework.

The proposed experiment is to use the Pixel as a normally paired BLE notification
consumer. First establish whether the owner's iPhone exposes ANCS and permits
access through the normal authorization flow. Do not assume availability: Apple
says the service may appear or disappear, all ANCS characteristics require
authorization, and only Notification Source is mandatory.

If access succeeds, a bounded, original decoder can handle add/update/remove
events and optional fragmented attributes. Keep identifiers and content scoped
to the current session and clear them on disconnect. Do not implement notification
actions until the phone's supplied action labels and the user's confirmation
flow have been verified. ANCS actions are not arbitrary message composition.

This is a research plan, not an implemented or verified iPhone notification relay.
No new pairing, notification read or action is performed by the current app.

## Audio and calls

Test the factory iPhone A2DP/HFP pairing independently. Then test whether the
Fujitsu Ten unit can retain iPhone media/call profiles while a Pixel handles its
STARLINK data session. Do not assume the unit supports those simultaneous roles.
ANCS is not an audio or telephone-audio relay.

## Native iOS app checks still needed

On an authorized Mac/iPhone setup, sign the app normally and verify music-library
permission denial/grant, real library playback, Google Maps launch, call prompt,
SMS composer cancellation and sending to a consenting test recipient. Simulator
tests cannot establish these physical-device behaviors. Spotify remote control
needs a separate authorized integration; the current iOS app opens Spotify.

References: [Apple ANCS introduction](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Introduction/Introduction.html),
[ANCS characteristics and session rules](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html),
[External Accessory](https://developer.apple.com/documentation/externalaccessory),
[UserNotifications](https://developer.apple.com/documentation/usernotifications).
