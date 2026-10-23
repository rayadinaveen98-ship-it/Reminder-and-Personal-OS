# Android V0.3c — Password-encrypted reminder backups

**Date:** 2026-10-23
**Status:** Developer alpha. Android APK and JVM crypto checks passed in CI; export/import on a physical device is NOT yet verified.
**Package:** `dev.reminderos.alpha.v03c`, intentionally separate from the previous test packages.

## Why backups are a priority

Previous alpha builds have unstable debug APK signing because GitHub runner keystores are temporary. A new build may not install over an existing package without uninstalling, which would delete the old package's local data. The long-term solution is a stable Android signing key managed through GitHub secrets **plus** independent backup/restore.

**This V0.3c build can export and import backups created by V0.3c. It cannot directly read reminders stored privately by V0.2, V0.3a or V0.3b, because Android isolates data across package IDs, and those older builds do not provide this export action. Do not uninstall previous alphas to test V0.3c.**

## Implemented in V0.3c

- Export a user-selected `.rpos` file via Android's Storage Access Framework (`ACTION_CREATE_DOCUMENT`) without requesting broad shared-storage permissions.
- Password-based AES-256-GCM encryption with authenticated header, random 16-byte salt, random 12-byte nonce and PBKDF2-HMAC-SHA256 (210,000 iterations). Export requires at least 12 characters. No passwords or unencrypted backup files are intentionally persisted.
- Portable versioned JSON plaintext inside the encrypted container. Contains **all** reminder/recurrence rows including archived data, explicit outcomes, original due dates, snoozes and audit events.
- Native platform registration IDs and pending scheduler operation jobs are *not* exported: these would be invalid in a different installation.
- **Verify saved backup** opens an encrypted file, authenticates it with the passphrase and validates the record structure, showing counts **without importing or modifying any data**.
- Restore through `ACTION_OPEN_DOCUMENT`, requiring the correct passphrase, supported backup format and valid schema references.
- Export uses a consistent database transaction snapshot across rules/reminders/events. **Restore only into an empty database.** No merge or destructive overwrite. One database transaction inserts all rules, occurrences and events; failures roll back everything.
- Rebuild eligible native future alarm registrations after a successful restore; past due but unanswered reminders remain unresolved.
- File size capped to about 8 MB to bound memory use. Large account histories may require a later streaming backup format.

## Test plan

Native Android `EncryptedBackupTest` checks encryption roundtrip, random nonces, wrong passwords, authenticated header/ciphertext corruption, short passwords, and truncated/oversized files. Existing SQLite migration and recurrence tests remain in CI.

**Important limitation:** The new SQLite export/import pathway is Android source-compiled but is not yet instrumented-tested on a device. The crypto unit tests do not prove successful UI picker, actual export, restore, power-loss recovery or native alarm delivery. Real-device testing is mandatory.

## Safe manual QA

1. Install V0.3c **without removing previous alpha packages**.
2. Create one-time and repeating reminders, and mark some occurrences Done/Skipped.
3. Tap **Export encrypted backup**, supply/confirm a unique 12+ character passphrase, and select a safe file location.
4. Keep the saved `.rpos` backup and passphrase outside the app. Never put either in a public repository or support ticket.
5. Use **Verify saved backup** to select the exported file and enter its passphrase. Confirm that the app displays rule/reminder/event counts and has not changed existing data.
6. Observe that Restore refuses to run on the populated app, without deleting existing reminders.
6. On a separate clean V0.3c installation (or a dedicated test device/emulator with no valuable data), choose Restore, select the exported file and enter the password.
7. Confirm rules, reminders, action history and outstanding future alarms are recreated. Check a recurring reminder's next notification on-device.
8. Test a wrong passphrase and edited/truncated file: no records should be imported.
9. Verify that export/restore works offline and that no sensitive contents appear in Android logs.

Do not uninstall or wipe your only copy of test data just to verify restore. Prefer an emulator or spare test device. Backup file alone is not a substitute for a verified recovery roundtrip.

## Important outstanding work

- Stable Android signing key managed securely in GitHub Actions/release infrastructure. No private signing key in the public repository.
- Native instrumented tests for SQLite backup/restore with simulated crashes, and end-to-end UI picker tests.
- Import/export support in previous separately installed alpha packages is not possible without building suitably signed updates for them or a controlled migration path.
- Repeating reminder editing, iOS feature parity, syncing, dashboard analytics and a premium interface.
- Recovery after Doze, reboot, denied permissions and platform-specific notification limits remains unverified on real devices.

## References

- Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Android cryptographic key generation guidance: https://mas.owasp.org/MASTG/knowledge/android/MASVS-CRYPTO/MASTG-KNOW-0012/

## Verified CI release artifact — October 23, 2026
- [GitHub Actions run #39899394481](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39899394481) passed all four jobs: Android debug APK and native JVM crypto/calendar tests, shared JVM, portable SQLite contracts and iOS simulator host.
- CI artifact: `android-reminder-v03c-debug`, source revision `85cc0891e33163784642128b925df428d815ae82` (the latest code changes, including Verify Backup and consistent SQLite snapshot).
- Extracted APK: **938,098 bytes**, SHA-256 `13d14c131ecaf13b8b141dbf5fcd57210ea2c4d2f7902bca9e8cd9d5a0e48f73`; ZIP integrity, binary AndroidManifest.xml and compiled DEX verified.
- App package `dev.reminderos.alpha.v03c` installs separately from V0.3b. **No automatic transfer from older alphas.**
- **No physical-device export/import, incorrect-password UI, background alarm or crash-recovery tests have been performed.** Treat encryption/restore as a test feature until a real roundtrip passes.
