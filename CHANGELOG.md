# Changes

## 0.57-dev — Unified Android workspace

- Keep Garage, the original STARLINK dashboard, integrations and diagnostic tools
  in the existing app ID; add Home and Tools entries for Record & diagnose.
- Replace laptop-armed test recording with an opt-in, no-root foreground recorder,
  passive USB/phone sampling, event markers, private notes and saved timelines.
- Bound sessions to ten minutes/one MiB and twelve retained runs; preserve partial
  runs as interrupted, support explicit deletion and include sessions in ZIP export.
- Add phone-only self-checks and a developer view with permission/USB/resource
  state, system settings links and older-listener detection.
- Rotate bounded diagnostic journals instead of silently dropping new events when
  full. Keep the latest prior journal and propagate save/export failures.
- Use one shared private-report exporter and an original blue bridge-and-road
  adaptive icon, round icon and notification symbol, with Android themed-icon support.
- Preserve old private data and experimental protocol source. No vendor runtime,
  proprietary artwork or extracted application is included. iOS remains paused.
- Guard revoked Bluetooth permissions, explicitly enable native gesture-back
  callbacks, validate reminder broadcast actions and avoid timer catch-up bursts.
  Exclude local app data from automatic Android cloud/device transfer.

## 0.56-dev — Ownership summaries and time handling

- Add shared, read-only monthly/yearly aggregation and native trip-distance and
  operating-cost charts, with readable monthly details.
- Separate logged trips, confirmed odometer growth, fuel-cycle measurements,
  financing and undated services. Do not imply complete driving/cost coverage.
- Use native local calendar boundaries for timestamped records and preserve saved
  civil dates for services/expenses, including daylight-saving boundary tests.
- Refresh maintenance on return and system date/time changes. Keep a draft's
  original time zone and explicitly handle repeated or missing local hours.
- Guard portable revision counters and validate bounded strict JSON before native
  document parsing; keep malformed, oversized or future-schema input intact.
- Preserve recorded cents when the phone's locale uses a different currency format.
- Match Garage navigation and chart controls to the shared palette, and keep
  scrolling content clear of the phone's status and navigation bars.
- Add a public design demo using real synthetic-emulator screenshots and original
  head-unit templates, with three themes, two layouts and browser-only actions.
- Keep the existing Android media, STARLINK fallback and basic iOS source intact.

## 0.55-dev — Android Garage

- Integrate the shared ownership foundation with the working Android media and
  native basic iOS source, preserving the earlier development branches.
- Add native Android vehicle profiles, mileage/correction history, maintenance
  explanations and source references, services, fuel, expenses and manual trips.
- Persist one bounded state atomically; preserve invalid/future-version files.
- Add record edits, owner interval restoration, currency relabel protection and
  atomic optional odometer recording with fill-ups.
- Add opt-in local maintenance reminders and explicit JSON/CSV/offline HTML exports.
- Add vehicle-scoped owner maintenance, reminder lead times/snoozes and service
  book parts/labor/tax, DIY, part numbers, warranty notes and history search.
- Add isolated storage, corruption, failed/interrupted write, backup, concurrent
  mutation, export-escaping, reminder-filtering and native-form tests.

The independent STARLINK physical-car test, iPhone/Taurus service exposure,
automatic GPS, live vehicle diagnostics, critical technical specifications and
native iOS Garage UI remain outstanding. Component/emulator results do not certify
head-unit compatibility or manufacturer maintenance exceptions.
