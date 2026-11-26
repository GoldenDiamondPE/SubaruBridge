# SubaruBridge head-unit UI

This is the editable presentation layer for the SubaruBridge 0.51-dev
dashboard. It uses ordinary HTML links and forms, original SVG icons, and CSS
compatible with the older-browser test baseline. No application JavaScript,
external fonts, CDN or cloud service is needed for these pages.

## Change the design

| File | What to edit |
| --- | --- |
| `dashboard.json` | Brand text, tile names/order, icon assignments, colors, default theme/layout and column counts. |
| `dashboard.css` | Dock, spacing, typography, rounded tiles, responsive layout and individual components. |
| `templates/shell.html` | The shared frame, header and side dock. |
| `templates/home.html` and `tile.html` | Dashboard composition and app tiles. |
| `templates/display.html`, `touch.html`, `phone.html`, `results.html` | Each functional connection-check screen. |
| `templates/settings.html`, `developer.html` | Appearance controls and developer workspace. |
| `icons/*.svg` | Original vector icons; edit paths, strokes and shapes. |

The Style button changes Midnight, Daylight and Coast themes, plus app-grid or
list layout. Preferences stay on the Pixel and update the car's entry page.
Developer remains one dock tap away. Diagnostic state and saved results remain
backed by the existing native handlers and private journal.

Tokens such as `{{accent}}` are resolved into literal CSS at build time, so the
head unit does not need CSS variables. HTML tokens are resolved from escaped
values or trusted component markup by `CarUi.java`; changing page markup does
not require moving Bluetooth or event-validation code into the UI.

## Preview and build

From the repository root:

```sh
python3 scripts/compile_car_ui.py --output .local/ui/assets --landing .local/ui/home.html --previews .local/ui/themes
```

Open the generated HTML files to compare themes and layouts. Functional forms
need the Android app's loopback server. `gradle -p android assembleDebug` compiles
these same sources automatically. `CarUi.java` renders them, `StarlinkRouter.java`
handles requests and `DiagnosticEngine.java` validates and journals events.
No proprietary STARLINK APK or SDK is used by these components.

Keep the IDs used by tests (`start-basic`, `basic-nav1` through `basic-nav4`,
`basic-run-id`, `basic-submit`) when restyling. The four built-in tile IDs map to
existing handlers; changing labels/order is configurable, while a new action
needs an explicit server handler. Templates are script-free and icons cannot
include scripts, event attributes or external resources. The compiler validates
these constraints instead of silently generating a broken fallback.

The new independent app still needs physical Subaru rendering, touch, theme and
reconnect verification. Modern WebView and desktop preview success alone do not
prove compatibility with a car browser.
