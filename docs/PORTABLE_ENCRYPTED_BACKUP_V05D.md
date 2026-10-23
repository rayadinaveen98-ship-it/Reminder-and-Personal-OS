# V0.5d — Interoperable Encrypted Portable Backup (Export + Verify)

**Date:** 2026-10-23  
**Stage:** Android + iOS export/verify implemented; CI validation pending  
**Source:** `feature/v05d-portable-encrypted-export`

## User-facing features

- **Android:** New `Export portable backup (Android + iOS)` and `Verify portable backup (no import)` actions in the Private backup & restore section. Uses the standard Documents file picker. The old `Export encrypted backup`, `Verify saved backup`, and empty-database legacy restore continue to use RPOSB1 unchanged.
- **iOS:** SwiftUI secure passphrase and confirmation, encrypted portable export to the Files picker and file-import **verification only**. The iOS app's existing JSON store is never modified by these operations.
- Both generate `RPOSB2` encrypted files with the **same v1 canonical JSON data fields** for recurring rule definitions, original and mutable due times, separate UNKNOWN/DONE/SKIPPED outcomes, archived flags and audit-event history. Platform-specific Android `schedule_jobs` and iOS `UNNotificationRequest` handles are not portable.
- Both platforms can open/decrypt, authenticate and validate a portable file *without importing it*. A portable file must not be fed into the legacy Android restore operation; its B2 header is explicitly rejected by RPOSB1.
- Cross-platform interoperability is tested using the exact same independently generated Python AES-GCM test ciphertext. The test file contains only synthetic data and a public **test-only passphrase**.

## RPOSB2 cryptographic profile (frozen for this alpha)

On-disk layout:
```text
ASCII "RPOSB2" [6] | random salt [16] | random nonce [12] |
AES-256-GCM ciphertext [variable] | GCM authentication tag [16]
```

- KDF: PBKDF2-HMAC-SHA256, **210,000** rounds, 32-byte key.
- Header (6+16+12 = 34 bytes) is AES-GCM **additional authenticated data**. Password or ciphertext/header corruption must reject the file before parsing.
- Portable export requires a **12–128 printable ASCII character passphrase**. This profile deliberately avoids JVM/Swift Unicode normalization mismatches; broader password support requires versioned KDF normalization, not silent compatibility assumptions.
- Authentication alone does not prove the schema is valid: decrypted content is parsed against `portable_format:1`, required fields and whole-snapshot references.
- Maximum unencrypted JSON is **8 MiB**, with encrypted file capped at 8 MiB + 4096 bytes. No plaintext portable snapshots are intentionally saved to disk in either app.
- This is a **password-based portable backup**, not cloud synchronization, account identity or a multi-device automatic merge. Users should choose a unique secret passphrase and retain it separately.
- **The original Android RPOSB1 format remains byte-for-byte unchanged**, including existing empty-store restore behaviour.

## Portable JSON v1 fields

```json
{
  "portable_format": 1,
  "rules": [
    {
      "id": "string", "title": "string", "anchor_at_ms": 1792771200000,
      "cadence": "DAILY", "weekday_mask": 0,
      "paused": false, "archived": false
    }
  ],
  "reminders": [
    {
      "id": "string", "title": "string",
      "due_at_ms": 1792771200000,
      "original_due_at_ms": 1792771200000,
      "revision": 1, "outcome": "UNKNOWN",
      "paused": false, "archived": false,
      "series_id": null, "slot_key": null
    }
  ],
  "events": [
    {
      "id": "string", "reminder_id": "string", "kind": "CREATED",
      "revision": 1, "at_ms": 1792771200000
    }
  ]
}
```

Timestamps are integer UTC epoch milliseconds; recurring slot keys are local **YYYY-MM-DD** strings. A snooze changes `due_at_ms` and revision, **not** `original_due_at_ms` or `slot_key`. Native permission, alarm delivery state and schedule handles are excluded.

## Quality gates

- Android emulator checks: old RPOSB1 restore regression, portable SQLite export/verify leaves current DB untouched, wrong password/tampering fail, independent Python RPOSB2 file decrypts with the correct UNKNOWN outcome.
- iOS simulator XCTest: same independent RPOSB2 file decrypts, tampering and wrong password fail, iOS-generated encrypted payload round-trips, and a real synthetic JSON store is unchanged after export.
- Shared Kotlin snapshot and recurrence tests, JVM and SQLite reference tests, iOS Kotlin/Native framework linkage and Android debug APK assembly remain required.
- **Never claim portable import, rollback or restore in V0.5d.** Import is intentionally disabled until write transactions, backup-before-import, verify-after-import and full rollback are tested.

## Outstanding limitations and next plan

1. Implement versioned **transaction-safe empty-store import** for Android SQLite and iOS JSON, preserving IDs, history, archives, unresolved states and all original calendar slots. Do not overwrite existing populated stores.
2. Detect / block migration data loss from fields not represented in v1 (e.g. OS attempt counters and native timestamps). Source records remain the authority until migration tests are complete.
3. Verify import-then-export round-trips on both platforms, with simulated disk errors and corrupted files. Register future OS notifications only after a committed import.
4. Deliver stable signing and real-phone QA. The Android V0.5d package is a *separate developer alpha*; the iOS artifact is Simulator-only, not a signed iPhone IPA.
5. Full shared database ownership/merging, synced identities, and unattended iOS recurrence still require work.
