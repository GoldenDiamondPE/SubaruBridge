# Maintenance data provenance

The bundled profile applies only to the **US 2017 Subaru Legacy 2.5 CVT**.
Other vehicles can use owner-defined maintenance with the manufacturer profile
disabled. Verification date: **2026-11-27**.

## Manufacturer sources

- [2017 US Warranty & Maintenance Booklet](https://cdn.subarunet.com/stis/doc/warrantyBooklet/2017_war_and_maint_112216.pdf),
  printed pages 29–31. SHA-256:
  `6f0e44f69e0da2ed87b515d01fd049d98926cdf14f14dc5573bd3c810178f866`.
- [Subaru vehicle resources for 2017 Legacy 2.5i Premium](https://www.subaru.com/owners/vehicle-resources.html?model=2017-LEG-HAD),
  **2017MY Legacy and Outback Owner's Manual**, document
  `MSA5M1703A_B_STIS_Index.pdf`, sections 11-19 and 12-4 through 12-9,
  plus the gas-station reference. Official delivered PDF SHA-256:
  `b93d7dfb30ab96ddd8703433af27bb93a990490e8e9599da764ec4c74ed28133`.
  Temporary signed download URLs are not embedded in the app or repository.
- [Subaru bulletin 02-191-22R](https://static.nhtsa.gov/odi/tsbs/2024/MC-11008267-0001.pdf),
  revised 2024-09-17, page 1, for general oil-filter part references. This bulletin
  does not verify a filter's fitment to the owner's exact VIN.

The original schedule and specification pages were visually inspected. Public
source contains factual summaries and references, not OEM PDFs, scans, artwork
or copied service procedures. Manufacturer documents are not relicensed under
the project's GPL. Recheck current service information before performing work.

The earlier [official one-page attachment](https://contactus.subaru.com/_entity/annotation/5a65d58d-ed51-ec11-8c62-0022482a2d61)
was cross-checked against booklet page 30. Its SHA-256 is
`b408b8a024f6a1d9ff7a12ad3ee117fe298c17fadb0e242e68d4e3556a3e5830`.
The support article's conflicting prose is not used for calculations.
Mislabelled third-party downloads containing newer booklets were rejected.

## Severe-service applicability

Page 31 links each driving example to particular schedule rows. Conditions are
selected individually, without applying one interval to every item.

| Condition and applicable item | Verified exception |
| --- | --- |
| Short trips, extreme cold or repeated towing: oil/filter | 3,000 miles or 3 months |
| Salt/corrosive roads or coastal use: fuel-system inspection | 6,000 miles or 6 months |
| Repeated towing: CVT, where applicable | Replacement every 24,855 miles; no time interval added |
| Repeated towing: front/rear differential fluid | Replacement every 15,000 miles or 15 months |
| Rough/muddy roads, salt or towing: brake lines/operation | Inspection every 6,000 miles or 6 months |
| High humidity or mountainous use: brake fluid | Replacement every 15,000 miles or 15 months |
| Extreme cold, salt or towing: brake wear/boots/joints | Inspection every 6,000 miles or 6 months |
| Extreme cold or towing: steering/suspension | Inspection every 6,000 miles or 6 months |

Dust-related air-cleaner inspection and extreme-temperature fuel-filter
replacement have no shortened numerical interval. Their notes remain visible
without invented numbers. Row 21's reference to note 8 retains its wording
ambiguity. Rough-road/salt/towing examples list brake fluid, but note 5 supplies
numbers specifically for humidity/mountain use. Note 10's coolant-conditioner
instruction is retained.

An older broad `severe:true` flag without specific conditions remains unresolved
until the owner chooses conditions or an explicit interval. Owner overrides
retain priority. Initial coolant service uses the initial factory baseline;
later replacements use the subsequent interval. Unknown service history stays
unknown; approximate mileage remains a range.

## Specifications and parts

The versioned reference file includes original oil requirements, oil/filter
service fill, published coolant/CVT capacities, differential grades/quantities,
brake-fluid standard, spark-plug type, battery type, factory tire configuration
and pressures, wheel-nut torque and fuel. Each item records a section and notes.

Published CVT/coolant capacities are **not** drain-and-fill amounts. The manual
refers CVT fluid selection to Subaru; no service fluid is guessed. The spark-plug
type is verified for the 2.5 L engine. Air/cabin-filter order numbers, exact
VIN-specific oil-filter pairing, aftermarket equivalents and CVT service fluid
remain explicitly unverified. Owner-recorded parts are stored with services and
receipts without becoming manufacturer compatibility claims.
