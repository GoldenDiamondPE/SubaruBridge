# Changes

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
