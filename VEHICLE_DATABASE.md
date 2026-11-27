# Vehicle data model

The canonical model is `rust/src/ownership/model.rs`, with schema version 1.
Android persists that model directly. There is no second ownership database.
The maintenance database is versioned factual data in `vehicle-database/`; its
provenance and limitations are recorded in [MAINTENANCE_DATA.md](MAINTENANCE_DATA.md).

| Collection | Meaning |
| --- | --- |
| `vehicles` | Identity, optional local identifiers/purchase data, units/currency, schedule selection and archive status |
| `readings` | Observation timestamp, source, confirmed/approximate distance, optional superseded ID and reason |
| `services` | One service visit, item IDs, optional civil date and distance range, provider, cost and notes |
| `fuel` | Fill timestamp/odometer, purchased volume/cost, full/partial and missed-fill flags |
| `expenses` | Civil date, category, cost and separate financing flag |
| `trips` | Completed non-overlapping time range, source, distance and notes |
| `overrides` | Explicit owner distance/time intervals or disabled maintenance items |

Distances are unsigned integer millimetres. Fuel uses integer millilitres.
`costCents` means hundredths of the vehicle's supported two-decimal currency.
The first native UI offers USD, CAD, EUR, GBP, AUD and NZD. Financial amounts
never use binary floating-point arithmetic. Times are Unix milliseconds; civil
service dates are strict ISO `YYYY-MM-DD`. Calendar intervals use month arithmetic.
IDs are stable opaque strings, unique across history records. Revision increments
only for a validated state change. Unsupported schemas are rejected.

The C API `sb_ownership_execute` accepts a JSON object containing `state`,
`today`, `nowMs`, `command` and optional due-soon distance/day thresholds. The
success object contains `ok`, `changed`, `state`, `summaries`, `schedule` and
`capabilities`; a validation failure contains `ok:false,error` without a new state.
Input is capped at 4 MiB. Owned buffers must be freed exactly once using
`sb_core_buffer_free`.

Commands are `view`, `addVehicle`, `updateVehicle`, `addReading`, `addService`,
`updateService`, `addFuel`, `updateFuel`, `addExpense`, `updateExpense`, `addTrip`,
`updateTrip`, `setOverride`, `resetOverride`, `recordFillUp` and `mergeBackup`.
Variant and field names are camelCase. `recordFillUp` takes `entry` and a matching
confirmed manual `reading`; both are validated in one revision. Record edits may
not move a history record between vehicles. Currency changes with saved costs
are rejected. Odometer correction uses a new reading with `supersedes` and notes.

Android's backup envelope has `format:"org.subarubridge.garage"`, `schemaVersion:1`,
`exportedAt` (ISO timestamp) and `garage` (the canonical state). Restore is additive
and rejects conflicting IDs. Import must commit the whole successful result or
none of it. Native iOS should implement the same rules around the C API instead
of reimplementing calculations in Swift. Attachments, user-defined schedule items,
telemetry samples and future schema migrations are not part of this schema yet.
