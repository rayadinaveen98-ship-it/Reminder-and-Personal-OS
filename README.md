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

See [Architecture Proposal](docs/ARCHITECTURE.md) and [Project Status](docs/PROJECT_STATUS.md).

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
Define and approve the detailed Product Requirements Document, reminder semantics and tracking/event data contract before scaffolding code.
