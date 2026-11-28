# Development roadmap

Preserve the working Android, basic iOS and earlier private research paths.
Build the ownership system into SubaruBridge; do not create a separate tracker.

Android 0.57 consolidates everyday Garage, dashboard, integrations and diagnostic
recording in one app. The former root-ADB-armed recorder is replaced by bounded,
opt-in app metadata recording. Duplicate research apps can remain disabled with
their private data preserved. The original icon, recorder guide and remaining
experimental migration boundary are documented in [One Android app](docs/unified-android.md).
iOS development is paused at the owner's request while Android is the focus.

1. **Media and STARLINK reliability:** fix reproducible Android player/control
   faults; bench-test the iPhone AMS adapter with Taurus; then test independent
   Subaru display, touch, audio coexistence and reconnect. Record evidence per layer.
2. **Ownership foundation:** the Android Garage now uses shared Rust vehicle,
   odometer, uncertain service history, maintenance, fuel and expense models,
   with atomic storage and explicit backup/restore. The iOS storage
   and UI adapter will use the same engine when iOS work resumes. Android 0.58 adds
   tested storage migration, receipts and complete/encrypted reviewed backups.
   Ownership remains usable without a car.
3. **Authoritative maintenance:** Android 0.58 includes verified 2017 booklet
   notes and offline manual specifications. Extend exact VIN-specific filter/CVT
   fluid fitment and schedules for other vehicles. Unknown history stays unknown; estimates stay labeled.
4. **Everyday companion:** Android 0.58 implements reviewed/encrypted complete
   backups, receipts, clearer reminder diagnostics, fuel cost and mileage forecasts.
   Retain overnight reminder observation and extend cross-period comparisons.
   Custom maintenance, service-book details, lead times, snoozes and manual trips
   plus recorded monthly/yearly distance/cost charts are implemented; automatic GPS/routes follow
   explicit permission and battery-budget testing. Do not infer live ECU data.
5. **Infotainment framework:** capability-driven dashboard/widgets, provider-specific
   media features, configurable themes, virtual CDs, notifications and static
   fallbacks. Gate distracting features on reliable parked status.
6. **Cross-platform work:** preserve native Apple and Android conventions; share
   models and logic. Investigate authorized iPhone notification/accessory interfaces
   separately from app-owned notifications, audio and telephone functions.
7. **Extensibility and advanced data:** documented restricted app/widget/theme SDK,
   richer desktop simulator, selective black-box recording and optional read-only
   OBD-II. Keep sensitive records local and access scoped.
8. **Other protocols:** resume MirrorLink and optional Pi/CarPlay bridge research
   when it can answer a specific question. No firmware or safety bypasses.

An iOS port is a first-class target, but feature availability follows verified OS
APIs and accessory capabilities. No physical test result is inferred from mocks.
