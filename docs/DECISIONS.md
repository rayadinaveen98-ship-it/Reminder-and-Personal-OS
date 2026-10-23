# Decision Log

Statuses: **PROPOSED**, **ACCEPTED**, **SUPERSEDED**.

| ID | Decision | Status | Reason / follow-up |
|---|---|---|---|
| D-001 | New standalone Reminder and Personal OS repository | ACCEPTED | Keep separate from creator and virtual-pet apps |
| D-002 | Android **and** iOS in initial product scope | ACCEPTED | Cross-platform access from the start |
| D-003 | Reliability, privacy and offline-first behaviour | ACCEPTED (principle) | Delivery engine must not depend on network or AI |
| D-004 | Kotlin Multiplatform shared core | PROPOSED | Validate compatibility and iOS tooling |
| D-005 | Compose Multiplatform shared UI | PROPOSED | Compare platform feel/accessibility vs native UI |
| D-006 | Native reminder/notification adapters | PROPOSED | Platform OS scheduling semantics are different |
| D-007 | Event-based tracking + deterministic offline analytics | PROPOSED | Auditable evidence-based insights |
| D-008 | Optional cloud sync and AI, after reliable local V1 | PROPOSED | Reduce dependency, cost and privacy risk |
| D-009 | Document every meaningful code milestone and evidence | ACCEPTED (workflow) | Git history + project status should reflect real work |

| D-010 | V1 initial release is focused on reminders + independent trackers + basic evidence-based analytics | PROPOSED | See V1 PRD; excludes cloud account, AI agent and shared workspaces |
| D-011 | Reminder non-response is UNKNOWN, not automatically a Skip or Done | PROPOSED | Protect analytics integrity; needs sign-off and tests |
| D-012 | Default initial alert has no automatic repeat; optional single capped follow-up | PROPOSED | Avoid notification fatigue; configurable per reminder |
| D-013 | Store current state plus append-only audit-relevant event history | PROPOSED | Auditable analytics without complex event-sourcing platform |
| D-014 | Release requires Android and iOS physical-device validation | PROPOSED | OS lifecycle and notification restrictions differ |
| D-015 | Privacy-preserving encrypted backup/restore part of V1 critical quality scope | PROPOSED | Protect local-first user data |
| D-016 | Time zone / DST recurrence defaults described in reminder contract | PROPOSED | Validate UX and native scheduling before locking |

Relevant draft specifications: [PRD](PRODUCT_REQUIREMENTS_V1.md), [Reminder Contract](REMINDER_BEHAVIOR_V1.md), [Data/Analytics](DATA_ANALYTICS_CONTRACT_V1.md), [Quality Gates](QUALITY_GATES_V1.md).

| D-017 | Kotlin shared rule engine can be isolated from OS scheduling | PROVISIONALLY VALIDATED on JVM only | 16 Kotlin CLI smoke assertions passed, not an Android/iOS compile |
| D-018 | Use Room KMP or SQLDelight | PROPOSED | Official KMP Room support confirmed, but migration/encryption/device tests pending |
| D-019 | Do not lock Compose shared iOS UI without VoiceOver/performance tests | PROPOSED | Both platforms supported by framework, real-world UX needs verification |

| D-020 | Separate calendar recurrence rules from native time-zone-to-instant resolution | PROVISIONALLY VALIDATED on JVM | Leap day/weekdays/monthly-date behaviour tested; DST/native zone resolver unimplemented |
| D-021 | Native scheduling registers only desired pending alerts via reconciliation plan | PROVISIONALLY VALIDATED on JVM | Pure planner tests missing/stale/duplicate/revised alert projection |
| D-022 | Skip nonexistent monthly calendar dates instead of shifting dates | PROPOSED | This behaviour has a JVM test; still requires product approval |
| D-023 | Treat calendar/common core spikes as feasibility code, not production Android/iOS apps | ACCEPTED (reporting) | No native SDK/Xcode/device execution completed |

| D-024 | Provisional KMP build version set Kotlin 2.4.20 / AGP 9.3.1 / Gradle 9.5.0 | PROPOSED | Matched official compatibility guidance, requires successful CI builds |
| D-025 | Android KMP library in separate shared module | PROPOSED | AGP 9+ recommended structure; app shells not yet added |
| D-026 | Native scheduler gateway models capability, accepted registration and explicit failure | PROVISIONALLY JVM-VALIDATED | 12 adapter-boundary tests; does not guarantee OS presentation |
| D-027 | Do not replace native alert if cancellation failed | PROVISIONALLY JVM-VALIDATED | Avoid duplicate alarms after partial failures; requires persistent journal and retry |

| D-028 | Build native one-alert platform previews before generalizing the scheduler | PROPOSED | Low-risk validation of OS permission and trigger behaviour |
| D-029 | Native preview does not imply completion or constitute production tracking | ACCEPTED (reporting) | Keep notification emission separate from user's verified actions |
| D-030 | Android approximate fallback when exact-alarm permission is unavailable | PROPOSED | Native system may delay notification; clearly disclose limitation |
| D-031 | Initial iOS preview is SwiftUI and UNUserNotificationCenter without KMP bridge | PROPOSED | Validate platform scheduling independently, integrate shared core later |

**Reminder:** Accepted product goals are not claims that code is implemented. This document is updated as choices are finalized or revised.

## Native preview build notes — 2026-10-22
- D-032 (PROVISIONALLY ACCEPTED): Android test harness uses a separate standalone Gradle build because combining AGP 9's built-in Kotlin compiler with the experimental shared KMP module caused Kotlin metadata/classloader errors. This is temporary; a properly verified KMP-to-Android bridge is still required.
- D-033 (ACCEPTED reporting principle): A compiled debug APK is not evidence that notifications arrived reliably or that a person completed a task.

## Persistence design proposals — 2026-10-22
- D-034 (PROPOSED): Portable versioned SQLite schema stores reminder intent, occurrence outcomes, explicit action events, native-registration projections and pending OS operation outbox.
- D-035 (PROPOSED): SQLite transaction must encompass outcome/action/outbox enqueue atomically; OS call executes afterward using idempotent, stable native IDs.
- D-036 (ACCEPTED reporting principle): SQLite reference tests never constitute Android/iOS app persistence or device reliability evidence.
- D-037 (OPEN): Evaluate Room KMP stable line vs SQLDelight and confirm an actual upgrade/migration and encrypted backup strategy before production integration.

## Android alpha implementation decisions — 2026-10-22
- D-038 (TEMPORARY): Android Alpha v0.2 uses native SQLiteOpenHelper with separate app package to validate actual multiple reminder persistence and actions before full KMP database integration.
- D-039 (PROPOSED): Native one-time AlarmManager alarms use stable per-reminder PendingIntent URI identity and revision guards to avoid responding to superseded alerts.
- D-040 (ACCEPTED reporting): Passing debug APK CI is not evidence of device-level reliability; Alpha v0.2 is not suitable for critical alarms. Physical-device QA, migration and backup remain release blockers.

## Android V0.3a decisions — 2026-10-23
- D-041 (ACCEPTED alpha safety): Use a distinct developer application ID until signing is stable; never advise uninstalling a reminder app without export/backup.
- D-042 (PROVISIONAL): Paused and archived state extends SQLite v1 through additive migration without destructive table replacement.
- D-043 (PROVISIONAL): Edit/pause/archive updates are guarded by current reminder revision, cancel previous OS alerts, and preserve history.
- D-044 (NOT IMPLEMENTED): Repeating schedules require immutable logical occurrences, independent responses and no-response handling before shipping. No pretend recurrence via rewriting a single reminder row.

## Recurrence decisions — 2026-10-23
- D-045 (PROVISIONAL): Recurring schedules own immutable logical date slots with distinct occurrence IDs; each occurrence keeps an independent response and notification history.
- D-046 (PROVISIONAL): Pre-materialize seven upcoming slots per schedule and replenish after app open/alarm callback/restart; capped historical backfill, not an unbounded background process.
- D-047 (PROVISIONAL): Skip nonexistent monthly dates; use device-local calendar wall-clock recurrence, with DST and travel policy validation pending.
- D-048 (ACCEPTED alpha safety): V0.3b installs alongside prior test packages to avoid losing user data under changing debug-signing keys.

## V0.3c backup decisions — 2026-10-23
- D-049 (PROPOSED): Use a password-protected, authenticated, versioned backup container with Android's system file picker rather than broad external-storage permissions.
- D-050 (PROPOSED): Never overwrite populated reminder databases; restore into an empty app transactionally, then regenerate local OS alarms.
- D-051 (ACCEPTED ALPHA LIMIT): A separately packaged test build cannot read another package's private app data. No assertion of V0.2/V0.3a/V0.3b import without a compatible export source.
- D-052 (OPEN): Configure reproducible release signing from private GitHub secrets; never commit private keystore to the public repository.

## V0.3d decisions — 2026-10-23
- D-053 (PROVISIONAL): Require Android emulator integration tests of the actual SQLite import/export boundary in addition to pure Kotlin and Python tests.
- D-054 (SECURITY DESIGN): Do not place signing keys or credentials in the repository; signed alpha builds run only manually from trusted main with authorized environment secrets.
- D-055 (ACCEPTED LIMITATION): Debug-signed test APKs cannot update apps signed with a different certificate; preserve existing user data by separate app IDs until a verified stable signing and migration strategy exists.
- D-056 (TEST SCOPE): Emulator SQLite tests are not evidence that OS notification delivery or Android file-picker workflows are reliable on a physical phone.

## V0.3e reliability decisions — 2026-10-23
- D-057 (PROVISIONAL): Queue native REGISTER only once for the same occurrence/revision/time while PENDING/FAILED. Normal resume treats APPLIED as a valid registration; explicit repair and boot can requeue after OS state loss.
- D-058 (PROVISIONAL): Cancel notifications for all affected pending routine occurrences when pausing/archiving, not only UI-visible occurrences.
- D-059 (ACCEPTED REPORTING): Internal outbox status and OS alarm callback do not confirm notification visibility or task completion. Keep UNKNOWN user outcomes until explicit action.

## iOS V0.4a — 2026-10-23
- D-060 (TEMPORARY): Native iOS V0.4a uses a versioned atomically written app-private JSON snapshot while the cross-platform KMP database bridge is unimplemented. Incompatible/corrupt files must not be silently overwritten.
- D-061 (PROVISIONAL): iOS actionable reminder notifications have Done/Skip/Snooze 10m IDs; persist user action before OS cancellation/registration; stale revision callback is ignored.
- D-062 (LIMIT): Queue at most 48 iOS pending local notifications and transparently explain the cap. Foreground reconciliation is best effort and not a guarantee of background delivery or production-ready recurrence.

## iOS V0.4b — 2026-10-23
- D-063 (PROVISIONAL): Native iOS calendar slot materialization retains separate original-date IDs and per-slot UNKNOWN/DONE/SKIPPED outcomes; a missed date does not block later dates.
- D-064 (TEMPORARY): iOS local JSON snapshot writes V2 and reads V1 with optional recurrence/occurrence fields; moving both platforms to shared KMP persistence remains a release prerequisite.
- D-065 (ACCEPTED PRODUCT LIMIT): A rolling seven-slot schedule cannot promise indefinite background recurrence on iOS without app execution. Be explicit about horizon and pending notification caps in app and release notes.
