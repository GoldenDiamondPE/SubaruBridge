---
layout: default
title: Android 0.58 ownership release
---

# Android 0.58 ownership release

One standalone Android SubaruBridge installation now includes private receipts,
complete backups, optional password encryption, native restore conflict review,
verified condition-specific maintenance, offline specifications and fuller
fuel/cost/mileage summaries. Vehicle connectivity, a PC and an account are not
needed for ownership features. The iOS build is deliberately unchanged.

## Use the additions

1. Open Home → Garage. Review the vehicle's driving conditions in its profile.
2. More → Fluids, parts & specifications opens the offline manufacturer reference.
3. Open a saved vehicle, service, fill-up, expense or trip → Receipts & photos.
4. More → Backups & receipt data creates complete encrypted/ZIP backups and opens
   restore review. The original records-only JSON export remains available.
5. More → Ownership summaries switches distance/cost/fuel charts and shows fuel
   purchases, measured cycle costs and confirmed-growth forecasts.
6. More → Maintenance reminders shows last/next check and Android permission or
   storage failures. Send test notification checks delivery without inventing a
   due maintenance item.

See [the full Garage guide](garage.md), [manufacturer provenance](../MAINTENANCE_DATA.md)
and [backup format](ownership-backup-format.md).

## Verification and limits

Physical Pixel 6 tests cover native forms, private storage, interrupted/rejected
writes, v1 migration, future-schema preservation, PDF/photo receipts, complete
backup round-trip, stale/conflicting restore, wrong passwords, tampering,
truncation and ZIP traversal rejection. Tests use isolated synthetic garages;
owner records are not seeded with demonstration data. The original garage was
archived privately before upgrading the same app ID.

All 82 shared Rust tests passed on the Pixel: 50 ownership, six analytics,
18 protocol and eight media-decoder tests. Build and lint passed. All Android instrumented suites, including the native
encrypted restore journey and integration fixtures, passed. All 61 HTTP
regressions passed against the 0.58 build. The encrypted export
also authenticated and decrypted in an independent Python implementation using
a Unicode password, confirming the documented portable byte format. Native screenshot
fixtures exercise the actual Android UI, not mock web renderings.

An actual local notification posted on the Pixel. In forced deep idle, the real
private receiver checked records, reinstalled its daily target and posted a
clearly labelled test notification while the device remained in IDLE. After a
physical reboot and normal unlock, the boot receiver restored the daily alarm
and a test notification posted. Original reminder preferences were restored.
This accelerated check does not establish overnight inexact-alarm delivery;
Android power management may defer alarms. Longer daily-use observation remains.

Source-specific severe rules and fluid/capacity/torque/plug/tire references are
verified for the selected 2017 US Legacy 2.5 CVT configuration. Exact VIN-specific
filter pairings, aftermarket fitments and CVT service fluid are explicitly
unverified. Published system capacities are not drain-and-fill instructions.
Other model years/markets need separate source review.

The new backup format is documented for a future iOS adapter. This Android
release does not claim iOS feature parity or a new car connection result.
