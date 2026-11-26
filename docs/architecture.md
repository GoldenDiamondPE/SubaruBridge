---
layout: default
title: Architecture and limits
---

# Architecture and limits

```text
Paired head unit or synthetic peer
    Bluetooth Classic RFCOMM (Android)
    SLIP / IPv4 / TCP (Rust)
    Bounded HTTP framing (Rust + Java)
    Explicit local routes (Android)
    Original HTML/CSS dashboard + private result journal
```

Android's secure RFCOMM listener uses service UUID
`4213882b-44b9-45f4-ac4f-2c682bc800a7`. Only bonded peers are accepted.
The observed fixed link uses phone `192.168.1.1`, peer `192.168.1.2`, HTTP port 80.
These addresses live inside the SLIP stream, not on the phone's Wi-Fi network.
The preview server binds only `127.0.0.1:8009` and shares the same route handler.

The original Rust transport has a 4096-byte SLIP frame cap, 16 flows, bounded
receive/reorder buffers, fixed-length HTTP, checksums, segmentation, acknowledgments,
retries and a ten-second pending-handler timeout. It supports the observed
IPv4/HTTP path, not general IP routing or a complete operating-system TCP stack.
IPv6, IP fragmentation, TCP extensions and chunked request bodies are not supported.
More interoperability testing remains necessary.

Android owns Bluetooth permissions, a connected-device foreground service,
notification, socket I/O and a bounded active-session wake lock. The current
wake-lock timeout is 30 minutes; long-session power management still needs testing.
No root is needed for this transport. Rooted logging is separate and optional.

The local launcher handles the observed lookup, timestamp and absolute-form POST
without calling a retired cloud service. This is a new local application, not a
reimplementation of vendor identity or credential validation. Queries and bootstrap
bodies are not logged; event journals store only explicit diagnostic fields.
The private journal is capped at 1 MiB and reports storage failure rather than
claiming an unsaved result succeeded.

The shared Rust C API returns owned byte buffers; the adapter must free each
once using the library function. Handles are registered rather than exposing raw
engine pointers. JNI copies bytes and releases buffers on the Android boundary.
The iOS application is outside the current development scope.
