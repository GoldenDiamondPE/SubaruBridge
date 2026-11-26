---
layout: default
title: Build your own Subaru workspace
---

# Your phone. Your Subaru. Your code.

SubaruBridge is an original, device-local Android app for experimenting with
legacy STARLINK infotainment connections. Make your own dashboard, learn the
transport, and build small integrations from readable source.

**Experimental:** the independent transport is being tested. The new app still
needs a parked Subaru display, touch and reconnect result. This is not a released
replacement for all original STARLINK features.

## Start building

1. [Build the standalone Android app]({{ '/build.html' | relative_url }}).
2. [Test it on your phone, emulator and parked head unit]({{ '/testing.html' | relative_url }}).
3. [Change the layout or add your own local screen]({{ '/customization.html' | relative_url }}).

The included dashboard uses HTML links and forms with JavaScript off. Themes,
icons, layouts and screens are editable. Its local clock, battery view and result
journal need no cloud service or paid API.

## Independent by design

The Android app owns its Bluetooth socket and foreground service. A reusable Rust
core owns bounded SLIP/IP/TCP state. Our own local server renders our own pages.
The original STARLINK app is not a runtime or build dependency.

The [public repository](https://github.com/GoldenDiamondPE/SubaruBridge) contains
source and build directions. It contains no vendor APKs, decompiled source,
firmware, signing keys, extracted artwork or private captures. Read the
[architecture and current limits]({{ '/architecture.html' | relative_url }})
before planning an integration. Vehicle firmware and driving restrictions stay intact.
