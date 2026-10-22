# Reminder Behaviour Contract — V1 (Draft)

**Status:** PROPOSED; must be tested on both Android and iOS.  
**Scope:** one-time and calendar-based recurring local reminders, with controlled snooze/follow-up.

## 1. Entities and separations

- **ReminderDefinition:** stable definition ID, title, optional notes, status active/paused/archived, priority, privacy settings, follow-up policy.
- **ScheduleRule:** local date/time, recurrence, schedule time zone strategy, start/end, exceptions.
- **Occurrence:** unique identity derived from rule revision and logical due slot; original due time, current schedule registration and user outcome.
- **AlertAttempt:** request to schedule and/or OS callback, including timestamp, adapter result, error; never proof the user saw it.
- **UserAction:** explicit Done, Skip, Snooze, Acknowledge or Edit recorded with action ID, actor and timestamp.
- **Reconciliation:** comparing intended occurrences to OS-registered schedules and correcting valid discrepancies.

Store user outcome separately from operational scheduler status. Example: an occurrence can be past due, have an OS trigger callback, and still have UNKNOWN user outcome.

## 2. User outcomes and transitions

| Current user outcome | Action | Result | Notes |
|---|---|---|---|
| UNKNOWN | Done | DONE | Record actual completion time, optionally backdated by user |
| UNKNOWN | Skip | SKIPPED | Optional reason |
| UNKNOWN | Snooze | UNKNOWN + snooze schedule | Append action; original due timestamp stays |
| UNKNOWN | Acknowledge / dismiss | UNKNOWN + acknowledged flag | Recognition does not imply Done |
| UNKNOWN | No action after due | UNKNOWN + overdue status | May qualify for opted-in follow-up |
| DONE | Reopen / correct | UNKNOWN (correction event) | Never silently overwrite history |
| SKIPPED | Reopen / correct | UNKNOWN (correction event) | Same correction/audit rules |
| DONE/SKIPPED | Duplicate notification action | No-op | Idempotent against occurrence + action token |
| Any | Pause reminder definition | Existing outcome retained | Cancel future eligible alerts; keep history |
| Any | Delete reminder definition | Archive or soft-delete with explicit user choice | Do not silently erase historical records |

No-response is computed from lack of user response, not an intentional choice. If user explicitly logs a tracker activity without using the reminder notification, the app can offer to link it to the occurrence (never assume equivalence).

## 3. Time semantics

### Scheduling modes
- **Wall-clock local:** e.g., 08:00 in the user's current local time zone, adapting when the device zone changes.
- **Fixed-zone wall-clock:** e.g., 09:00 Asia/Kolkata even when traveling.
- **Absolute instant:** one-time exact moment represented as UTC instant.

Support one-time, daily, selected weekdays/weekly days, and monthly by chosen date in V1. Avoid silently changing semantics when editing. For the 29th–31st in short months, propose **skip nonexistent dates** by default with a clear configurable option later. Confirm this before implementation.

Store IANA time zone IDs, source local wall time, UTC resolution, recurrence revision and computed next occurrence.

### DST and clock changes
- Nonexistent local clock time (spring-forward): proposed default is earliest valid time after the gap on that date; flag in the occurrence event.
- Ambiguous local clock time (fall-back): proposed default is the earlier matching instant once, unless a future product setting changes this.
- Device manual time changes: reconcile future times; historical events must not be rewritten.
- Late OS callbacks: record actual callback time separately; never shift original planned due time.
- Use calendar-aware recurrence arithmetic, not fixed 24-hour durations for daily rules.

These policies are test cases and remain proposals until approved.

## 4. Native scheduling contract

**Android adapter**
- Determine notification permission and exact-alarm eligibility where applicable.
- Schedule time-sensitive occurrences using an allowed AlarmManager path; use WorkManager for deferrable maintenance, not exact time-critical delivery.
- Register reboot/time/timezone/permission-change reconciliation using supported APIs.
- Respect battery, OEM restrictions and OS rules. Avoid unjustified always-on foreground services.

**iOS adapter**
- Use UNUserNotificationCenter to request authorization and schedule local notifications/actions.
- Account for pending request limits, app lifecycle constraints and user Focus settings.
- Register a bounded rolling window of upcoming notifications, with capability limitations explicitly surfaced. Do not assume arbitrary future background scheduling.
- Never promise continuously running background services or exact callback reporting for a displayed notification.

**Shared scheduler port**
- `schedule(occurrence)`, `cancel(occurrence)`, `reconcile()`, `getCapabilities()`.
- Adapters return accepted/rejected/unknown scheduling status and diagnostics.
- Schedule only a bounded horizon, rebuild on supported wake/launch/interaction opportunities.
- Stable OS identifiers and deduplication across rule revisions and restoration.

An OS 'scheduled' acknowledgement is **not** guaranteed user delivery, visual display, audible alert, or user attention.

## 5. V1 follow-up policy

- **Default:** one initial notification; no automatic repeated follow-up.
- **Optional:** user can enable at most one follow-up after configurable delay on a reminder, subject to quiet hours and platform capability.
- **Snooze:** user's explicit request creates a next alert at an allowed later time; snoozing does not imply task completion and is recorded distinctly.
- **Capping:** one current follow-up per occurrence; an action cancels irrelevant outstanding notifications where possible.
- **Quiet hours:** user-selected local range; quiet hours suppress or defer eligible reminders only in the way user configured. Do not silently defer urgent reminders. Require a clear preview when an existing schedule conflicts.
- **Critical-like alerts:** never claim special entitlement, override Focus, or bypass OS permissions without actual approved entitlement. No emergency-grade guarantee.

## 6. Recovery and duplicates

1. Definition and intended occurrence persist before platform registration.
2. Reconciler works from local database as source of truth; OS registrations are projections.
3. On startup, boot (Android), time-zone change or other eligible resume events, compare next N intended schedules to registrations; repair only mismatches.
4. Each occurrence and action has immutable unique IDs; transactional processing prevents duplicate completion, duplicate snooze and duplicate follow-up.
5. If scheduling fails, persist failure reason and show capability warning, retry only on supported events.
6. Import/export restores user definitions/history and safely regenerates OS registrations; invalidates outdated registration IDs rather than copying them between devices.

## 7. Scenario acceptance examples

| Scenario | Required observable result |
|---|---|
| User marks Done twice | Exactly one completion outcome, repeated action ignored |
| User dismisses notification | Outcome remains UNKNOWN, acknowledged/dismissed only if observable |
| Alarm occurs while device offline | On-device alert attempt requires no network |
| Permission denied | Reminder still saved; clear delivery limitation shown |
| iOS background/terminated | Pre-scheduled supported local alert works where OS permits; no unsupported background assertion |
| Android reboot | Future eligible reminders reconciled via supported receiver; no duplicate Done |
| Change 08:00 to 09:00 | Old OS registration canceled/invalidated and correct new time registered |
| Daily event across DST | Occurrence is once per intended local calendar day per chosen gap/fold policy |
| Snooze then Done | Pending snoozed alert canceled where possible, original occurrence Done |
| Routine reminder ignored | UNKNOWN and overdue, not marked skipped or completed |
| Restore backup | Same logical occurrences, no double registration after reconciliation |
| OS callback late by 10m | Record late callback; do not falsify due time or completion |

## 8. Open implementation decisions

- Exact target Android/iOS versions and adapter behaviour matrix.
- How notification interaction events can be observed under each OS.
- Final UI labels for Unknown, Acknowledged and Overdue.
- Handling Android force-stop and iOS terminated/background limits in diagnostics.
- Final quiet-hours conflict rule and explicit user choice.
- Notifications window size and reconciliation cadence per platform.

All test cases need deterministic unit tests where possible and physical-device verification. **This specification does not claim native OS execution has been tested.**
