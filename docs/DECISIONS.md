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

**Reminder:** Accepted product goals are not claims that code is implemented. This document is updated as choices are finalized or revised.
