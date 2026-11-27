# Feature status

The original Android transport and old private research-adapter car result are
different implementations. Neither a simulator pass nor a delivered media command
is evidence that the independent app works on the Subaru.

| Area | Current implementation | Verification still needed |
| --- | --- | --- |
| STARLINK | Independent Android RFCOMM / Rust SLIP-IP-TCP-HTTP, original script-free UI, touch/result routes, private export | Independent transport on the target physical head unit, touch and reconnect |
| Android music | Generic MediaSession provider selection, advertised controls, toggle-only support, session-bound forms, metadata/timing | Real music apps and audio routing; streaming-provider capabilities vary |
| iPhone / Taurus music | Experimental Android AMS client; shared Rust decoder; separate source choice | Real iPhone pairing, service exposure, Taurus metadata/controls and car coexistence |
| iPhone companion | Native phone-side apps, local diagnostics; separate basic-device branch | Car transport and a general phone-to-phone app channel |
| Maps | Android notification directions; native phone URLs | Real navigation and head-unit rendering; no embedded live maps |
| Calls / messages | Android dialer/composer, confirmed notification replies; native iOS prompts/composer | No arbitrary inbox read or call-audio interception; normal factory hands-free testing |
| Notifications | Android opt-in bounded memory feed; iOS app-owned local notifications | iPhone-wide relay requires separate authorized accessory research |
| Ownership | Shared Rust garage/mileage/maintenance draft preserved separately | Calculation regressions, persistence, Android screens, iOS adapter and authoritative data gaps |
| Maintenance database | Official model-specific schedule research begun | Referenced severe-service notes and critical specifications; never guess missing values |
| Trips / fuel / expenses | Foundation planned around one shared vehicle history | Native permissions, durable schema/migrations, calculation tests and usable phone workflows |
| Themes / app SDK | Editable original dashboard configuration/templates | Versioned sandboxed packages, permissions and richer simulator |
| Media skins / virtual CDs | Planned on the common provider model | Provider APIs, content rights, actual rendering and verified parked restrictions |
| OBD-II / black box | Optional read-only design | Additional compatible hardware and platform permission/storage tests |
| MirrorLink / Pi / scrcpy | Earlier development copies preserved | Certification and descriptor acceptance remain unresolved; separate experiment |
| CarPlay | Research only | Receiver hardware, licensing/authentication and verified display path |

Shared data formats and Rust calculation/protocol code are preferred where they
help both platforms. Native adapters remain necessary. Android notification
access, arbitrary Classic RFCOMM and third-party media-session control must not
be presented as ordinary iOS-app capabilities.

See [the roadmap](ROADMAP.md) and [platform details](docs/apps.md).
