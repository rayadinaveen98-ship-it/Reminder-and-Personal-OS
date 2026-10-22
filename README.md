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
