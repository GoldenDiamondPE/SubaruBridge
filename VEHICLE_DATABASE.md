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
| `customItems` | Vehicle-scoped owner-defined maintenance, without a manufacturer interval claim |
| `preferences` | Shared due-soon distance/day thresholds |
| `snoozes` | Per-vehicle/item reminder dates; calculated due status is preserved |

Distances are unsigned integer millimetres. Fuel uses integer millilitres.
`costCents` means hundredths of the vehicle's supported two-decimal currency.
The first native UI offers USD, CAD, EUR, GBP, AUD and NZD. Financial amounts
never use binary floating-point arithmetic. Times are Unix milliseconds; civil
service dates are strict ISO `YYYY-MM-DD`. Calendar intervals use month arithmetic.
IDs are stable opaque strings, unique across history records. Revision increments
only for a validated state change. Unsupported schemas are rejected.
Revision counters are capped at the largest exactly representable JSON integer
(`2^53 - 1`); advancing past that limit is rejected without changing the data.

The C API `sb_ownership_execute` accepts a JSON object containing `state`,
`today`, `nowMs`, `command` and optional due-soon distance/day thresholds. The
success object contains `ok`, `changed`, `state`, `summaries`, `schedule` and
`capabilities`; a validation failure contains `ok:false,error` without a new state.
Input is capped at 4 MiB. Owned buffers must be freed exactly once using
`sb_core_buffer_free`.
`sb_ownership_json_valid` validates strict UTF-8 JSON with bounded nesting before
a platform DOM parser. It accepts an optional UTF-8 BOM and returns 1/0 without
allocating a caller-owned buffer. Typed schema validation still runs afterward.

Commands are `view`, `addVehicle`, `updateVehicle`, `addReading`, `addService`,
`updateService`, `addFuel`, `updateFuel`, `addExpense`, `updateExpense`, `addTrip`,
`updateTrip`, `setOverride`, `resetOverride`, `recordFillUp`, `mergeBackup`,
`addCustomItem`, `updateCustomItem`, `setPreferences`, `snoozeReminder`, `clearSnooze`
and read-only `analyze`.
Variant and field names are camelCase. `recordFillUp` takes `entry` and a matching
confirmed manual `reading`; both are validated in one revision. Record edits may
not move a history record between vehicles. Currency changes with saved costs
are rejected. Odometer correction uses a new reading with `supersedes` and notes.

Android's backup envelope has `format:"org.subarubridge.garage"`, `schemaVersion:1`,
`exportedAt` (ISO timestamp) and `garage` (the canonical state). Restore is additive
and rejects conflicting IDs. Import must commit the whole successful result or
none of it. Native iOS should implement the same rules around the C API instead
of reimplementing calculations in Swift. Attachments,
telemetry samples and future schema migrations are not part of this schema yet.

Service records also support optional `costBreakdown` (`partsCents`, `laborCents`,
`taxCents`), `diy`, `partNumbers` and `warranty`. The breakdown must equal the
single canonical `costCents` total. Optional additions default safely when reading
earlier v1 records. Old clients that do not recognize a field must preserve the
file and report an upgrade requirement, rather than discarding unknown data.

Backup restoration into an empty garage adopts its reminder preferences. A
non-empty garage rejects conflicting preferences, snoozes, intervals or record
IDs instead of silently choosing a winner. Custom items must be included with
the service records that refer to them.

`analyze` accepts 1–24 `periods`, each containing `id`, `label`, `startMs`, `endMs`,
`startDate` and `endDate`. Native code supplies actual local calendar boundaries,
including offset changes. Ends are exclusive. Each vehicle summary returns an
`analytics` array with trip counts/distance/duration, confirmed-reading growth and
coverage, fuel cycles wholly within the period, dated costs and unassigned services.
Analysis never changes the garage revision. Trip distance is attributed by trip
completion; undated services remain in lifetime totals and are not assigned a period.
