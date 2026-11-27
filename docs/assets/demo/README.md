# Original demo images

These four images were captured with Android CLI from the original SubaruBridge
Android 0.56 development app on an Android x86-64 emulator on 2026-11-27.
They were visually reviewed before publication. Their exact bytes are allowlisted
by SHA-256 in `scripts/audit_public_source.py`; other screenshots remain excluded.

- `home.png`: native phone home screen.
- `garage.png`: native Garage overview.
- `maintenance.png`: sourced maintenance list and explicit unknown-history labels.
- `analytics.png`: native recorded-distance chart and ownership summary.

The demo vehicle, 81,000-mile reading, 10-mile trip and $74.50 two-item service
visit were entered as synthetic emulator examples. They are not the owner's
vehicle records. No VIN, license plate, precise location, personal notification,
device identifier, account, credential, third-party album artwork or vendor-app
screen is present. The visible system status/navigation bars belong to the
Android emulator, not a Subaru application.

The head-unit preview is rendered from the project's original HTML/CSS/SVG source
via `scripts/build_web_demo.py`. It contains no captured proprietary STARLINK
interface, firmware graphics or extracted application resources.

To update these images, capture original screens with synthetic data, inspect
every image, update this provenance note and review the audit's pinned hashes.
Never broaden the check to allow arbitrary screenshots or image folders.
