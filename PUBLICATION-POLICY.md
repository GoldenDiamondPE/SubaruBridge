# Public source boundary

The public SubaruBridge repository is a **fresh, allowlisted source export**.
Do not publish this working tree or its Git history wholesale.

Allowed: original SubaruBridge implementation, original UI assets, tests and
documentation; separately identified open-source build tools under their licenses.

Never include:

- Original, modified or repackaged SUBARU STARLINK APKs.
- Decompiled Java, smali, extracted resources, vendor SDKs, libraries or signing material.
- Vehicle firmware or update packages, downloaded specifications or archived vendor websites.
- Raw USB/network/Bluetooth captures, device backups, bug reports, logs or screenshots.
- Personal identifiers, credentials, private research folders or their Git history.
- Unreviewed binaries or generated application packages.

Research APKs and captures remain in the private investigation workspace. The
public app must build from its own source without requiring either STARLINK APK.
Protocol observations do not grant permission to redistribute vendor code or art.
Keep original open-source attribution and license notices where applicable.

The public export script selects paths explicitly, rejects symlinks and prohibited
file types, and creates a SHA-256 inventory. Its checks are an additional guard,
not a substitute for reviewing source provenance. Use a separate export directory
and new public Git history; never push the research repository to a public remote.
