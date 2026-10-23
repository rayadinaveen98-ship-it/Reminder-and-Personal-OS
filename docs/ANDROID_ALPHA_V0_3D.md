# Android V0.3d — Backup Recovery & Signing Readiness

**Date:** 2026-10-23  
**Status:** Implemented in development branch; CI and Android emulator results require verification.
**Install ID:** `dev.reminderos.alpha.v03d` (separate from v0.3c and earlier apps).

## Scope

V0.3d is a validation milestone: prove portable encrypted backups can be restored through the actual **Android SQLite engine**, not only with synthetic Python contract and JVM cryptographic tests. The existing V0.3c Android interface and repeat reminders remain, without adding unrelated product features.

## Implemented

- Android instrumentation tests on a freshly initialized emulator app database:
  - Create one-time + recurring reminders, mark an occurrence Done, export a password-encrypted snapshot, inspect and decrypt it, then import into a **clean** app and check record count, series association, outcomes and preserved event histories.
  - Reject wrong passphrases without changing database records.
  - Reject restore into a nonempty database without replacing anything.
  - Confirm no prior OS registration jobs are imported and future schedules can be regenerated from the restored data.
- CI emulator workflow in `.github/workflows/android-device-qa.yml` using real Android system SQLite on an Android 15 / API 35 emulator. This is *more concrete* than the earlier standalone SQLite contract tests.
- Optional, manual, secret-backed Android release signing configuration. `.github/workflows/android-signed-alpha.yml` can only run on trusted `main`, and waits for an authorized `signed-alpha` environment and all signing secrets. **No signing secret, certificate, keystore, or password is committed.**
- V0.3d uses a separate package ID to prevent changes from affecting users' previous developer-alpha test data.

## Critical limitations

- The full export/import via Android Storage Access Framework document UI is still a **manual device QA** item. Emulator DB tests exercise the repository and native SQLite, not the file picker or user's actual documents.
- Instrumentation does not prove native alarm presentation, Doze-mode delivery, notification receipt, OEM differences, or battery behavior.
- CI test APKs are debug-signed using runner-specific keys. An APK signed by a newly configured stable certificate will **not** automatically update an older debug-signed install, even if the application ID matches.
- A stable key must be created and stored securely off-repository, and the same certificate must be used on every future update. A signing workflow being present **does not** mean it's configured.
- V0.3d cannot directly import private reminders from separately installed V0.3b/V0.3c apps without those apps having produced compatible backup exports. **V0.3c .rpos backups should be tested for interoperability on an empty V0.3d app** on a spare device/emulator.
- No background/cloud sync, analytics dashboard, complete iOS parity or production-grade recurrence-editing system yet. iOS is still a separately compiled preview.

## Setting up trusted signing — repository owner action

1. Generate an alpha signing keystore **locally**, on a trusted machine. Store it safely offline with a recovery copy; never commit it or send it in chat.
2. Base64-encode the keystore privately. Configure an environment named `signed-alpha` with required reviewers if desired, and environment secrets named `RPOS_ALPHA_KEYSTORE_BASE64`, `RPOS_ALPHA_STORE_PASSWORD`, `RPOS_ALPHA_KEY_ALIAS`, `RPOS_ALPHA_KEY_PASSWORD`.
3. Merge the reviewed production-ready app version and signing workflow to `main` before invoking **Signed Android Alpha (manual, trusted branch)**. Currently, PRs remain stacked drafts; the workflow cannot yet sign the app from `main`.
4. Use the same signing certificate, package ID and higher version code on future signed releases. Never mix CI debug APKs and production signed APKs as if they were freely interchangeable.
5. Verify APK signing certificate SHA-256 before installing updates. Back up app data via its encrypted export first.

Google Android documentation:
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/build/building-cmdline

## Release quality gate

- Existing shared JVM / portable SQLite / Android unit tests / iOS simulator builds must pass.
- **New emulator job must pass** with actual Android export/restore tests and no database overwrite.
- On a physical device, verify Export → Verify → Restore on a separate empty V0.3d installation, wrong-password rejection and notifications after restore.
- Record package ID, signing identity, version and database version for each build. Do not claim production readiness solely from emulator/CI results.
