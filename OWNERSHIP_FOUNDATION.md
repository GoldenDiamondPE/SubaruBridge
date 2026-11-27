# Shared vehicle ownership foundation

This branch provides a typed, versioned Rust model and calculation API. It is
component tested. Native persistent storage, phone forms, local reminders and
backup file pickers are the next work; this is not yet a released tracker UI.

The existing Android transport remains separate. No vehicle connection, ECU
command, account, server or location permission is needed by this model.

## Data and calculations

- Vehicles, confirmed/approximate odometer readings and explained corrections.
- Completed trips added after the latest reading; a newer confirmed reading
  reconciles estimates. Trips crossing the reading timestamp remain excluded
  rather than split without evidence.
- A 2017 US Subaru Legacy 2.5 CVT maintenance profile with source metadata.
- Service dates and mileage ranges, including genuinely unknown dates and the
  example of a prior service somewhere within a mileage interval.
- Whichever applicable time/mileage threshold occurs first; unknown history,
  conflicting record chronology and unverified severe-service notes stay explicit.
- Fuel full-to-full cycles including partial fills and missed-fill breaks.
- Fuel/service expenses counted once from their records, plus explicit other
  operating and financing expenses. No duplicate expense rows are generated.
- Additive backup merging by ID, conflict rejection, bounded JSON and schema
  validation. A repeated identical merge does not increment the revision.

Distance is stored as integer millimetres; a mile is exactly 1,609,344 mm. Fuel is
millilitres and money is integer cents with a vehicle currency. Civil dates use
calendar-month arithmetic, including month-end and leap-year handling.

## Reproducible tests

```sh
cargo +1.90.0 test --manifest-path rust/Cargo.toml --locked
```

Ownership regressions cover unknown/approximate history, first and subsequent
coolant intervals, calendar and mileage thresholds, severe-condition gaps, owner
overrides, odometer correction cycles, a 10,000-record correction chain, GPS
confidence, trip reconciliation, ambiguous service ordering, partial fuel fills,
zero-distance consumption, idempotent imports and conflicting backups.

The JSON C function is `sb_ownership_execute`. Input is bounded at 4 MiB; callers
free its owned output with `sb_core_buffer_free`. It performs no I/O. Native
adapters must write only successful results atomically, serialize transactions,
preserve corrupt/unknown-version files for recovery and keep vehicle records out
of protocol diagnostic exports.

## Next Android slice

Add a Garage destination inside SubaruBridge, atomic app-private storage, manual
vehicle/odometer/service/fuel forms and an explicit export/import workflow. Test
failed writes, process restart and schema rejection before calling it durable.
Then expose the same model to native iOS; do not duplicate maintenance logic or
claim Android-only notification/RFCOMM access is available on iOS.
