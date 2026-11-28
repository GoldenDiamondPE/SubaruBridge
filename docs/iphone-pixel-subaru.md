---
layout: default
title: iPhone, Pixel and Subaru
---

# iPhone → Pixel → Subaru

Updated 2026-11-28. This is an experimental split connection, not a completed
phone-call or audio repeater. The iOS app includes a setup guide; the Android
app contains the existing optional Apple Media Service client. Neither device
needs a computer during normal use.

## What each link can do

| Path | Current implementation | Evidence still needed |
| --- | --- | --- |
| Pixel → Subaru | Original paired STARLINK data listener, local dashboard and recorder | Display, touch and reconnect on the actual factory unit |
| iPhone ↔ Pixel | Optional AMS track information and offered playback commands, including Taurus when it publishes Now Playing | An authorized LE bond, actual iPhone service discovery and player responses |
| iPhone → Pixel → Subaru notifications | Research only; no notification consumer in this build | ANCS availability and authorization, bounded event/attribute handling and redacted rendering |
| iPhone → Pixel incoming-call alerts | Research only, through possible ANCS call notifications | Exact phone-provided action labels and explicit confirmation; no assumed Answer/Reject mapping |
| iPhone → Subaru music/call audio | Factory Bluetooth pairing is the first test path | Whether this head unit keeps iPhone A2DP/HFP active alongside Pixel STARLINK data |
| iPhone → Pixel → Subaru audio | Not implemented | Audio-receiver and hands-free-client roles, actual routing and latency; not just app-level messages |

Apple's [Media Service specification](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Specification/Specification.html)
defines metadata and remote commands. It is not an audio stream. A successful
command write does not prove that the player acted. Taurus compatibility must
be checked against the phone's actual Now Playing state, not a package name.

Apple's [Notification Center Service specification](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html)
defines authorized accessory notifications. Service availability is dynamic;
optional characteristics and message contents cannot be assumed. A later client
must handle fragmented data, changed/removed notifications and disconnect
cleanup. Phone-provided action labels must determine any action presented to
the user. A call notification does not transport the conversation's audio.

The newer [Accessory Notifications framework](https://developer.apple.com/documentation/accessorynotifications)
offers another iOS extension path, but Apple currently limits customer use to
EU-located iPhones with EU Apple accounts. It is not a general substitute for
an ordinary app reading other apps' alerts. This project does not enable that
framework or claim eligibility.

Android's [Automotive Bluetooth documentation](https://source.android.com/docs/automotive/ivi_connectivity)
describes A2DP sink and HFP client roles for vehicle systems. Their presence in
Automotive does not establish their availability to an APK on a Pixel. Rebuilding
the Bluetooth stack, changing the ROM or forwarding protected audio is not part
of this release. Normal installation does not require root.

## Verification order

1. Run the Pixel's standalone STARLINK check while parked and save a private report.
2. Follow the [iPhone media bench test](iphone-media-testing.md). Record each
   discovered capability and offered command without assuming audio delivery.
3. Separately verify factory iPhone music and hands-free audio.
4. Combine iPhone audio with Pixel STARLINK. Record profile conflicts and which
   device the head unit selects after reconnecting.
5. Develop notification forwarding only after a normal authorized ANCS connection
   is demonstrated. Keep previews off by default and session content out of logs.

No packet replay, safety-system commands or factory firmware changes are needed
for these tests. Preserve the existing native call controls and camera behavior.
