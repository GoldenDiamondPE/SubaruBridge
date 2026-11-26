---
layout: default
title: Create dashboards and integrations
---

# Create dashboards and integrations

The UI is original HTML/CSS, intentionally usable without JavaScript. Older head
unit browsers may reject JavaScript that works in a current Android WebView.
Keep ordinary links and form submissions as a working fallback.

| File | Purpose |
| --- | --- |
| `android/headunit-ui/dashboard.json` | Labels, tile order, colors, theme defaults, columns |
| `android/headunit-ui/dashboard.css` | Spacing, type, tiles, dock, responsive rules |
| `android/headunit-ui/templates/*.html` | Pages and reusable components |
| `android/headunit-ui/icons/*.svg` | Original editable icons |
| `CarUi.java` | Escaped view data and server rendering |
| `StarlinkRouter.java` | Local route methods, validation and results |
| `rust/src/` | Platform-independent protocol state |

Compile desktop previews without building an APK:

```sh
python3 scripts/compile_car_ui.py --output .local/ui/assets --landing .local/ui/home.html --previews .local/ui/themes
```

Open a generated HTML file. Static previews show layout only; forms need the app's
loopback server. Keep diagnostic element IDs if existing regression tests use
them. The compiler validates theme values, route mappings and script-free assets.

## Add a local integration

1. Start with an original, read-only screen under `/subarubridge-diag/your-feature`.
2. Add an explicit handler in `StarlinkRouter.handle`. Validate the method and
   input; escape user-visible text with `HtmlView.escape`. Use bounded request
   and response sizes and literal local links.
3. Add a template and a link from a page. The four existing dashboard tile IDs
   are reserved for the test flow; changing their labels/order is supported.
   To add new tile types, extend the compiler and `CarUi` mapping together.
4. Add a synthetic HTTP regression before connecting a car. Keep the display
   usable if a permission is denied or an optional service is absent.
5. Test through phone preview, an independent transport peer and a parked car.

Do not paste code or art from the proprietary STARLINK APK. Do not add arbitrary
shell execution, a forward proxy, unbounded file access or changes to vehicle
firmware, authentication or driving restrictions. Music, calls, messages and
navigation need their own platform permission and protocol research. Static
sample data can support a UI prototype; label it clearly as sample data.

## Preparing for another platform

The Rust core does not open Bluetooth sockets, read Android files or render an
iOS app. `rust/include/subarubridge_core.h` defines opaque handles, byte batches
and Rust-owned buffer lifetime. An eventual platform adapter must supply secure
socket I/O, monotonic time and response routing. This does not establish that iOS
permits the required Classic Bluetooth accessory connection; investigate the
platform and accessory requirements before promising an iPhone port.
