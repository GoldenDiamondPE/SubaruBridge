---
layout: default
title: Ownership storage and backup format
---

# Ownership storage and backup format

Android 0.58 uses one atomic ownership manifest and private receipt blobs.
The Rust business model remains schema 1; Android storage is version 2.
The existing file location is retained so an upgrade finds the old garage.
This Android release does not change the iOS implementation.

## Storage and migration

The v2 object has exactly `format`, `storageVersion`, `generation`, `garage` and
`attachments`. Format is `org.subarubridge.garage.storage`; storageVersion is 2.
Garage contains the Rust state. Generation advances for record or receipt
changes; business revision advances only when the Rust state changes. Counters
stay within the exact JSON integer range.

A bare v1 garage is fully validated, then wrapped and committed with AtomicFile
without changing its business revision. Interrupted writes recover the last
committed file. Malformed, duplicate-key, unsupported and future schemas remain
preserved. Future migrations must validate and atomically commit complete results.

Each receipt has exactly `id`, `kind`, `recordId`, `name`, `mime`, `size`, `sha256`
and `createdMs`. Its record must exist in vehicles, services, fuel, expenses or
trips. Files have SHA-256 names in private storage; imported names are never
paths. Files are published before their manifest references commit. Removal
commits metadata before deleting unused blobs. Unreferenced blobs older than a
day are eligible for cleanup after another successful save.

Limits: 1.5 MiB garage JSON, 2 MiB manifest, 256 receipts, 16 MiB per receipt,
256 MiB total receipt sizes. Supported files are JPEG, PNG, WebP and readable,
unlocked PDFs of 1–500 pages. Android decoders verify each type. Images are
downsampled for preview; PDFs render one bounded page at a time.

## Complete ZIP bundle, version 2

The archive contains exactly manifest.json and blobs/ followed by each distinct
64-character lowercase hexadecimal file hash. Directory entries, duplicate
names, extra files, missing files and path traversal are rejected. Expanded
sizes are bounded. Digest, size, type and decoder validity are checked before review.

The manifest has exactly format, schemaVersion, exportedAt, garage, attachments.
Format is `org.subarubridge.garage.bundle`, schemaVersion is 2.

Legacy records-only JSON remains supported: format `org.subarubridge.garage`,
schemaVersion 1, exportedAt and garage. Its export omits receipts. New optional
business fields such as severeConditions require matching model support in other
platform adapters; old apps may safely reject them.

## Optional encrypted wrapper: SBBK version 1

This platform-independent format uses system cryptographic APIs. Multi-byte
integers are unsigned, big-endian. PBKDF2 uses UTF-8 password bytes without
normalization; passwords are not stored or recoverable.

| Header bytes | Field |
| --- | --- |
| 0–7 | ASCII SBBK, version byte 1, three reserved zero bytes |
| 8–11 | PBKDF2-HMAC-SHA256 iterations, exactly 600,000 |
| 12–27 | Random 16-byte salt |
| 28–39 | Random 12-byte base nonce |

PBKDF2 derives a 32-byte AES key. SecureRandom generates a fresh salt/nonce for
every export. Each payload record contains a four-byte plaintext length, that
many encrypted bytes and a 16-byte AES-256-GCM tag. Plaintext records are at most
256 KiB; indexes start at zero.

For each record, the nonce equals the base nonce with its final four bytes XORed
with the record index. Associated data is the 40-byte header, four-byte record
index and four-byte plaintext length. A separately authenticated zero-length
record terminates the stream. Missing termination, trailing bytes, reordering,
tampering, unsupported parameters and wrong passwords fail. Total plaintext is
capped at 260 MiB. Fixed-size chunks keep phone memory bounded.

Decryption writes only private staging. ZIP parsing and restore review begin
after every record and the terminator authenticate. Failure removes the staging
output. Export requires 10–256 characters. Passwords are excluded from saved
instance state, logs and preferences. The work factor follows
[OWASP's PBKDF2 guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

## Review and commit

Rust previewImport validates incoming records without mutation and returns
new/matching counts plus conflicting versions. ApplyImport requires the reviewed
business revision and a choice for every conflict. Android also checks storage
generation; receipt changes invalidate stale reviews.

Owners choose keep local or use backup where safe. Conflicting odometer IDs must
keep local; corrections use the ordinary explained, append-only history path.
Records cannot move between vehicles, existing amounts cannot acquire another
currency, and receipt-ID conflicts retain the local receipt. The final graph is
validated together. Cancellation, stale review and invalid choices preserve data.

All files stage and verify before manifest commit. A failed commit may leave
unreferenced private blobs; these never appear as attached receipts and are
eligible for later cleanup. Abandoned transfer folders older than one day are
removed when the transfer workspace next opens.
