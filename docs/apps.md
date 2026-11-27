---
layout: default
title: Dashboard apps
---

# Dashboard apps and platform boundaries

The Android dashboard has Music, Maps, Phone, Messages and Notifications cards.
It remains usable without browser scripts. The original connection checks,
appearance settings and private diagnostic export remain available.

## Android setup

1. Open SubaruBridge, then **Apps & integrations**.
2. Open **Manage notification access** and grant access only if you want music,
   directions or notification features. Android presents its own permission screen.
3. Enable the categories you want. Messages and general notifications are off by
   default; ongoing notifications have a separate setting.
4. Optionally save a favorite phone number, Google Maps destination and quick reply.
5. Pair normally with the car. Open the dashboard preview before a parked-car test.

| Card | Implemented adapter | Limit |
| --- | --- | --- |
| Music | Select an Android MediaSession provider; track, artist, album, elapsed/duration snapshots and advertised controls | Start a compatible player. Toggle-only Play/Pause is offered only in a known appropriate state. Old forms cannot target a replacement session. |
| Maps | Google Maps navigation-notification title/text; Maps URL for a favorite destination | Direction cards, not embedded interactive maps. Notification formatting can vary by app version. |
| Phone | Prepare a favorite number, then open the system dialer after phone confirmation | Does not replace the default dialer or implement answering/hangup. Factory hands-free controls continue to handle calls. |
| Messages | Optional recent message notifications; confirmed replies through an app's offered RemoteInput action; system SMS composer | No full inbox, delivery receipt or iPhone message access. Phone confirmation is required before replies. |
| Notifications | Optional general Android notification feed, including ongoing notifications when enabled | Maximum 20 entries, 30-minute memory retention. Secret notifications and this app's own status notices are excluded. Android may withhold sensitive content. |
| Widgets | Phone time and battery snapshots | Refresh for current values. No weather or location is fabricated. |

Private app content is shown only while the phone is unlocked. Revoked access and
disabled sharing clear the relevant read model. Notification titles/text, favorite
numbers and destinations are excluded from diagnostic journals and exports.
Media actions use expiring, one-use, action-bound form grants; GET requests do not
change playback, place calls or send messages. Repeating an old form is rejected.
Pending phone confirmations expire after two minutes.
Personal app routes on the phone preview require an app-private session. The
paired car uses the separately authenticated Bluetooth connection. Enabling
sharing also reads the currently active notifications, subject to the filters.

## iOS companion

### Taurus and other iPhone players through the Pixel

Android 0.54 adds an **experimental Apple Media Service client**. In **Apps &
integrations**, enable Music controls, choose **iPhone media**, and select
**Connect paired iPhone media**. The iPhone and Pixel must first be paired through
their normal Bluetooth settings; the car connection service must be running.
Start Taurus on the iPhone, then inspect the Music card in the Pixel preview.

This reads the iPhone's reported active-player information and offers only its
reported controls. It does not depend on a Spotify bundle identifier or Taurus
internals. Taurus compatibility and service discovery still require physical
iPhone verification. The iPhone may not expose this service for a particular
pairing; an explicit failure is shown instead of falling back to Android music.

Track information stays in memory. Disconnect clears it and invalidates old
controls. Shortened Bluetooth text is marked. No audio relay, full playlist
browsing, album artwork transfer, or automatic reconnection is implemented here.
Use normal iPhone-to-car audio pairing. Simultaneous iPhone audio and Pixel
STARLINK data on the target head unit still requires a parked-car test.

See [the iPhone media test](iphone-media-testing.md) and
[Apple's AMS specification](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Specification/Specification.html).

### Native iPhone actions

The iOS source has native app cards, an Apple Music library player, a Spotify
opening link, Google Maps URLs, a system calling prompt and the system message
composer. It shares Android's dashboard configuration. These are phone-side
features, not a completed iPhone-to-car transport.

Ordinary iOS apps cannot read other apps' notifications or the system message inbox.
The Android RFCOMM transport cannot simply be used through Core Bluetooth.
External Accessory requires a compatible, authorized accessory protocol. No
protocol identity or accessory authorization is invented here.

An authenticated iPhone-to-Pixel general companion link is still a separate implementation
milestone. Pairing an iPhone for factory media/calls while a Pixel supplies the
STARLINK dashboard also needs a physical head-unit test.

## Customize

Edit `android/headunit-ui/dashboard.json` for app order, labels, icons and colors;
edit the HTML templates and CSS for layout. `apps` describes everyday cards;
`tiles` retains the four connection checks. Navigation-first ordering is also
available in phone Integrations. Keep the iOS resource copy synchronized with the
Android file; CI verifies that they match.

## Evidence still required

Synthetic Android fixtures can prove media command delivery to a real Android
MediaSession, intent construction, notification filtering and retry handling.
They cannot prove Spotify account playback, Google Maps behavior on a particular
phone, Bluetooth audio routing, SMS delivery, a phone call, or Subaru browser
compatibility. Keep those claims separate in test reports.

Primary API references: [Android media sessions](https://developer.android.com/reference/android/media/session/MediaSessionManager),
[Android intents](https://developer.android.com/guide/components/intents-common),
[Maps URLs](https://developers.google.com/maps/documentation/urls/get-started),
[Apple External Accessory](https://developer.apple.com/documentation/externalaccessory),
[Apple MessageUI](https://developer.apple.com/documentation/messageui).
