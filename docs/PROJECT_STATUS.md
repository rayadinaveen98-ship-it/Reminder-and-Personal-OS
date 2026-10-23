# Project Status

**Last updated:** 2026-10-23  
**Repository:** `rayadinaveen98-ship-it/Reminder-and-Personal-OS`  
**Working branch:** `feature/v05d-portable-encrypted-export` (stacked on V0.5c)  
**Stage:** V0.5d cross-platform RPOSB2 encrypted export and verification built/tested on Android+iOS; portable import/shared persistent DB still pending

## Completed — documentation only
- Confirmed project vision, Android + iOS scope and offline-first design principles.
- Documented proposed Kotlin Multiplatform architecture and native OS integration boundaries.
- Drafted [V1 PRD](PRODUCT_REQUIREMENTS_V1.md) with priority-ranked feature scope and acceptance criteria.
- Drafted [Reminder Behaviour Contract](REMINDER_BEHAVIOR_V1.md), including unknown/no-response semantics, recurrence edge cases, snooze and follow-up policy.
- Drafted [Data and Analytics Contract](DATA_ANALYTICS_CONTRACT_V1.md), including explicit metric formulas and evidence rules.
- Drafted [V1 Quality Gates](QUALITY_GATES_V1.md) for automated tests and Android/iOS physical-device scenarios.
- Recorded outstanding technical/product decisions; all substantive new implementation choices remain **proposed**.

## Initial technical spike — 2026-10-22
- Added dependency-free Kotlin reminder outcome reducer, explicit user action handling, idempotency and completion/unknown-share summary.
- **PASS:** 16 JVM smoke checks with Kotlin CLI 1.9.0 and Java 21 on Linux, using `sh scripts/run_core_smoke.sh`.
- Added [Initial Feasibility Report](TECH_FEASIBILITY_SPIKE.md) documenting KMP/Compose/Room candidates and OS notification constraints.
- **Limitation:** no Gradle, Android SDK or Xcode available locally; KMP Android/iOS compilation and native delivery are not tested.

## Calendar and reconciliation spike — 2026-10-22
- Implemented pure Kotlin Gregorian date/weekday and once/daily/weekly/monthly local recurrence generation.
- Implemented OS-neutral reconciliation planner for missing, stale, duplicate and revised pending notification registrations.
- **PASS:** 37 additional Kotlin/JVM assertions; 16 prior outcome/analytics checks still pass. **Total: 53 JVM checks.**
- See [Calendar and Reconciliation Spike](CALENDAR_RECONCILIATION_SPIKE.md).
- **No actual Android/iOS alarms, native compilation or device tests yet.** Calendar-to-UTC/DST resolution and transactional persistence remain pending.

## KMP platform foundation — 2026-10-22
- Added provisional Kotlin Multiplatform Gradle build module targeting JVM, Android KMP library, iOS Arm64 and iOS Simulator Arm64.
- Added common-test Kotlin assertions for reminder and native scheduler interface behaviour.
- Added `NativeScheduleGateway` capability/result contract and guarded reconciliation executor.
- **PASS local Kotlin CLI:** 12 additional adapter-boundary smoke checks; 65 total across three smoke suites.
- Added GitHub Actions workflow to request JVM + Android compilation on Linux and iOS Simulator compilation on macOS.
- **CI evidence:** Initial run [#39803890112](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39803890112) passed iOS Simulator shared-code compilation but failed Android SDK setup. Corrected run [#39804136951](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39804136951) passed shared JVM tests **and Android library compilation**; iOS job was still running at last check (initial run had succeeded). Split the latest CI workflow into separate JVM/Android/iOS jobs so platform bootstrapping cannot hide JVM test results. No native notification delivery, signed app or physical-device test was performed.
- Added [Native Platform Validation Plan](NATIVE_PLATFORM_VALIDATION.md) and recorded pinned candidate tooling.

## Native notification preview — 2026-10-22
- Created androidApp debug shell with AlarmManager single-test notification, Android permission checks, exact/inexact fallback, cancel UI and eligible reboot/time-change recovery.
- Created iosApp SwiftUI single-test local notification shell with UNUserNotificationCenter authorization, one-shot scheduling, cancellation and foreground presentation.
- Updated GitHub Actions to compile the Android debug APK and generate/build the Xcode iOS Simulator preview; binaries and actual notification timing must be verified through CI and device tests.
- Documented [Native Notification Preview](NATIVE_NOTIFICATION_PREVIEW.md) limitations. iOS Swift app is not yet connected to shared Kotlin rules.
- Prior KMP CI run #39804136951 completed Android/JVM and iOS Simulator shared-library builds successfully.
- No complete functional reminders, tracked outcomes, historical analytics or durable multi-reminder database yet.

## Not yet implemented or verified
- Android and iOS notification preview shells exist in source; neither has verified end-to-end native delivery or user completion tracking. Production AlarmManager/UNUserNotificationCenter adapters and shared database remain pending.
- Previous KMP shared-library CI builds passed. New Android APK and iOS Simulator preview app CI build passed. No physical-device evidence, signed iOS IPA or release.
- No finalized OS version floor, KMP UI sharing validation or persistence library decision.
- No user approval for the V1 PRD, individual reminder defaults or visual design.

## Next work — in order
1. Review and lock a small set of P0 product behaviour choices (quiet hours, follow-up defaults, recurrence edges).
2. Verify Android APK / iOS Simulator app CI builds and correct compilation failures; test local notifications on physical devices, then implement shared local persistence.
3. Finalize module boundaries, version matrix and data migrations from the tested spike.
4. Design main journeys and design system, then scaffold the project on an implementation branch with basic tests.
5. Build and test reminder core before advanced analytics or optional AI.

## Change protocol
Every active work session starts with reading repo history and open branches. Commit related code/docs/tests and update status/decision log; report exact evidence and commit/PR. A planning document is not implementation or test evidence. No unattended work happens between sessions.

## History
- **2026-10-22:** Initial architecture planning docs created.
- **2026-10-22:** V1 scope, reminder lifecycle, event/analytics model and quality gates drafted on documentation branch.
- **2026-10-22:** Initial portable Kotlin reminder-core smoke test passed (16 JVM checks); platform feasibility report documented.
- **2026-10-22:** Added calendar recurrence and OS registration reconciliation spike with 37 additional JVM checks (53 total); native delivery still untested.
- **2026-10-22:** Added KMP target configuration, shared scheduling gateway and 12 local JVM checks (65 total); shared Android/iOS CI compilation passed.
- **2026-10-22:** Added native Android/iOS one-alert notification preview source and CI build targets; new app builds and device notification delivery not yet verified.

## Android preview CI validation — 2026-10-22
- GitHub Actions [run #39807577755](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39807577755) **passed** shared JVM checks, Android KMP shared-library compilation and separate Android app debug APK assembly.
- Verified the artifact **android-native-preview-debug**, containing ReminderAndroidPreview-debug.apk, size 884,810 bytes, SHA-256 `06d9a09a231808779e5fe5313f37cd43588ab1eebac5f1878e48a2d5d213c03f`.
- The Android preview is deliberately isolated as a separate Gradle project to avoid mixed AGP 9 Kotlin/KMP compiler classloader issues encountered in earlier failed CI builds. It is **not bridged into the KMP domain engine yet**.
- iOS Simulator preview app **compiled successfully** in Actions run #39807577755. All physical-device notification-delivery tests remain **unverified**. No signed iOS IPA or production-ready reminder APK exists.

## Full platform preview build evidence — 2026-10-22
- **PASS:** [GitHub Actions #39807577755](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39807577755) completed all three jobs successfully: JVM unit tests, Android shared-library plus native APK, and iOS shared-library plus SwiftUI simulator application.
- Android artifact: **android-native-preview-debug** (debug APK, no on-device reliability test).
- iOS artifact: **ios-native-preview-simulator** (simulator .app, not a signed device .ipa).
- PR #5 remains a draft because physical-device permission/background/notification delivery, production storage and integration with shared KMP business logic are not completed.

## Portable persistence and outbox spike — 2026-10-22
- Added `storage/schema/v1.sql`: versioned reminder/occurrence/action/outbox/platform registration SQL schema with foreign keys and integrity constraints.
- Added `tools/sqlite_reference.py`: transactionally records reminder creation, explicit actions, cancellation/re-registration intentions, retryable failure states and accepted native-registration projection. **Executable reference, not mobile app repository implementation.**
- Added `tests/test_sqlite_reference.py` for failure rollback, duplicate action callbacks, snooze, job superseding/retry, unknown-outcome analytics, SQLite reopen and schema refusal.
- Added independent `sqlite-contract` GitHub Actions job; CI outcome must be checked before claiming tests passed.
- Documented [Durable Persistence Contract](PERSISTENCE_CONTRACT_V1.md), crash windows and physical-device validation still outstanding.
- Production cross-platform persistence library selection (Room KMP / SQLDelight), schema migrations, native outbox executor, concurrent writes, encrypted backup and device verification remain pending.

## Android functional alpha — 2026-10-22
- Created a **separate Android alpha package** `dev.reminderos.alpha`, preserving the earlier one-alert test app.
- Implemented persistent native SQLite one-time reminders, multiple scheduled alarms, in-app reminder list, date/time selection and Done/Skip/Snooze 10m controls.
- Implemented per-reminder PendingIntent identity, notification actions with revision checks, an Android scheduling outbox, and recovery attempts after supported reboot/time-change events.
- **Build verification:** [GitHub Actions #39819474781](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39819474781) passed all four jobs: Android APK, iOS simulator preview build, shared JVM and **25 SQLite reference contract tests**.
- APK artifact: `android-reminder-alpha-debug`; source commit `9d1c9dad5eaee00b74142365bbd03d3fa6c5628d`; extracted APK SHA-256 `e404e0ad58bb83f696d9e89de0f711194c960286f4c9275be7c3aedf5cb643ab`.
- See [Android Alpha v0.2 QA and limitations](ANDROID_ALPHA_V0_2.md). **No physical-device notification delivery has been verified.**
- Current Android alpha persistence is separate from shared KMP/portable reference implementation. iOS remains an independent one-alert preview, not matching the Android alpha functionality.
- **Next:** on-device test notification permission/Doze/reboot/cancel/snooze; production cross-platform persistence adapter, tested migrations, signed build continuity and feature parity.

## V0.3a reminder management — 2026-10-23
- Added non-destructive SQLite alpha schema v1 -> v2 migration (paused/archived flags; existing reminder/action/job rows preserved) and Python contract tests that exercise the exact Kotlin ALTER statements.
- Implemented revision-guarded edit, pause/resume, soft archive, flexible in-app snooze, recent history and manual future-alarm repair.
- New test app ID `dev.reminderos.alpha.v03` ensures the earlier v0.2 test app remains installed with its existing local data even if CI debug signing differs. **No automatic v0.2-to-v0.3a data transfer** between separately installed packages.
- [V0.3a scope, QA and limitations](ANDROID_ALPHA_V0_3A.md). One-time reminders only: reliable per-occurrence recurrence across unanswered reminders remains V0.3b work.
- No claim of APK readiness, unit tests passing, or physical-device reliability until the current CI run and artifact are verified.

## V0.3a build result — 2026-10-23
- [GitHub Actions #39895650894](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39895650894) passed SQLite (29 tests including V0.2→V0.3a migration contract), JVM, Android preview APK and iOS simulator jobs.
- Android alpha V0.3a APK artifact: `android-reminder-v03a-debug`, **909,102 bytes**, SHA-256 `b5f86f7b9ac69597c5dc1d84ae0364dcd0b0b1e74ea333aea1c635c912855b8c`.
- Package `dev.reminderos.alpha.v03` is isolated to preserve V0.2 installation and local data despite non-reproducible CI debug signing. The apps do not exchange their user data.
- On-device tests of edit/pause/archive/notification cancellation/boot recovery are **still pending**. Cross-platform persistence, recurring rules, backup/export and stable signing remain incomplete.

## V0.3b recurrence implementation — 2026-10-23
- Android-specific calendar engine handles once/daily/weekly-selected-days/monthly (skipping invalid dates) using local calendar arithmetic instead of fixed 24-hour intervals.
- SQLite v3 adds recurrence rules and immutable per-date occurrence IDs linked via nullable `series_id` and unique `slot_key`.
- Each occurrence records its own Done/Skip/unknown state; a missed date cannot block a later due slot.
- Rolling scheduling targets seven future occurrences, with bounded overdue catch-up. Materialization runs after creating a rule, alarm callback, app reopen and supported recovery broadcasts.
- Routine-level pause/resume/archive; per-occurrence actions; summarized routine history; developer UI for repeat selection.
- Kotlin JUnit tests added for calendar/DST and Python v2→v3 SQLite migration tests. **CI outcome must be checked before release claims.**
- V0.3b uses a separate installed package `dev.reminderos.alpha.v03b` to protect v0.3a/v0.2 local data in the absence of stable signing. There is no cross-package data import.
- See [Android V0.3b QA](ANDROID_ALPHA_V0_3B.md).

## Verified V0.3b build — 2026-10-23
- [Actions run #39897461506](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39897461506) passed all four CI jobs, including Android JUnit recurrence tests and APK build, 32 SQLite contract/migration tests, shared JVM and iOS Simulator build.
- Android Actions artifact `android-reminder-v03b-debug`, binary **925,602 bytes**, SHA-256 `78de6e56c343f2fd96acfa597b43aa3c9e846cea0174f205730268d1747c58f3`; APK zip integrity and manifest/classes entries verified.
- Developer test package `dev.reminderos.alpha.v03b` installs independently of previous alphas. V0.3b does not import V0.3a/V0.2 reminders; never uninstall previous versions without backup.
- Per-occurrence recurring history is implemented in Android only and is unverified on real devices; native iOS host is still a one-alert preview.
- Outstanding: production/reproducible APK signing, encrypted export/import, recurring rule editing, travel/DST edge-case validation, real-device battery/reboot testing and full tracking/analytics.

## Encrypted backup V0.3c — 2026-10-23
- Added authenticated AES-256-GCM password-based portable backup (PBKDF2-HMAC-SHA256, random salt/nonce, bounded payload). No plaintext file writes or password storage are intended.
- Android system file-picker export/import, all-or-nothing restore into an empty app database, and rebuilding of future scheduling registrations after restore.
- Exports reminder records, recurring rules and event history, explicitly **not** Android OS alarm handles/operation jobs.
- Added 10 native JVM/JUnit crypto checks for successful roundtrip, wrong passphrase, tampering, truncated and oversized payloads.
- Separate alpha package `dev.reminderos.alpha.v03c` protects older package installations. **Earlier V0.2/V0.3a/V0.3b records cannot automatically transfer into V0.3c**; older apps have no backup export.
- [V0.3c scope and limitations](ANDROID_ALPHA_V0_3C.md). CI build and native device roundtrip not yet verified.

## Verified V0.3c build — 2026-10-23
- [GitHub Actions #39899394481](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39899394481) passed all four jobs including native Android crypto/calendar tests, SQLite contracts and iOS simulator regression compilation.
- Android debug APK `android-reminder-v03c-debug`, source `85cc0891e33163784642128b925df428d815ae82`, **938,098 bytes**, SHA-256 `13d14c131ecaf13b8b141dbf5fcd57210ea2c4d2f7902bca9e8cd9d5a0e48f73`.
- Added in-app encrypted export, integrity-check-only Verify Backup, and transactional empty-app restore; crypto/password tests are JVM-verified, native file picker and restore roundtrip **not device-tested**.
- Separate package prevents overwriting earlier alpha apps, but cannot import their private data until a compatible export/upgrader exists.
- Outstanding: first device export→verify→restore QA, durable release signing, scheduling reliability, accurate recurrence on travel and full iOS reminder parity.

## V0.3d backup and signing reliability — 2026-10-23
- Added Android instrumentation tests using actual Android SQLite for encrypted export → clean restore, preserved records and user actions, no native-handle import, wrong-passphrase rejection and no overwrite.
- New Android emulator CI workflow runs isolated backup/restore tests on Android 15 API 35; passing evidence must be checked before claiming device validation.
- Added optional private signing configuration and separate manual trusted-main/environment GitHub Actions workflow. Signing keys are **not stored in the repository** and have not been configured by the user; this does **not** yet establish stable update signing.
- V0.3d installs as a separate package `dev.reminderos.alpha.v03d` to preserve V0.3c and older app data. V0.3c encrypted backup files may be used for V0.3d clean restore after QA.
- See [V0.3d QA and signing guidance](ANDROID_ALPHA_V0_3D.md). No physical device backup/file-picker or OS alarm-delivery guarantee yet.

## V0.3d verified outcomes — 2026-10-23
- [Emulator QA #39902092344](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39902092344): 3 instrumentation tests passed on Android 15 (native Android SQLite encrypted backup roundtrip, incorrect passphrase protection, no overwrite on populated app).
- [Standard CI #39902092246](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39902092246): all 4 jobs passed for Android APK/unit tests, shared JVM, portable SQLite, iOS Simulator preview.
- Downloadable `android-reminder-v03d-debug` APK **938,126 bytes**, SHA-256 `ec595e3c69d5f99938161cdc2c037b9bd5004bc99d7528793a4e8f8be57c5b90`. This is **not** an alpha stable signed release.
- Added secret-controlled release signing machinery; private signing secrets not provided/configured, so no updatable signed release yet. Prior debug-signed versions still use separate app IDs and their data is not automatically migrated.
- Physical-device system file-picker backup/export, notification timing, reboot and OS/OEM restriction tests remain release blockers. Cross-platform/iOS feature parity incomplete.

## V0.3e scheduling reliability — 2026-10-23
- Found duplicate SQLite REGISTER job creation after repeated manual repair or recovery; normal resume should also distinguish already-applied from queued work.
- Fixed outbox reconciliation to check current occurrence/revision/due and existing queued or applied jobs, with explicit force semantics for reboot/manual recovery.
- Fixed routine Pause/Archive notification dismissal to include all pending occurrences, not just the handful shown in the UI.
- Added status counts: active future reminders, overdue unresolved reminders, queued scheduling jobs and failed scheduler jobs (not proof of OS delivery).
- Added seven Android emulator SQLite regression tests for idempotency, stale notification actions, per-occurrence series state, pause/archive suppression and reboot recovery.
- V0.3e uses separate test package `dev.reminderos.alpha.v03e` to preserve V0.3d and older app data; no automatic cross-package import.
- [V0.3e release scope and QA](ANDROID_ALPHA_V0_3E.md). Build and physical-device delivery still require verification.

## V0.3e verified CI results — 2026-10-23
- [Actions emulator run #39903891862](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39903891862): **10 Android instrumentation tests passed** (native SQLite recovery, database guardrails, repeat/repair idempotency, stale-action revisions and routine pause).
- [Standard CI #39903891795](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39903891795): Android build/unit checks, shared JVM, SQLite contract and iOS Simulator builds all passed.
- APK artifact `android-reminder-v03e-debug`, source `7b7812ac2a6c34a487ae56a2963632ba65999be3`, **940,398 bytes**, SHA-256 `b9df0634de2688534607601595753930fe76db3f6deccf1d3ed258f683751270`, archive integrity verified.
- Debug test package `dev.reminderos.alpha.v03e` is separate from older installations. Signed release workflow has no owner secrets and no stable-signing claim.
- Physical-phone background delivery, reboot/exact-alarm behaviour and file-picker flows still require QA; not a stable release.

## iOS native persistent reminders V0.4a — 2026-10-23
- Replaced single 60-second iOS preview UI with locally persisted, multi-reminder one-time SwiftUI flow: create reminders, display pending/overdue/Done/Skipped, explicit Done/Skip/Snooze 10m, archive and event history.
- JSON V1 snapshot written atomically in iOS Application Support with post-first-unlock file protection. Corrupt/unsupported store opens read-only, not destructive reset.
- Native UNUserNotificationCenter action categories registered at launch; background Done/Snooze/Skip action callbacks pass stable UUID + revision to protect against stale taps.
- Best-effort pending notification reconciliation on app launch/foreground/actions, with maximum 48 outstanding alerts. Unknown/ignored reminders remain unresolved.
- Separate iOS alpha bundle ID `dev.reminderos.iosalpha`; prior simulator-only preview remains logically separate.
- [iOS V0.4a scope and limitations](IOS_PERSISTENT_ALPHA_V0_4A.md). This is NOT full Android parity: no recurrence, cross-platform database bridge, encrypted backups, iPhone signed app or physical-device evidence. Native iOS CI pending.

## iOS V0.4b recurrence — 2026-10-23
- iOS V0.4a [run #39931842373](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39931842373) passed all four standard CI jobs including **5 iOS persistence XCTests**.
- Added native calendar recurrence for daily, selected-weekday and monthly schedules, original local date slot IDs and independent occurrence outcomes.
- Added SwiftUI routine creation, weekday selection, Pause/Resume/Archive and routine history.
- Evolved persisted iOS JSON snapshot V1→V2 read compatibility; write atomically. Added 10 calendar/recurrence/migration XCTest cases.
- Rolling bounded seven-future-occurrences per routine + 30-day catch-up; notification limit 48. **Unattended perpetual delivery beyond the preloaded horizon is not guaranteed on iOS.**
- [V0.4b scope, caveats and verification](IOS_RECURRENCE_ALPHA_V0_4B.md). This is native Swift, not a completed KMP cross-platform shared store, and not a signed physical-iPhone app.

## V0.4b verified simulator milestone — 2026-10-23
- [CI run #39934657157](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39934657157) passed all 4 jobs including **15/15 iOS simulator tests**: ten new recurrence/V1 snapshot cases plus five earlier one-time persistence tests.
- Xcode-generated iOS simulator app available as `ios-reminders-persistent-alpha-simulator` workflow artifact; not a signed iPhone IPA.
- Android V0.3e remains unchanged and compiled in the same CI run.
- iOS is still Swift native rather than the shared KMP database; rolling pending notifications may run out without app execution, so perpetual unattended scheduling is not established.
- Next: recurrence definition editing, reliable iOS native notification strategy, cross-platform persistence, iOS device-signing and physical-device QA.

## V0.5a shared recurrence parity work — 2026-10-23
- Reused the existing KMP Gregorian date/time/recurrence implementation instead of creating another calendar engine.
- Added validated `PortableRecurrenceSpec` with cadence, ISO weekday masks, stable logical (series ID, YYYY-MM-DD) occurrence keys and revision/outcome action guards.
- Added 10 Kotlin common tests and five matching Android native/JUnit and iOS Swift/XCTest date-key cases to detect differing native calendar results before migrating runtime code.
- Existing iOS V0.4b simulator [run #39934657157](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39934657157) passed 15/15 tests; new parity branch CI must be verified separately.
- [Contract details and limitations](SHARED_RECURRENCE_PARITY_V05A.md): platform apps still use their native recurrence engines and separate SQLite/JSON stores. This is a compatibility/test milestone, not completed shared persistence or runtime binding.

## V0.5a CI confirmation — 2026-10-23
- [Run #39936693872](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39936693872): all 4 jobs passed. 20/20 iOS XCTest passed (5 new parity cases), Android native JUnit and shared Kotlin JVM tests passed; SQLite contract passed.
- This is a verified **portable recurrence model/compatibility milestone**, not actual Android/iOS runtime coupling, merged databases, stable signed updates or physical device certification.

## V0.5b actual KMP runtime integration — 2026-10-23
- Android V0.5b `RecurrenceSchedule.next` now executes the same `PortableRecurrenceSpec`/date-key Kotlin source used in `:shared` (AGP 9 built-in Kotlin source roots). Native Calendar resolves local timezone/DST instant.
- iOS `IOSCalendarRecurrence.next` calls `RecurrenceBridge` from a compiled, linked static `ReminderCore` Kotlin/Native framework. Swift Foundation.Calendar retains timezone/DST conversion and UNUserNotificationCenter.
- [GitHub Actions #39939787871](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39939787871): all four CI jobs passed; **20/20 iOS Simulator XCTest passed**, Android `testDebugUnitTest assembleDebug`, KMP JVM and SQLite contracts passed.
- [Android emulator #39939788038](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39939788038): 10/10 instrumented SQLite recovery tests passed.
- Extracted `android-reminder-v05b-debug` APK from source SHA `4b10d9e5995e9292407af0bab40fdd5ad9c01663`, **976,004 bytes**, SHA-256 `c51bdc801226cd809e615075fd6e85f5d1b0613acc7d78ce4913b66931247d8c`. Separate app ID preserves older installations. No automatic data import.
- See [runtime integration V0.5b report](KMP_RUNTIME_INTEGRATION_V05B.md). This does not yet unify on-device SQLite/JSON persistence, sign stable updates, produce an iPhone IPA, or guarantee perpetual unattended iOS notifications.

## V0.5c shared storage preparation — 2026-10-23
- Added `PortableStorageSnapshot` (versioned rules, independent reminder outcomes and audit events) in shared Kotlin, with no OS schedule job transport, plus a referential-integrity/uniqueness validator.
- Added native Android SQLite v3 **read-only** snapshot inspection, preserving hidden archived and recurring entries and audit history; normalizes stale nonweekly weekday masks in-memory.
- Added iOS JSON V1/V2 in-memory read-only inspection, calling Kotlin/Native `StorageMigrationBridge` for field validation and Swift for cross-row references; no automatic file conversion.
- Both apps expose an Inspect data migration readiness UI, which changes neither source reminders nor OS registrations.
- Added 8 shared contract unit tests, 3 Android emulator data-inspection tests and 3 iOS XCTest; CI results pending.
- [V0.5c scope](PORTABLE_STORAGE_V05C.md): **shared model + read-only migration checks only**, NOT shared production persistence, import/export parity, full migrator or rollback yet.

## V0.5c verified read-only migration gate — 2026-10-23
- [Full CI #39943474845](https://github.com/rayadinave98-ship-it/Reminder-and-Personal-OS/actions/runs/39943474845) passed all four jobs. **23/23 iOS simulator tests passed**; Android native/JUnit and shared Kotlin tests plus SQLite contracts passed.
- [Emulator QA #39943474863](https://github.com/rayadinave98-ship-it/Reminder-and-Personal-OS/actions/runs/39943474863) passed **13/13 Android SQLite tests** (including three new no-write inspection regressions).
- V0.5c Android APK artifact `android-reminder-v05c-debug`, **987,640 bytes**, SHA-256 `106ea142d6697cc36a2ad54c646d27b856d4ace2b6a300ef576a5ce7a33e4869`, source commit `3378c1794ddb1fdfcfa5e475fa1d28a094b8819e`; verified manifest and DEX.
- Separate `dev.reminderos.alpha.v05c` package protects previous installs. No automatic migration, portable iOS backup format, stable signing, or new Android/iOS data sync.
- Next phase must implement cross-platform portable write/import and tested backups/atomic rollback before changing the underlying databases.

## V0.5d portable backup export and verification — 2026-10-23
- Added RPOSB2 authenticated portable backup profile with AES-256-GCM and 210k-round PBKDF2-HMAC-SHA256, strict 12–128 printable ASCII passphrase and canonical version-1 reminder/series/event JSON.
- Android provides encrypted portable export/verification through system document picker, in addition to unchanged legacy RPOSB1 Android encrypted export and empty-store restore.
- iOS provides password-protected encrypted export to Files and read-only portable backup verification using CryptoKit+CommonCrypto; it never overwrites local JSON.
- The same independent Python-generated encrypted backup test vector is decrypted by both Android and iOS; both validate incorrect passphrases and tampered ciphertext.
- [Four-job CI #39947028584](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39947028584) passed including **27/27 iOS simulator tests**.
- [Android emulator #39947028457](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39947028457) passed **16/16 native tests**.
- Android V0.5d debug artifact `android-reminder-v05d-debug`: **992,444 bytes**, SHA-256 `c90f7bc83200f7ffc3adda5a80f14dc978639ad8e881dc7f9cf64e1510006bbd`, source commit `0bf3276e2a0c40fb5f00c888050031d1a4d22fdf`. Separate `dev.reminderos.alpha.v05d` package protects prior data.
- **CRITICAL LIMIT:** No RPOSB2 import, transactional rollback or automatic cross-platform migration; data is EXPORT + VERIFY only. [V0.5d scope and wire format](PORTABLE_ENCRYPTED_BACKUP_V05D.md).
