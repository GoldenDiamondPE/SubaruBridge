# Contributing

Start with the phone-only preview and native tests. Make one protocol change at
a time and add a targeted regression for a reproduced failure. Treat the saved
research observations, emulator, synthetic Bluetooth peer and actual head unit
as different levels of evidence.

Keep platform-independent framing and transport state in `rust/`. Android owns
permissions, foreground service, Bluetooth sockets, assets and result storage.
Use explicit local routes and bounded request bodies. Keep screen markup and
styling in `android/headunit-ui/` rather than in Bluetooth code.

Do not submit vendor APKs, firmware, extracted resources, decompiled SDK code,
credentials or raw captures. Use synthetic packets in tests. Scrub identifiers
from diagnostics before opening an issue. Public CI checks the source boundary.

Before a pull request:

```sh
python3 scripts/audit_public_source.py .
python3 scripts/audit_public_source.py . --inventory source-inventory.json
python3 scripts/audit_public_source.py . --verify-inventory
cd rust
cargo test
cd ../android
gradle assembleDebug assembleDebugAndroidTest
```

Describe the observable failure, change and verification. Do not claim a parked
Subaru result from a phone preview, packet ACK or successful RFCOMM connection.
