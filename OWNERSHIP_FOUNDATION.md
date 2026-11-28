# Shared vehicle ownership foundation

This branch provides a typed, versioned Rust model and calculation API plus the
native Android Garage. It includes atomic local storage, vehicle and history
forms, local reminders and explicit file export/import. See [the owner guide](docs/garage.md).
The native iOS Garage adapter is still planned.

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
  conflicting record chronology and unresolved broad severe flags stay explicit.
- Fuel full-to-full cycles including partial fills and missed-fill breaks.
- Fuel/service expenses counted once from their records, plus explicit other
  operating and financing expenses. No duplicate expense rows are generated.
- Reviewed backup merging by ID, explicit conflict choices, bounded JSON and schema
  validation. A repeated identical merge does not increment the revision.

Distance is stored as integer millimetres; a mile is exactly 1,609,344 mm. Fuel is
millilitres and money is integer cents with a vehicle currency. Civil dates use
calendar-month arithmetic, including month-end and leap-year handling.

## Reproducible tests

```sh
cargo +1.90.0 test --manifest-path rust/Cargo.toml --locked
```

50 ownership regressions cover unknown/approximate history, first and subsequent
coolant intervals, calendar and mileage thresholds, severe-condition gaps, owner
overrides, odometer correction cycles, a 10,000-record correction chain, GPS
confidence, trip reconciliation, ambiguous service ordering, partial fuel fills,
zero-distance consumption, idempotent imports and conflicting backups.
Six additional analytics regressions cover empty data, completion-time attribution,
exclusive period ends, corrected/approximate readings, financing, undated service
costs, wholly-contained fuel cycles, clock-boundary tolerance and read-only execution.

The JSON C function is `sb_ownership_execute`. Input is bounded at 4 MiB; callers
free its owned output with `sb_core_buffer_free`. It performs no I/O. Native
adapters must write only successful results atomically, serialize transactions,
preserve corrupt/unknown-version files for recovery and keep vehicle records out
of protocol diagnostic exports.

## Android persistence and workflows

`OwnershipStore` serializes load/validate/change/commit transactions in this
process. Each mutation reloads the committed file. `AtomicFile`, explicit file
sync and a post-commit read check protect against partial writes. Corrupt or
future-schema files are never replaced with an empty garage. The state is capped
at 1.5 MiB so two states fit within the Rust merge limit. It is a bounded first
implementation, rather than a second database beside shared state.

Garage component tests use isolated cache files, covering restart, interrupted
and failed saves, rejected writes, schema rejection, concurrent writers, import
conflicts and escaped exports. An optional fill-up odometer is one transaction;
failure rejects both records.

Service, fuel, trip and expense edits retain their IDs and rerun validation.
Odometer corrections append a reasoned replacement and preserve the original.
Changing currency after cost records exist is rejected, because amounts are
never silently relabeled or converted. Units can change without changing data.

Owner-defined maintenance is scoped to a vehicle and works without a bundled
manufacturer profile. It is labeled as an owner interval, supports service
history and cannot be attached to another vehicle's records. Configurable
due-soon thresholds and reminder snoozes are included in the shared state and
backups. Snoozing never changes a calculated maintenance status.
Raw import/state documents pass bounded strict JSON validation in Rust before
Android DOM parsing, preventing malformed or deeply nested files from exhausting
the platform parser. UTF-8 BOM input is accepted without weakening schema checks.

Service-book details support DIY, owner-recorded part numbers, warranty notes
and exact parts/labor/tax amounts. A supplied breakdown must equal the total;
overflow or a mismatch is rejected before persistence.

Android also exposes read-only yearly/monthly summaries and native distance/cost
charts. Local date/time changes refresh maintenance; drafts keep their original
zone and reject nonexistent/ambiguous times unless a valid explicit offset is supplied.

Next: native iOS persistence/UI using this API, attachments,
backup conflict review, advanced analytics, permission-based GPS and optional read-only
diagnostics. Android-only notification/RFCOMM capabilities are not implied for iOS.

## Android 0.58 ownership workspace

The native adapter now supports saved-record photos/PDFs, complete ZIP and
password-encrypted backups, reviewed conflict choices and tested atomic migration
from bare v1 state into a version-2 manifest. Driving conditions select verified
manufacturer exceptions; offline specifications retain section-level provenance.
Fuel cost/price summaries and labeled mileage forecasts use the shared engine.
The iOS adapter is deferred in this release. See [release verification](docs/ownership-058.md)
and [storage/encryption format](docs/ownership-backup-format.md).
