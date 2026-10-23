# Android V0.3a — Reminder Management Test Build

**Stage:** Android debug APK compiled successfully in CI; real-device behaviour unverified (developer alpha).
**Scope:** Edit, pause/resume, archive, event history, flexible snooze and manual alert repair.
**Built on:** Android Alpha v0.2's one-time reminders, native AlarmManager and local SQLite.

## Safeguarding the existing alpha

v0.2 used a CI-generated Android debug signing key. An APK from a different GitHub runner may not share that certificate, so an in-place update could fail. **Do not uninstall v0.2 to install this test build**: it has no backup/export yet, so uninstalling deletes user reminders.

V0.3a intentionally uses **a separate Android application ID** `dev.reminderos.alpha.v03`. Both apps can coexist without touching v0.2's stored data. **V0.3a does not import the records stored by the separately installed v0.2 app.** Its database migration path is for a compatible, same-package v1 database if present in future production upgrade scenarios. We still need a stable signing and portable backup/export strategy.

## Functionality included

- Create multiple local one-time reminders with title and date/time picker.
- **Edit** pending reminder title/time; updates revision and cancels/overwrites prior native alert.
- **Pause/Resume** pending reminder; resume requires a future due time. For an overdue paused reminder, edit due time first.
- **Archive** a reminder after confirmation: hides it from the list, retains events and cancels remaining native registration.
- **Snooze** from the app for 5, 10, 30 or 60 minutes; notification still offers 10m.
- **History** shows recent stored audit events, including alarm callback attempts. An OS alarm callback is not proof of viewing/completion.
- **Check & repair future alerts** manually re-registers current active upcoming alerts with stable native IDs after permission/system issues.
- **SQLite schema v2** adds paused and archived columns with non-destructive ALTER TABLE upgrade logic.
- Existing old event/outbox history is retained across a same-package v1->v2 schema migration.

## Tests and build

- Python sqlite3 contract tests extract **the exact ALTER TABLE statements from Kotlin** and validate v1 data/action/outbox preservation, new defaults and CHECK constraints. This is not Android emulator migration instrumentation.
- Existing 25 SQLite/outbox reference tests run alongside the new migration checks in GitHub Actions.
- **PASS:** [GitHub Actions run #39895650894](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39895650894) completed Android APK, JVM, SQLite/migration and iOS Simulator CI jobs successfully.
- Device behaviour (Doze, force stop, alarm permissions, boot handling, editing near an alarm firing) is not automatically validated by CI build.

## Known gaps

- **One-time reminders only.** Calendar-aware daily/weekly/monthly/custom recurrence requires per-occurrence history and scheduling to avoid losing outcomes when alerts go unanswered. That is V0.3b work, not implemented here.
- Android-specific SQLiteOpenHelper remains separate from the shared KMP domain store; production shared persistence is future work.
- No stable APK signing, account sync, encrypted backup, safe external export, true production recurrence, app analytics, smart insights, or designed UI yet.
- Archiving retains local history, but there is not yet an archive browse/restore screen.
- Diagnostics show attempted OS alarm callbacks only. We cannot prove a user saw a notification.
- Database mutation and native OS scheduling calls cannot share one transaction; operation queue and deterministic native IDs mitigate but do not remove crash windows.
- The independent V0.3a app doesn't take over V0.2 reminder schedules. If both are installed, their active alarms may both fire; this is deliberate parallel testing.

## Minimum device QA

1. Install V0.3a beside V0.2 without uninstalling the latter; verify original v0.2 reminders are still present in v0.2.
2. Create 2 future reminders in V0.3a. Edit one title/due time, confirm only new time alerts.
3. Pause another, wait past due and check that no new alarm appears; edit due time then resume.
4. Test Done, Skip, Snooze 5m, Snooze 30m, and notification Snooze 10m; inspect history and reopened app state.
5. Archive a pending reminder, verify cancellation and that it disappears from the main list.
6. Reboot with future active reminders; check restoration without duplicate alerts.
7. Toggle notification permission and exact alarm eligibility; open the app and tap Check & repair future alerts.
8. Observe delivery time on locked screen; record device/OS, permission state and battery mode without posting private reminder titles to public GitHub.

## Roadmap

- **V0.3a:** Reliable management mechanics and safe migration (this alpha build).
- **V0.3b:** Per-occurrence recurrence engine and proper audit/unknown outcomes across missed daily/weekly/monthly alerts.
- **V0.3c:** Stable signing, export/restore and expanded device reliability QA.
- **Beyond:** Equivalent iOS persistent reminder app and shared KMP persistence, tracking and analytics.

All future releases must explicitly distinguish code committed, CI compiled, and physical-device tested.

## Verified V0.3a artifact — 2026-10-23
- [GitHub Actions build #39895650894](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39895650894): **all four CI jobs passed**.
- SQLite database/outbox and migration checks: **29 Python tests passed**.
- Artifact: `android-reminder-v03a-debug` containing `ReminderAndroidPreview-debug.apk`, from commit `28991b1a701b40715272b2284d2401488d287c6d`.
- Extracted APK: **909,102 bytes**, SHA-256 `b5f86f7b9ac69597c5dc1d84ae0364dcd0b0b1e74ea333aea1c635c912855b8c`; inspected manifest/dex presence.
- Android app ID is `dev.reminderos.alpha.v03`, separate from V0.2's `dev.reminderos.alpha`. Installing it must not require uninstalling V0.2.
- Build/check success does not demonstrate actual reminder notification delivery or native Android schema migration on a physical device. Those remain pending.
