# Architecture Proposal — V0.1

**Date:** 2026-10-22  
**State:** PROPOSED, not a final implementation decision

## Design philosophy
Reliability before intelligence; local-first by default; meaningful insights rather than vanity metrics; accessibility and restrained UI; user consent for sensitive automation; modular boundaries and traceable behaviour.

## Platform strategy
Target Android and iOS in the initial product scope. Evaluate **Kotlin Multiplatform (KMP)** for shared business logic, with **Compose Multiplatform** as a candidate UI framework. Build native adapters for operating-system capabilities. Validate iOS interaction fidelity, build tooling, binary size, and device test coverage before finalizing the UI-sharing strategy.

### Native platform boundaries
- Android: AlarmManager or appropriate platform scheduler for precise time-sensitive reminders where available/allowed; WorkManager for deferrable, reliable background operations; Android notifications, runtime permissions, boot/time-zone reconciliation.
- iOS: UNUserNotificationCenter scheduling and actions; background processing only where the system permits; account for notification and pending-request limits and Focus/silencing behaviour.
- Explicitly design for powered-off devices, force-stop cases, restricted background execution and revoked permissions: delivery cannot be guaranteed under all OS conditions.

## Logical modules
- `app/android`: Android shell, integrations, navigation entry.
- `app/ios`: iOS shell, platform integration and signing.
- `shared/core`: models, time handling, recurrence, validation.
- `shared/reminders`: recurrence rules, occurrences, follow-up policy, scheduling port.
- `shared/tracking`: activities, measurements, goals, user confirmations.
- `shared/analytics`: deterministic metrics, aggregation and comparisons.
- `shared/insights`: auditable observations, evidence and suggestions.
- `shared/data`: repositories, database and migration strategy.
- `shared/ui`: common Compose UI if validated for both platforms.

Directory structure above is **proposed**, not yet created.

## Data model (candidate)
- ReminderDefinition, ScheduleRule, ScheduledOccurrence
- DeliveryAttempt (attempted vs confirmed delivery must not be conflated)
- UserResponse (done, snoozed, skipped, acknowledged)
- ActivityLog, Measurement, Goal
- EventHistory (append-only events where valuable)
- Insight (finding, evidence, timeframe, confidence / limits)
- PermissionAndReliabilityDiagnostic

Never infer activity completion from notification delivery or app launch. Track unknown/no-response as unknown.

## Delivery flow
1. Validate reminder/schedule.
2. Persist intended occurrence and scheduling state transactionally.
3. Register next OS alarm/notification through platform adapter.
4. On callback, record trigger attempt and eligible interaction.
5. Process Done/Snooze/Skip idempotently.
6. Reschedule recurrence and reconcile after restarts/time changes.
7. Produce analytic aggregates from verified event and activity history.

## Analytics layers
1. Offline calculations: completion rate, punctuality, streaks (with defined semantics), trend comparisons.
2. Rule-based pattern detection with evidence traces and safeguards against tiny sample sizes.
3. Optional AI interface grounded in local analysis; no unsupported health diagnoses or causal claims.

## Security and backups
Local-first storage; least-privilege permissions; protected handling of sensitive personal data; encrypted portable backups with integrity verification and versioned migrations. Evaluate storage and key-handling options separately for both OSes. Cloud/sync and AI processing must be optional and explicit.

## Test and release gates
- Unit/property tests for recurrence, time zone and DST logic.
- Data consistency and idempotency tests.
- Permission-denial/revocation and notification actions.
- Android reboot/device sleep/manufacturer testing.
- iOS foreground/background/terminated/local-notification testing.
- Restore, database migration and corruption-recovery tests.
- Accessibility and performance on physical devices.
- Clearly report OS-level limitations; do not promise universal exact delivery.

## Related draft specifications
- [V1 PRD](PRODUCT_REQUIREMENTS_V1.md)
- [Reminder Behaviour Contract](REMINDER_BEHAVIOR_V1.md)
- [Data / Analytics Contract](DATA_ANALYTICS_CONTRACT_V1.md)
- [V1 Quality Gates](QUALITY_GATES_V1.md)
- [Calendar/Reconciliation Spike](CALENDAR_RECONCILIATION_SPIKE.md) — JVM-only code and tests
- [Native Platform Validation](NATIVE_PLATFORM_VALIDATION.md) — build scaffold and scheduler port
- [Native Notification Preview](NATIVE_NOTIFICATION_PREVIEW.md) — single-alert platform app experiments

## Open decisions
1. Full PRD and concrete reminder types.
2. Compose Multiplatform UI vs selective native SwiftUI UI.
3. Multiplatform local persistence (Room KMP vs SQLDelight etc.).
4. Exact alarm eligibility, alert interaction, follow-up/urgency policies.
5. Supported minimum Android and iOS versions.
6. Sync/backup scope and threat model.
7. CI, macOS/Xcode access and release-signing plan.

**No code has been implemented or tested by this document.**

## Persisted scheduling intent
See [Durable Persistence Contract V1](PERSISTENCE_CONTRACT_V1.md). Store explicit user actions and native schedule attempts separately; use an idempotent outbox to reconcile non-transactional OS scheduling side effects. SQLite schema is proposed and integration-tested in a standalone Python harness; Android/iOS native database integration remains pending.
