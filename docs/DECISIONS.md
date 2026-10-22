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

**Reminder:** Accepted product goals are not claims that code is implemented. This document is updated as choices are finalized or revised.
