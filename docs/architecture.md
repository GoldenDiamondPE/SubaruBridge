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

An additional app-owned metadata journal is separately capped at 1 MiB. It stores
connection stages, byte counts, allowlisted route categories and response status,
with preview/Bluetooth source labels. It never records packet payloads, bootstrap
bodies, header values or device addresses. A full or unavailable metadata journal
is flagged in the exported report. These summaries cannot replace a raw HCI trace
when exact framing details are required.

The shared Rust C API returns owned byte buffers; the adapter must free each
once using the library function. Handles are registered rather than exposing raw
engine pointers. JNI copies bytes and releases buffers on the Android boundary.
The native iOS companion is developed separately. A small dependency-free Rust
AMS decoder also exposes a C API for the Android JNI adapter and future platform
adapters. It validates bounded entity updates without opening sockets or reading
device data itself.

## Vehicle ownership

The native Android Garage is independent of the Bluetooth service and HTML
router. `OwnershipStore` loads the sole versioned JSON state from the app's
no-backup private directory and sends a bounded command through JNI to
`sb_ownership_execute`. Rust validates the entire state, applies the command,
calculates summaries and returns the new state. Only successful changes are
committed atomically. App worker threads serialize file transactions; forms do
not perform disk or Rust work on the main thread.

No vehicle records enter HTTP routes or connection report journals. The owner
explicitly chooses backup/import destinations through Android's document picker.
Local notifications use a separate opt-in receiver and deferred daily inexact
alarm. They contain counts, rather than vehicle identifiers or private notes.
See [the shared model](../VEHICLE_DATABASE.md) and [ownership details](../OWNERSHIP_FOUNDATION.md).

## Media providers

The head-unit music page consumes a provider-neutral snapshot: source, opaque
session target, text metadata, timing, truncation state and offered commands.
Android MediaSession and the experimental iPhone AMS client supply that model.
Choosing iPhone media never falls back to an Android session. Action forms bind
their one-use grant to the displayed session. Replacing the selected player or
disconnecting invalidates the target.

The iPhone adapter uses a user-selected bonded device and serialized GATT
operations with connection/operation timeouts. Service changes clear the session
and require rediscovery by reconnecting. Commands are rechecked immediately
before sending and are never automatically retried. Stopping the car foreground
service closes the iPhone connection. Raw attributes, track titles and device
identifiers are excluded from the diagnostic journals.

This is a metadata/control connection, not an audio bridge or a general iOS
notification interface. Physical iPhone service exposure and car audio coexistence
remain separate verification tasks.
