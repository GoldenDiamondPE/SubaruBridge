---
layout: default
title: Garage and vehicle ownership
---

# Garage

Open **SubaruBridge → Home → Garage**. The Garage works offline, without the car,
Bluetooth, an account or a location permission. The initial vehicle form is set
up for a US 2017 Subaru Legacy 2.5 CVT. Review it before saving. Open **Vehicle
details, units & maintenance** for trim, engine, currency, VIN, purchase details,
severe conditions and archiving. Other profiles can use ownership records with
the bundled manufacturer schedule turned off.

## Start with what you know

1. Add your vehicle. Optional identifiers stay local.
2. Choose **Log odometer**, enter the reading and when it was observed. Mark an
   approximate reading explicitly. No value is filled in from research examples.
3. Choose **Log service** and select the completed work. Add an exact date and
   mileage if known. For a service somewhere between 70,000 and 74,000 miles,
   enter those lower and upper bounds. Leave the date blank if it is unknown.
4. Open **Maintenance** and tap an item. It shows last service, the next distance
   range/date, remaining distance/days, calculation explanation and source row.
   Unknown history remains unknown. A known elapsed threshold can establish due
   status even when another part of the record is incomplete.
5. Open **Interval & source** to set an explicit owner interval, disable an item,
   restore the manufacturer interval or open the source document.

Use **Add maintenance item** for batteries, wipers, tire changes, registration,
inspections, PCV service or other work. Enter the action and a distance and/or
time interval you choose. It is labeled as owner-defined, never as a verified
manufacturer requirement. Custom items work on vehicles without a bundled
manufacturer profile. Edit the definition through its interval screen; saved
service history remains attached to the same item.

**Reminder lead times** changes the Due soon window for all garage vehicles.
It does not change the actual service interval. An item's interval screen also
offers 7/30-day reminder snoozes and clearing the snooze. The calculated due
status remains visible while its notification is snoozed.

Normal-service thresholds use whichever applicable time or mileage threshold
comes first. Manufacturer severe-service footnotes have not yet been verified.
Choosing severe conditions does not invent a shorter interval. Critical fluid
specifications, capacities, part numbers and torque values are not supplied yet.
See [maintenance provenance](../MAINTENANCE_DATA.md).

## History, fuel, trips and costs

**History** filters odometer, service, fuel, expense and trip records. Tap a
record to review/edit it. Odometer corrections require a reason and append a
replacement; original readings remain visible. Other edits retain the record ID
and recalculate the same history, rather than adding another paid service.
History search includes service items, part numbers, providers and notes.

In **Log service**, open **Parts, labor & warranty** for DIY status, part numbers,
warranty notes and an optional parts/labor/tax breakdown. Components must equal
the total. Leave Total paid blank to calculate it from those components. Part
numbers are your records; the app does not certify their compatibility.

For fuel, enter the fill time, odometer, purchased volume, total cost and full or
partial status. Partial fills are included in the next full-to-full measurement.
Mark a missed fill so the app does not claim an economy measurement across a
gap. You can also save a confirmed odometer from a new fill-up; both records are
validated and saved together. A saved odometer is independent history: editing
the fuel entry later does not silently rewrite the odometer audit trail.

**Log completed trip** records a manual start/end time and distance. Completed
trips after the latest reading contribute to estimated mileage. A newer
confirmed odometer reconciles previous estimates. Overlapping trips are rejected;
trips crossing a reading timestamp are excluded with an explanation. GPS routes,
automatic recording and live vehicle telemetry are not implemented in this build.

Fuel and service costs contribute automatically. Log other purchases under
**Log expense**; do not enter the same fuel/service bill twice. Financing is
separate from operating expenses. **More** shows the recorded breakdown. Purchase
price stays on the profile and is excluded from these recorded operating totals.
Currency is locked after cost records are saved; no exchange conversion is guessed.
US miles/gallons and metric kilometres/litres share canonical stored values.

## Ownership summaries

Open **More → Ownership summaries** for a year of recorded trip-distance or
operating-cost bars. Switch chart type or year and choose **Read monthly details**
for counts, distance, duration, average/longest trip, dated costs, financing and
fuel measurements. The information is calculated locally without changing history.

Logged trip distance is grouped by completion date. Confirmed odometer growth
covers the first and last confirmed readings within the period; it is not
presented as complete monthly/yearly mileage. Undated service costs stay in
lifetime totals and are not assigned a guessed month. Fuel economy requires
both full fills within the selected period; partial fills between them count.
Unrecorded driving and purchases are not invented.

Maintenance refreshes when you return to the Garage and when the phone's date
or time changes. Timestamp forms retain their shown time zone while you edit.
Default timestamps include an offset so a repeated daylight-saving hour can be
identified precisely. A nonexistent local time is rejected instead of shifted.

## Reminders and data

**More → Maintenance reminders** enables local reminders. Allow Android
notifications when asked. The app checks saved changes and requests a daily
inexact alarm; Android power management may defer delivery. No exact-alarm
permission, server or push account is required. Unknown history and archived
vehicles do not create overdue alerts. Notifications contain counts, without VINs,
plates, private notes or message contents. Immediate notification delivery and
alarm restoration after reboot passed on the Android emulator. Extended idle
delivery on physical phones still needs device-specific verification.

**Export garage backup** opens Android's file picker. It exports all vehicles
and ownership records as versioned JSON, including custom maintenance, lead times
and snoozes. It excludes app themes, connection captures, media credentials,
Android reminder-enable permission and selection preferences. Backups are
unencrypted: the destination is your choice, and choosing a cloud file provider
may upload that file. Keep a backup before uninstalling or clearing app data.

**Import garage backup** adds new IDs. Identical records are not duplicated.
Conflicting records/settings, unknown schemas and invalid data cancel the entire import.
This first version does not replace a conflicting local record automatically.

**Export history CSV** exports the selected vehicle with explicit canonical units
and a currency column. Formula-like text is escaped for spreadsheets. **Export
service report** creates offline HTML suitable for opening in a browser and
printing. It retains unknown dates and approximate mileage, and contains no
external scripts or images. Neither export is a mechanic's vehicle-condition
certificate.

The first private storage adapter caps garage state at 1.5 MiB and 10,000 total
history records. A failed or oversized save preserves the previous committed
file. Future-schema or unreadable files are not reset to an empty garage.
Attachments, encrypted backups, richer analytics and
the native iOS Garage interface are subsequent work.

## See the current screens

The [design demo](demo.html) includes real Android 0.56 screenshots from a
synthetic emulator vehicle. The mileage, service bill and trips shown there are
examples, not the owner's private records. The head-unit preview uses the original
app templates and does not access a vehicle, phone, notification feed or account.
