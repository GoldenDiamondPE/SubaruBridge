# Maintenance data provenance

The first profile is **US-market 2017 Subaru Legacy 2.5 CVT**. It is not applied
to another year, engine, transmission or market automatically.

Source: [Subaru's maintenance schedule attachment](https://contactus.subaru.com/_entity/annotation/5a65d58d-ed51-ec11-8c62-0022482a2d61),
linked from [Subaru's 2017 maintenance article](https://contactus.subaru.com/knowledgebase/article/1001834/en-us).
The one-page table was visually inspected. Its PDF SHA-256 is
`b408b8a024f6a1d9ff7a12ad3ee117fe298c17fadb0e242e68d4e3556a3e5830`.
The original PDF remains outside the public repository.

The structured file records source row/page, URL, hash and verification date.
The article's prose conflicts with its attached table, so it is not used to
invent replacement intervals. The table refers to additional booklet notes;
those severe-condition exceptions are not yet verified and are not filled in.
Selecting severe conditions without a verified rule produces an unknown status
unless the owner explicitly supplies an interval.

The model distinguishes inspection from replacement and the initial coolant
interval from later replacements. Service history is never invented from a
vehicle's age or odometer. Unknown history does not mean confirmed overdue.
An approximate range is retained rather than replaced by its midpoint.

Critical fluid types/capacities, torque values, parts compatibility, additional
markets and custom maintenance schedules still need their own authoritative
source review. No such specifications are guessed by this foundation.
