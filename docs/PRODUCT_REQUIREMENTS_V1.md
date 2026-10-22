# Product Requirements Document — V1 (Draft for review)

**Product:** Reminder and Personal OS  
**Date:** 2026-10-22  
**Status:** DRAFT — not approved for implementation  
**Target:** Android + iOS, offline-first  
**Owner:** Product specification; implementation not started

## 1. Product outcome

Help a person reliably remember commitments, explicitly record what happened, see useful historical patterns, and make informed adjustments. The differentiator is a trustworthy loop:

**Plan → Notify → Respond → Record → Analyze → Improve**

A notification is not proof that an activity occurred. A lack of response is not proof of failure.

### Jobs to be done

- When I plan a task or routine, let me express when it is due, how urgent it is, and what constitutes success.
- When I am busy or offline, remind me within platform constraints and let me respond in as few taps as possible.
- When I don't respond, preserve an honest unknown status and follow only the follow-up rules I elected.
- When I revisit progress, show what actually happened, not only what was scheduled.
- When patterns emerge, explain what records support them and offer adjustments I can approve.

## 2. Principles / guardrails

1. Local reminders, history, manual tracking, and deterministic analytics work offline without signup.
2. Native platform schedulers handle notification delivery; shared code owns rules and state, never assumes Android and iOS background behaviour are identical.
3. No automatic marking done, skipped or failed solely based on notification events or elapsed time.
4. Simple first-run experience; advanced options progressively disclosed.
5. Notification fatigue controls: quiet hours, per-reminder follow-up policies, caps, and easy disable.
6. User data export/backup and deletion; no raw personal records committed to public repositories.
7. Insights are explainable, source-linked, and cautious about small samples or causal claims.
8. Physical-device validation precedes reliability or release claims.

## 3. Primary users and use cases

V1 assumes a single person using one device independently; no family/team collaboration. Flexible categories may include personal care, medications (as user-entered reminders only, not dosing advice), exercise, bills, errands, learning and household tasks. The app is not a medical device, emergency alarm service, or financial advisor.

### Core workflows

**W1 — Quick reminder:** Create a one-time reminder with title, time and optional notes, priority, repeat and snooze preferences. Show a confirmation and next due time.

**W2 — Routine:** Create a recurring reminder (daily, weekdays, weekly by selected days, monthly date) with local-time or time-zone-fixed semantics. View upcoming instances and pause/resume a routine.

**W3 — Response:** Notification offers appropriate action shortcuts (Done / Snooze / Skip; capabilities vary by OS). App provides same actions in history. Acknowledge/dismiss is distinct from task completion.

**W4 — No response:** Occurrence remains pending/unknown after due time. A configured optional follow-up may fire if permissions and OS permit. No repeated alarm loops by default.

**W5 — Track:** User records an activity and optional numeric measurement, unit, note and timestamp, even without a reminder. Editing must preserve an audit trail for relevant corrections.

**W6 — Review:** Today view shows upcoming, overdue-unresolved, and recent outcomes with clear distinctions. Track shows logging and history. Insights shows evidence-based weekly/monthly summaries.

**W7 — Restore:** User exports encrypted backup, verifies integrity and restores on a compatible application version; restore never silently doubles future reminders.

## 4. V1 feature scope and prioritization

| ID | Priority | Requirement | Acceptance criteria |
|---|---|---|---|
| R-01 | P0 | Create/edit/delete/pause one-time and recurring reminders | Save and reopen preserve all settings; edits reconcile next occurrence |
| R-02 | P0 | Reliable local scheduling adapter per OS | Scheduled notifications work without network; permissions and restrictions reported accurately |
| R-03 | P0 | Response states Done, Snoozed, Skipped, Acknowledged, No response | Actions produce distinct event records; no response never silently converted to Skip |
| R-04 | P0 | Snooze and one optional follow-up policy | No duplicate active follow-ups; user can disable at any time |
| R-05 | P0 | Crash/restart/time-change reconciliation | Pending occurrences reconstructed without accidental duplicate completions |
| R-06 | P0 | Today screen, reminder editor, occurrence detail/history | All main actions usable with accessible controls; overdue and unknown states visible |
| T-01 | P0 | Custom tracker (boolean, count, numeric) | Values, units, recorded time, source and optional note saved locally |
| T-02 | P0 | Simple routines and goals linked to reminders/trackers | Progress derived from actual recordings, not notification impressions |
| T-03 | P0 | View/edit/delete records and filter history | User corrections persisted with timestamps; derived aggregates updated |
| A-01 | P0 | Offline 7/28/90-day summaries | All metrics use explicit denominators and show missing-data context |
| A-02 | P0 | Evidence-backed simple insights | Each observation includes timeframe, sample size and a link to supporting entries |
| P-01 | P0 | Permission and reliability health check | Accurate permission states and actionable OS settings guidance; no false guaranteed-delivery wording |
| P-02 | P0 | Local-first and no mandatory account | Creating, using and reviewing the app works offline |
| P-03 | P0 | Protected backup/restore and clear-all controls | Format versioned; integrity and wrong-passphrase failures handled without partial overwrite |
| U-01 | P0 | Accessibility and understandable design | Screen reader, large text, reduced-motion and contrast checks covered in device QA |
| U-02 | P0 | Notification controls | Quiet hours, reminder-specific sound/vibration options where OS permits, and quick pause |
| A-03 | P1 | Compare routine consistency across periods | Comparison reports qualify small samples |
| T-04 | P1 | Photo attachment and user-created tags | Optional, private, and excluded from V1 critical path |
| P-04 | P1 | Device-to-device transfer wizard | Optional if export/import can already serve the need |

### Explicitly out of V1

- Mandatory cloud account, real-time multi-device sync, social/community features, shared family workspaces.
- Autonomous AI making schedule changes; general-purpose AI agent; always-listening microphone.
- Medical diagnosis, treatment advice, emergency alerting or guarantees for critical medication dosing.
- Predictive ML without sufficient data; broad financial account integrations; location surveillance.
- Unbounded escalating alarms or advertising-style engagement notifications.
- Desktop/web version and smartwatch companion at initial release.

## 5. Main navigation and screens (proposed)

1. **Today:** due soon, unresolved/overdue, recent completions and quick actions. A compact reliability warning appears only when actionable.
2. **Track:** quick record, recent measurements, trackers and accessible history.
3. **Insights:** time selector; completion/response breakdown, trend charts, supported observations, data-quality notes.
4. **Library:** reminders, routines, goals, trackers, templates; search and filter.
5. **Quick Add:** persistent affordance to add reminder / log activity / tracker value.
6. **Settings:** permissions health, quiet hours, data privacy, backup/restore, accessibility and help.

The actual design system and navigation are not yet approved. Keep default screens clean and hide advanced settings progressively.

## 6. Reminder lifecycle — summary

States must not conflate *scheduled*, *OS trigger attempted*, *user saw notification*, *user acknowledged* and *real-world task completed*. Full rules in [REMINDER_BEHAVIOR_V1.md](REMINDER_BEHAVIOR_V1.md).

- Store the schedule definition and stable occurrence ID before registering with an OS scheduler.
- User Done and Skip are explicit terminal outcomes. Snooze creates a controlled next alert without editing original due time.
- Lack of user action is **unresolved/no response** and remains visible for follow-up or manual resolution.
- An OS callback is evidence of attempted execution, not guaranteed presentation or human attention.
- User-defined quiet hours and urgency determine eligible follow-ups; V1 avoids persistent urgent alert loops.
- If native capabilities or permissions are unavailable, explain the limitation and offer supported fallbacks.

## 7. Tracking and analysis

Trackers initially support check-in (yes/no/unknown), count and numeric measurement with units. Activity records may exist without reminders. Store both event timestamp and the relevant occurrence/activity timestamp if the user records something later.

Core reports:
- scheduled occurrences vs confirmed Done / Skipped / Snoozed / Unresolved;
- known-outcome completion rate and overall confirmed completion coverage;
- response lag using only confirmed user actions;
- tracker values and time-series trends;
- routine goal progress based on explicit records;
- data-quality coverage and small-sample warnings.

See [DATA_ANALYTICS_CONTRACT_V1.md](DATA_ANALYTICS_CONTRACT_V1.md) for formulas and safeguards.

## 8. Notifications, urgency and consent

Default is a single normal reminder. Only reminders explicitly configured for a follow-up may produce an extra alert, subject to cap/quiet hours and native OS constraints. Prefer one-time setup guidance rather than repeated nagging to grant permissions. Never exploit notification fatigue or shame a user for missed tasks.

For genuinely time-critical workflows, show the OS reliability limitations and do not present this app as an emergency-grade alert system.

## 9. Platform and nonfunctional requirements

- **Android:** support correct background scheduling paths, API permission gates, boot/time-zone reconciliation, Doze handling within OS policy.
- **iOS:** schedule native local notifications and supported actions; operate within Focus, authorization and pending-notification limitations; do not rely on guaranteed background refresh.
- **Shared core:** recurrence, domain states, tracker schema, analytics, validation, and error contracts.
- **Latency:** UI interactions should feel immediate; measure on a defined low/mid-tier device set before setting numeric targets.
- **Data integrity:** idempotent action processing; transaction boundaries; safe migrations; audited backup restore.
- **Security:** least-privilege permissions; protected sensitive local data; encrypted portable exports; no telemetry of personal content by default.
- **Accessibility:** talkback/voiceover semantics, dynamic text, reachable controls, motion accommodation.
- **Observability:** privacy-preserving local diagnostics with user-controlled export; no hidden upload of activity content.

Supported minimum OS versions and numeric performance budgets require build/device validation.

## 10. Release criteria

1. Cross-platform implementation for all P0 features with explicit limitation parity matrix.
2. Automated tests covering recurrence, DST, idempotency, event semantics, metrics, migrations and restore.
3. Physical-device tests for notification scheduling, permission denial/revocation, background, terminated app, reboot on Android, low battery, and quiet hours as applicable to each OS.
4. Failures and unsupported scenarios documented; no deceptive 'always works' promise.
5. Backup/restore tested with corrupted data, incorrect passphrase, duplicate schedule reconciliation and upgrade paths.
6. Screen reader and layout QA on representative Android and iOS devices.
7. At least one independent end-to-end test of Plan → Notify → Respond → Track → Analyze.
8. No release label before verifiable test evidence and sign-off.

## 11. Open questions (to resolve before code)

- Default reminder urgency/follow-up preset and whether snooze options vary by category.
- Whether check-in and measurements require a separate 'habit' model or remain unified trackers.
- Compose Multiplatform UI parity versus some SwiftUI screens; database persistence implementation.
- Minimum Android and iOS versions, macOS/Xcode access and physical iPhone testing.
- Backup passphrase recovery policy and photo attachment storage limits.
- Final V1 visual identity and product name beyond the repository working title.

## 12. Product scope discipline

No new advanced module enters V1 unless it demonstrably improves the core Plan → Notify → Respond → Track → Analyze loop and can meet both platforms' quality gates. Changes to requirements must update this PRD, the architecture/decision log, and acceptance tests. 
