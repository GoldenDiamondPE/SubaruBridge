# Feature status

The original Android transport and old private research-adapter car result are
different implementations. Neither a simulator pass nor a delivered media command
is evidence that the independent app works on the Subaru.

| Area | Current implementation | Verification still needed |
| --- | --- | --- |
| STARLINK | Independent Android RFCOMM / Rust SLIP-IP-TCP-HTTP, original script-free UI, touch/result routes, private export | Independent transport on the target physical head unit, touch and reconnect |
| Android connection recovery | Service-owned listener/session retries, Bluetooth off/on recovery, persistent Pause, bounded renewable session power and independent loopback fallback; Android 17 emulator lifecycle checks | Actual head-unit reconnect and long-drive observation |
| Unified Android workspace | One existing app ID for Garage, dashboard, integrations, head-unit checks, phone self-checks, original icon and developer mode | Earlier experimental MirrorLink/AirPlay/CarPlay receiver paths remain separate; iOS has native unified navigation; direct accessory transport remains separate |
| On-device diagnostic recorder | No-root foreground recording, private notes/markers, event timelines, explicit ZIP export; 10-minute/1-MiB sessions, 12-session retention, interrupted-run recovery | Metadata only; exact Bluetooth/USB packets need privileged external capture. Android scheduling and power behavior vary |
| Passive USB lab | USB state/accessory and interface counts, phone resource/permission samples, system settings links | Does not change USB functions or establish MirrorLink registration/certification |
| Android music | Generic MediaSession provider selection, advertised controls, toggle-only support, session-bound forms, metadata/timing | Real music apps and audio routing; streaming-provider capabilities vary |
| iPhone / Taurus music | Experimental Android AMS client; shared Rust decoder; separate source choice | Real iPhone pairing, service exposure, Taurus metadata/controls and car coexistence |
| iPhone companion | Native unified Home/Garage/Dashboard/Tools, shared Rust Garage, script-free preview, local metadata recorder, phone apps and reminders | Signed-phone validation, direct car transport and a general phone-to-phone channel |
| Maps | Android notification directions; native phone URLs | Real navigation and head-unit rendering; no embedded live maps |
| Calls / messages | Android dialer/composer, confirmed notification replies; native iOS prompts/composer | No arbitrary inbox read or call-audio interception; normal factory hands-free testing |
| Notifications | Android opt-in bounded memory feed; iOS app-owned local notifications | iPhone-wide relay requires separate authorized accessory research |
| Ownership | Native Android and iOS Garage; private atomic storage, confirmed/approximate readings, correction trail and shared versioned Rust calculations | Physical iPhone validation; future business-schema migrations |
| Maintenance | 18 sourced rules with item-specific severe exceptions; offline fluids/capacity/torque/plug/tire references; uncertain history and owner intervals | Other vehicles/markets, exact VIN-specific filter fitments and CVT service fluid |
| Service book | Multi-item visits, dates/ranges, DIY/provider, cost breakdown, part/warranty notes, private photos/PDFs, search and printable history | Richer verified VIN-specific parts database |
| Maintenance reminders | Opt-in daily Android inexact alarm and local notification; configurable lead times and snoozes; saved changes trigger checks; unknown history and archived vehicles excluded | Physical notification and accelerated deep-idle receiver checks passed; reboot verification recorded in release testing; overnight delivery remains observational; iOS local calendar reminders implemented; physical delivery remains to test |
| Trips / fuel / expenses | Completed manual trips, reconciled estimates, full/partial/missed fills and derived operating/financing totals; editable records | Automatic GPS, routes, richer analytics and safeguards for auto recording |
| Ownership summaries | Shared monthly/yearly distance/cost/fuel charts, purchased-fuel and measured-cycle cost, recent confirmed-growth estimates; partial coverage remains explicit | Configurable widgets, cross-period comparisons and complete-cost coverage |
| Data ownership | Complete ZIP/optional encrypted SBBK backups with receipts, native conflict review, tested v1 storage migration, legacy JSON, CSV and printable HTML | Future platform adapter, CSV/GPX import and additional schema migrations |
| Themes / app SDK | Editable original dashboard configuration/templates | Versioned sandboxed packages, permissions and richer simulator |
| Media skins / virtual CDs | Planned on the common provider model | Provider APIs, content rights, actual rendering and verified parked restrictions |
| OBD-II / black box | Optional read-only design | Additional compatible hardware and platform permission/storage tests |
| MirrorLink / Pi / scrcpy | Earlier development copies preserved | Certification and descriptor acceptance remain unresolved; separate experiment |
| CarPlay | Research only | Receiver hardware, licensing/authentication and verified display path |

## Remaining platform scope

| System | Next deliverable and dependency |
| --- | --- |
| Ownership analytics | Monthly/yearly trip/cost/fuel charts, purchased-fuel summaries, measured cycle fuel cost and labeled mileage forecasts implemented; comparisons and user-arranged widgets remain |
| Maintenance references | Verified 2017 US notes and offline specifications implemented; extend exact VIN-specific parts fitment, other model years/markets and manufacturer revisions |
| Attachments and backups | Implemented on Android 0.58 with transaction, corruption, stale-review, migration and encryption tests; iOS implementation deferred |
| Automatic trips | Explicit location/background permission, vehicle selection, battery-efficient GPS, route/parking storage and GPX; platform and device checks |
| Read-only diagnostics | Optional supported OBD-II hardware, verified PID support, DTC/readiness history and gauges; no ECU writes or code clearing |
| Black box | Bounded local telemetry/connection timeline, retention, redaction and export; verified data sources and privacy controls |
| Head-unit dashboard | Ownership widgets, media apps, notification grouping and static fallbacks; independent car render/touch/reconnect evidence |
| Media expansion | Provider capability model, queues, virtual CDs, skins, radio/self-hosted sources and listening history; official APIs, rights and parked-state limits |
| Screensavers and wheel shortcuts | Idle/static clock designs and event inspector first; verified rendering/input and a trusted parked signal for animations |
| App ecosystem | Versioned manifests, restricted permissions, original examples and simulator; compatible distribution on both mobile platforms |
| Native platform integration | Signed iOS Garage checks, Android/iOS widgets and automation; platform-specific APIs and signed device testing |
| Protocol tools | Capture import/redaction, decoded inspector, replay and simulator failure cases; synthetic tests before any hardware verification |

This is an incremental development backlog. A planned system is not described as
implemented merely because a placeholder, model or simulator exists.

Shared data formats and Rust calculation/protocol code are preferred where they
help both platforms. Native adapters remain necessary. Android notification
access, arbitrary Classic RFCOMM and third-party media-session control must not
be presented as ordinary iOS-app capabilities.

See [the roadmap](ROADMAP.md) and [platform details](docs/apps.md).
