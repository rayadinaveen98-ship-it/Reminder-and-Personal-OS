# Reminder and Personal OS

A cross-platform **personal reminder, activity tracking, and intelligence system** for Android and iOS.

> Status: **Discovery / architecture proposal**. This repository does **not** yet contain an application build. Product requirements and technology choices remain subject to validation before implementation.

## Product vision
Help people not just remember commitments, but record what actually happened, identify meaningful patterns, and make better decisions using evidence from their own history.

## Principles
- **Reliability first:** Reminder delivery and recovery are critical; never equate a notification with a completed task.
- **Simple outside, capable inside:** Minimal UI, powerful tracking and analytics.
- **Privacy and offline-first:** Core reminders, logs, and reports work locally without an account.
- **Explainable insights:** Show evidence and distinguish correlation from causation.
- **User control:** Automation suggests changes; users approve disruptive or sensitive actions.
- **Modular growth:** New trackers and integrations must not destabilize the reminder engine.

## Proposed platforms and architecture
- **Android and iOS from V1.**
- **Candidate shared stack:** Kotlin Multiplatform + Compose Multiplatform.
- **Native system adapters:** Android AlarmManager / platform scheduling APIs, Apple local notifications and permitted iOS background facilities.
- **Shared engines:** Reminder rules, tracking, historical events, analytics, and deterministic insight logic.
- **Storage:** Local-first database; evaluate a compatible Kotlin Multiplatform persistence solution before locking.
- **Optional future capabilities:** AI assistance, encrypted sync, and integrations.

See [Native Notification Preview](docs/NATIVE_NOTIFICATION_PREVIEW.md) (Android/iOS app shells, delivery unverified), [Native Platform Validation Plan](docs/NATIVE_PLATFORM_VALIDATION.md) (cross-platform Gradle scaffold, pending CI verification), [Calendar and Scheduler Reconciliation Spike](docs/CALENDAR_RECONCILIATION_SPIKE.md) (37 JVM checks) plus [Initial Cross-Platform Feasibility Spike](docs/TECH_FEASIBILITY_SPIKE.md) (JVM-only smoke, native targets untested) and [Architecture Proposal](docs/ARCHITECTURE.md), [V1 Product Requirements (draft)](docs/PRODUCT_REQUIREMENTS_V1.md), [Reminder Behaviour (draft)](docs/REMINDER_BEHAVIOR_V1.md), [Data & Analytics Contract (draft)](docs/DATA_ANALYTICS_CONTRACT_V1.md), [V1 Quality Gates](docs/QUALITY_GATES_V1.md) and [Project Status](docs/PROJECT_STATUS.md).

## V1 scope (provisional)
1. Reliable local reminders and configurable follow-ups.
2. Manual activity tracking, completion outcomes, history, and customizable routines.
3. Evidence-based offline analytics and actionable insights.
4. Backup/restore, diagnostics, accessibility, and physical-device testing.

## Project tracking rules
- Keep technical proposals and approved decisions documented.
- Each implementation phase should include relevant code, tests, documentation, and a Git commit or reviewed PR.
- Maintain [Project Status](docs/PROJECT_STATUS.md) and [Decision Log](docs/DECISIONS.md).
- Do not label work *implemented*, *tested*, or *released* without evidence.
- No unattended or continuous background development is implied: repository updates happen during active work sessions.

## Next step
Review and validate the draft V1 requirements; complete real Gradle KMP builds and Android/iOS scheduling and persistence feasibility checks before production scaffolding. The shared core has 65 local JVM smoke checks and Android/iOS shared-library CI compilation has passed. Android debug APK and iOS Simulator one-alert preview app **both compile successfully in CI** ([run #39807577755](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39807577755)). Native delivery still requires physical-device verification; neither preview is the final product.

### Durable storage research
A [portable SQLite event + scheduling outbox contract](docs/PERSISTENCE_CONTRACT_V1.md) is being tested. It is a reference implementation and **not yet a mobile database adapter**.

### Android alpha APK (developer test build)
Android Alpha v0.2 saves multiple one-time reminders on the device and provides Done/Snooze/Skip. [CI build #39819474781](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39819474781) passed; download the `android-reminder-alpha-debug` artifact while retained. [Scope and QA instructions](docs/ANDROID_ALPHA_V0_2.md). This is not a stable release or an iOS feature-parity build.

### V0.3a reminder management test
[Android V0.3a QA and compatibility notes](docs/ANDROID_ALPHA_V0_3A.md): editing, pause/resume, archive, event history, flexible snooze and manual repair, with additive SQLite migration. The separate app package protects existing v0.2 local data during signing-unstable developer builds; it does **not** migrate data from the parallel v0.2 install.

**Android V0.3a:** [CI build #39895650894](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39895650894) passed. Download expiring GitHub Actions artifact `android-reminder-v03a-debug`, or the APK attached in the development conversation. This side-by-side test package does not import data from the V0.2 install; physical-device QA is pending.

### Android V0.3b recurring reminders
[Recurrence rules, scope and device QA](docs/ANDROID_ALPHA_V0_3B.md). Repeating schedules use independent per-date records and rolling native alert registration; a missed alert never automatically becomes skipped or blocks the next. This remains a separate developer alpha package, with physical-device validation outstanding.

**V0.3b APK test build:** [GitHub Actions #39897461506](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39897461506) passed. Artifact `android-reminder-v03b-debug` (temporary Actions retention), SHA-256 `78de6e56c343f2fd96acfa597b43aa3c9e846cea0174f205730268d1747c58f3`. See [V0.3b release notes and device tests](docs/ANDROID_ALPHA_V0_3B.md). It does not replace existing alpha packages or migrate their records.

### Android V0.3c backup experiment
[Password-encrypted Android backup/export/import and QA](docs/ANDROID_ALPHA_V0_3C.md). Restores are accepted only into empty V0.3c installations and do not migrate separately installed older alphas; the Android notification/recurrence engine still needs physical-device reliability verification.

**Android V0.3c APK:** [Actions run #39899394481](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39899394481), artifact `android-reminder-v03c-debug`, checksum `13d14c131ecaf13b8b141dbf5fcd57210ea2c4d2f7902bca9e8cd9d5a0e48f73`. New buttons Export encrypted backup / Verify saved backup / Restore into empty app. Physical-device restore and OS notification reliability are NOT yet verified.

### Android V0.3d persistence QA
[Android V0.3d device tests and safe signing plan](docs/ANDROID_ALPHA_V0_3D.md). Android SQLite backup/restore instrumented tests added, plus a guarded/manual secret-backed signing workflow. This developer build retains a separate package ID; stable signing is **not configured** and full phone export/restore QA is pending.

**V0.3d verified QA:** [3 native Android SQLite backup/recovery tests passed on emulator](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39902092344); [all four standard CI jobs passed](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39902092246). APK `android-reminder-v03d-debug`, SHA-256 `ec595e3c69d5f99938161cdc2c037b9bd5004bc99d7528793a4e8f8be57c5b90`. Optional signed-alpha workflow requires private secrets and reviewed code on main; it has **not** issued a signed release.

### Android V0.3e scheduling reliability
[Outbox recovery fixes, Android emulator regression tests and device QA](docs/ANDROID_ALPHA_V0_3E.md). Normal app resume now avoids duplicate scheduling jobs, while explicit repair and reboot reconciliation can restore missing native alarms. Developer test build: no automatic migration from prior installed alpha apps.
