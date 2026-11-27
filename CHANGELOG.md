# Changes

## 0.56-dev — Ownership summaries and time handling

- Add shared, read-only monthly/yearly aggregation and native trip-distance and
  operating-cost charts, with readable monthly details.
- Separate logged trips, confirmed odometer growth, fuel-cycle measurements,
  financing and undated services. Do not imply complete driving/cost coverage.
- Use native local calendar boundaries for timestamped records and preserve saved
  civil dates for services/expenses, including daylight-saving boundary tests.
- Refresh maintenance on return and system date/time changes. Keep a draft's
  original time zone and explicitly handle repeated or missing local hours.
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
