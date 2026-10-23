# V0.5b — Kotlin shared recurrence executed by native apps

**Date:** October 23, 2026
**Status:** Verified on GitHub CI for Android and iOS Simulator. Not a stable release.

## What changed

This is the first actual runtime linkage of the portable Gregorian recurrence rules, building on [V0.5a](SHARED_RECURRENCE_PARITY_V05A.md).

### Android runtime
- Android's standalone `androidApp` Gradle build now includes the **actual** Kotlin common source directories from `shared/src/commonMain/kotlin` and `spikes/core-model/src/commonMain/kotlin`, using AGP 9's built-in `AndroidSourceSet.kotlin` directories.
- `RecurrenceSchedule.next()` now calls `PortableRecurrenceSpec` and `portableOccurrenceKeys` to select original recurring calendar dates. The Android `java.util.Calendar` adapter resolves a candidate's local wall-clock to an epoch alarm time. Missing local DST hours are skipped.
- Existing SQLite schema v3, per-occurrence action/audit records, outbox and Android notification actions are preserved. The source integration is a **temporary direct common-source bridge**, not yet a Gradle binary dependency on the KMP module.
- Developer test app ID is `dev.reminderos.alpha.v05b`, separate from all previous debug builds to protect the local data on prior installations, since build signing identity remains unstable.

### iOS runtime
- The shared KMP module now emits a static `ReminderCore` Kotlin/Native framework for `iosSimulatorArm64` and `iosArm64`.
- `RecurrenceBridge.matchesDate` exposes primitive fields suitable for Swift bridging. `IOSCalendarRecurrence.next()` **actually invokes this Kotlin object** for calendar rule selection, then Foundation.Calendar resolves the native absolute date/time including timezone/DST behavior.
- XcodeGen links the framework to both the SwiftUI app and XCTest host. CI builds arm64 simulator, then executes the iOS tests through the linked framework.
- This is **not** a signed physical-iPhone IPA. It does not provide Kotlin/Swift shared persistence, and native iOS's bounded pending-notification horizon is unchanged.

## Verified test evidence

- [Four-job CI run #39939787871](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39939787871): all jobs **passed**, including shared Kotlin JVM, SQLite contracts, Android KMP + Android `testDebugUnitTest assembleDebug`, Kotlin/Native iOS simulator framework linkage and SwiftUI/XCTest.
- iOS Simulator: **20/20 XCTest passed** (10 calendar tests, 5 cross-platform parity cases and 5 persistence regressions), 0 failures.
- [Android emulator QA #39939788038](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39939788038): **10/10 instrumented Android SQLite tests passed**, including backup, scheduler recovery, stale actions and routine pause.
- GitHub Android artifact `android-reminder-v05b-debug`, source commit `4b10d9e5995e9292407af0bab40fdd5ad9c01663`. Extracted APK **976,004 bytes**; SHA-256 `c51bdc801226cd809e615075fd6e85f5d1b0613acc7d78ce4913b66931247d8c`. APK archive integrity and manifest/DEX presence verified.
- The CI evidence is from the executable-code commit listed above; follow-up documentation-only commits do not change its runtime behavior.

## Safety and outstanding work

- **NO automatic transfer from Android V0.3e or older alpha packages.** Keep prior installations if they contain data. V0.5b may import a V0.3c+ encrypted `.rpos` backup into an empty V0.5b database, but do so only as a QA trial on separate test installation; do not delete the source installation.
- Android and iOS STILL use distinct persistent stores (SQLite v3 and local V2 JSON). No KMP shared database schema, upgrade migrator, sync service or encrypted cross-device export format on iOS exists yet.
- No production release signing, safe in-place update, iPhone installable IPA, real-device timezone travel / locked-screen alarm timing certification or sustained offline background iOS recurrence guaranteed.
- KMP recurrence model governs calendar membership only. Android Java Calendar and Swift Foundation.Calendar can differ in ambiguous fall DST folds, invalid local hours and evolving timezone database rules. Native portability QA continues to be necessary.
- Android's source-level integration should eventually be replaced by a proper Gradle dependency on the same KMP module. Avoid copying its source files into separate platform implementation forks.
- System notification firing, being visible or being ignored **never** means a reminder is DONE or SKIPPED. User outcomes stay explicit.

## Next milestones

1. Design and implement a versioned **shared local persistence schema** with adapter migrations for existing Android SQLite v3 and iOS local JSON V2, using dry-run conversion and atomic backup/recovery on error.
2. Move Android's source-level bridge to a unified Gradle KMP binary dependency without destabilizing the tested notification engine.
3. Implement iOS notification scheduling and acknowledgement policy able to maintain a realistic unattended recurrence guarantee within Apple restrictions (or disclose its limited horizon).
4. Establish secure reproducible signing and owner-managed release credentials; validate on real Android devices and physical iPhone.
5. Build independent trackers/goals and transparent offline analytics only after reliable local reminders and recoverable migrations.
