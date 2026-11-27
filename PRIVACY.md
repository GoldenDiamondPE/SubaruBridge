# Local data and privacy

SubaruBridge has no account requirement, analytics tracker, advertising SDK or
mandatory remote service. The ownership engine has no network or file access.
Android stores Garage records in app-private no-backup storage. Device storage
protection applies; this first version does not add a separate database password.
Uninstalling or clearing the app removes those records.

VINs, plates, odometer/service history, costs and notes remain local. They are
excluded from STARLINK HTTP routes, media snapshots and connection diagnostic
reports. Maintenance notifications show counts. Sharing message content remains
a separate, off-by-default Android setting; the iOS companion has no general
notification inbox access.

Export is an explicit action through the system document picker. Garage backups
contain all saved vehicles and ownership records, including optional identifiers.
CSV and service reports contain the chosen vehicle history. Exports are currently
unencrypted. Choosing a remote document provider can upload the selected file
through that provider. There is no automatic backup or synchronization.

Automatic GPS trips, location history, OBD-II telemetry and extended vehicle
logging are not active in this build. Future implementations require their own
permissions, retention/storage limits and export controls. The app does not
modify ECU settings, clear diagnostic codes or replay unknown messages to a car.

Private packet captures, original vendor applications, credentials, signing
material and extracted assets are not part of the public source repository.
