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

Normal and applicable severe-service thresholds use whichever time or mileage
threshold comes first. Choose specific driving conditions in the vehicle profile;
the verified 2017 booklet changes only the matching components. Instructions to
inspect more often without a numerical interval remain notes. An older broad
severe flag stays unresolved until specific conditions or an owner interval are
chosen. **More → Fluids, parts & specifications** opens the offline reference.
Quantities distinguish oil/filter service fill from published system capacities.
Unverified VIN-specific filter pairings and CVT service fluid remain labelled.
See [manufacturer provenance](../MAINTENANCE_DATA.md).

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
operating-cost or measured fuel-economy bars. Switch chart type or year and choose **Read monthly details**
for counts, distance, duration, average/longest trip, dated costs, financing and
fuel measurements. The information is calculated locally without changing history.

Logged trip distance is grouped by completion date. Confirmed odometer growth
covers the first and last confirmed readings within the period; it is not
presented as complete monthly/yearly mileage. Undated service costs stay in
lifetime totals and are not assigned a guessed month. Fuel economy requires
both full fills within the selected period; partial fills between them count.
Unrecorded driving and purchases are not invented. Summaries also show fill-up
counts, purchased volume/cost, weighted average fuel price and measured cycle
fuel cost. The operating-costs-per-logged-distance ratio is explicitly labelled
as partial recorded coverage. It is not a measured whole-vehicle cost per mile.
Recent confirmed readings can support a labelled annual mileage estimate and
an estimated date for reaching a service mileage threshold. Calendar service
may be earlier, and future driving may differ.

Maintenance refreshes when you return to the Garage and when the phone's date
or time changes. Timestamp forms retain their shown time zone while you edit.
Default timestamps include an offset so a repeated daylight-saving hour can be
identified precisely. A nonexistent local time is rejected instead of shifted.

## Receipts and photos

Save a vehicle, service, fuel entry, expense or trip, then open its saved record
and choose **Receipts & photos**. The system picker accepts JPEG, PNG, WebP and
unlocked PDF files. A private copy is saved immediately; no broad storage or
camera permission is needed. Limits are 16 MiB per file, 256 attachments and
256 MiB total, with PDFs up to 500 pages. View privately, save a copy or remove
an attachment without deleting its record. Original photo metadata is retained.

## Backups and restore

Open **More → Backups & receipt data**; the same entry appears before adding a
vehicle. Encrypted .sbbk and unencrypted ZIP backups include all ownership records
and receipt files. Records-only JSON keeps the original format without receipts.
Encrypted export requires a confirmed 10–256 character password. Keep it safely:
the app cannot recover a forgotten password.

**Restore or merge a backup** authenticates encrypted files first, then opens a
review of new/matching records and receipts. Review each conflict, compare both
versions and explicitly keep local or use the backup where permitted. Odometer
ID conflicts retain local history; use the explained correction workflow instead.
Records cannot move between vehicles, existing costs cannot change currency and
receipt-ID conflicts keep the original local receipt.

Choose **Import reviewed backup**, then confirm. Files and the final record graph
validate together. Cancellation, wrong passwords, damaged files, future versions,
invalid choices and stale reviews preserve the garage. A garage changed after
review needs another review. Reimporting identical records does not duplicate them.
No PC, account or internet service is needed.

CSV history and printable HTML service reports remain available for the selected
vehicle. They preserve missing/approximate information and do not embed receipts.
Choosing a cloud document provider may upload an explicitly exported file. All
ownership data is excluded from connection diagnostic reports. Keep a complete
backup before clearing app data or uninstalling.

## Local reminders

**More → Maintenance reminders** shows the last check, next scheduled daily check
and permission/storage problems. Reminders are opt-in and need Android and
channel permission. **Send test notification** checks local delivery without
inventing a due service. Notifications show counts without vehicle names or notes.

Daily checks target 9:00 a.m. local time, with schedule recovery after unlocked
boot, app update or clock/time-zone change. Reopening Garage does not postpone
the target. Saved changes also prompt checks. Android can defer inexact alarms
during idle; scheduled time is not a delivery guarantee. Snoozed/archived items
and unknown history are excluded. Mileage reminders depend on new saved readings
or trips. See [release verification](ownership-058.md) for actual test evidence.

The tested storage migration wraps the existing v1 garage without replacing its
business records. Unsupported future schemas and unreadable files remain intact.
See [backup and storage details](ownership-backup-format.md).

## See the current screens

The [design demo](demo.html) includes real Android 0.56 screenshots from a
synthetic emulator vehicle. The mileage, service bill and trips shown there are
examples, not the owner's private records. The head-unit preview uses the original
app templates and does not access a vehicle, phone, notification feed or account.

See [backup and storage details](ownership-backup-format.md).
