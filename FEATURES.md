# Feature status

The original Android transport and old private research-adapter car result are
different implementations. Neither a simulator pass nor a delivered media command
is evidence that the independent app works on the Subaru.

| Area | Current implementation | Verification still needed |
| --- | --- | --- |
| STARLINK | Independent Android RFCOMM / Rust SLIP-IP-TCP-HTTP, original script-free UI, touch/result routes, private export | Independent transport on the target physical head unit, touch and reconnect |
| Android music | Generic MediaSession provider selection, advertised controls, toggle-only support, session-bound forms, metadata/timing | Real music apps and audio routing; streaming-provider capabilities vary |
| iPhone / Taurus music | Experimental Android AMS client; shared Rust decoder; separate source choice | Real iPhone pairing, service exposure, Taurus metadata/controls and car coexistence |
| iPhone companion | Native phone-side apps, local diagnostics and app-owned notifications, included in this branch | Native Garage adapter, car transport and a general phone-to-phone app channel |
| Maps | Android notification directions; native phone URLs | Real navigation and head-unit rendering; no embedded live maps |
| Calls / messages | Android dialer/composer, confirmed notification replies; native iOS prompts/composer | No arbitrary inbox read or call-audio interception; normal factory hands-free testing |
| Notifications | Android opt-in bounded memory feed; iOS app-owned local notifications | iPhone-wide relay requires separate authorized accessory research |
| Ownership | Native Android Garage; bounded atomic private storage, profiles, confirmed/approximate readings, correction trail and versioned Rust calculations | iOS persistence/UI adapter, attachments and future schema migrations |
| Maintenance | 18 sourced normal-service rules for US 2017 Legacy 2.5 CVT; uncertain ranges, date/distance thresholds, editable owner-defined items and intervals on any vehicle | Manufacturer severe-service notes and verified schedules for other vehicles |
| Service book | Multi-item visits, dates/ranges, DIY/provider, parts/labor/tax breakdown, owner-recorded part numbers, warranty notes, search and printable history | Receipt/photo/PDF attachments and richer part records |
| Maintenance reminders | Opt-in daily Android inexact alarm and local notification; configurable lead times and snoozes; saved changes trigger checks; unknown history and archived vehicles excluded | Notification delivery after device power management/reboot; iOS adapter |
| Trips / fuel / expenses | Completed manual trips, reconciled estimates, full/partial/missed fills and derived operating/financing totals; editable records | Automatic GPS, routes, richer analytics and safeguards for auto recording |
| Ownership summaries | Shared monthly/yearly calculations, native distance/cost charts and monthly details; corrected readings and undated costs remain explicit | Forecasts, configurable analytics widgets, trend comparisons and cost-per-mile coverage logic |
| Data ownership | User-chosen JSON export/additive restore, CSV history and escaped printable offline HTML service report | Attachments, encrypted backups, conflict-resolution UI and CSV/GPX import |
| Themes / app SDK | Editable original dashboard configuration/templates | Versioned sandboxed packages, permissions and richer simulator |
| Media skins / virtual CDs | Planned on the common provider model | Provider APIs, content rights, actual rendering and verified parked restrictions |
| OBD-II / black box | Optional read-only design | Additional compatible hardware and platform permission/storage tests |
| MirrorLink / Pi / scrcpy | Earlier development copies preserved | Certification and descriptor acceptance remain unresolved; separate experiment |
| CarPlay | Research only | Receiver hardware, licensing/authentication and verified display path |

## Remaining platform scope

| System | Next deliverable and dependency |
| --- | --- |
| Ownership analytics | Monthly/yearly recorded trip/cost charts and fuel-cycle details are implemented; extend with forecasts, comparisons and user-arranged widgets |
| Maintenance references | Exact severe-service notes, fluids/capacities/torques and parts; authoritative model-specific review before publishing facts |
| Attachments and backups | Private receipt/photo/PDF storage, bundled restoration, encrypted export and conflict review; transactional file/schema tests |
| Automatic trips | Explicit location/background permission, vehicle selection, battery-efficient GPS, route/parking storage and GPX; platform and device checks |
| Read-only diagnostics | Optional supported OBD-II hardware, verified PID support, DTC/readiness history and gauges; no ECU writes or code clearing |
| Black box | Bounded local telemetry/connection timeline, retention, redaction and export; verified data sources and privacy controls |
| Head-unit dashboard | Ownership widgets, media apps, notification grouping and static fallbacks; independent car render/touch/reconnect evidence |
| Media expansion | Provider capability model, queues, virtual CDs, skins, radio/self-hosted sources and listening history; official APIs, rights and parked-state limits |
| Screensavers and wheel shortcuts | Idle/static clock designs and event inspector first; verified rendering/input and a trusted parked signal for animations |
| App ecosystem | Versioned manifests, restricted permissions, original examples and simulator; compatible distribution on both mobile platforms |
| Native platform integration | iOS Garage, Android/iOS widgets, local reminders and automation; platform-specific APIs and signed device testing |
| Protocol tools | Capture import/redaction, decoded inspector, replay and simulator failure cases; synthetic tests before any hardware verification |

This is an incremental development backlog. A planned system is not described as
implemented merely because a placeholder, model or simulator exists.

Shared data formats and Rust calculation/protocol code are preferred where they
help both platforms. Native adapters remain necessary. Android notification
access, arbitrary Classic RFCOMM and third-party media-session control must not
be presented as ordinary iOS-app capabilities.

See [the roadmap](ROADMAP.md) and [platform details](docs/apps.md).
