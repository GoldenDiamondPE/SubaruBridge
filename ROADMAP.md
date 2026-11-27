# Development roadmap

Preserve the working Android, basic iOS and earlier private research paths.
Build the ownership system into SubaruBridge; do not create a separate tracker.

1. **Media and STARLINK reliability:** fix reproducible Android player/control
   faults; bench-test the iPhone AMS adapter with Taurus; then test independent
   Subaru display, touch, audio coexistence and reconnect. Record evidence per layer.
2. **Ownership foundation:** finish and test the preserved shared Rust vehicle,
   odometer, uncertain service history, maintenance, fuel and expense models.
   Add bounded atomic persistence, migrations and Android garage/maintenance
   screens, followed by a native iOS adapter. Ownership remains usable without a car.
3. **Authoritative maintenance:** complete manufacturer schedule notes, provenance,
   severe-service applicability and verified parts/fluid references for the exact
   initial vehicle. Unknown history stays unknown; estimates stay labeled.
4. **Everyday companion:** local reminders, export/restore, fuel and service history,
   expenses and analytics. Add manual trips before optional automatic GPS tracking.
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
