# V1 Quality and Release Gates — Draft

**Stage:** Test specification only; zero tests executed as of 2026-10-22.

## Test pyramid

### Pure shared unit tests (required)
- One-time/daily/weekly/monthly recurrence generation in multiple IANA zones.
- Calendar edge cases: short months, leap years, DST gaps/overlaps, travel time zones.
- Occurrence identity and rule-revision changes.
- State transitions: Done, Skip, Snooze, Acknowledge, Unknown; invalid transitions.
- Idempotency for duplicate callbacks, duplicate user actions and import.
- No-response and follow-up policy: default no repeat, optional one capped repeat.
- Data validation for tracker type, unit, numeric range and backdated entry.
- Analytics denominator/numerator consistency, zero denominators, corrections and missing-data thresholds.
- Privacy: no personal content in diagnostic schema.
- Backup codec format/schema version and tamper/corruption detection.

### Android instrumentation/device tests (required)
- Fresh install, notification permission allowed/denied/revoked.
- Exact alarm eligibility allowed/denied and fallback behaviour.
- Background, app swiped away, Doze/battery saving, device reboot, time zone/time change.
- Action shortcuts Done/Snooze/Skip and rapid repeated taps.
- Alarm edit/cancel/recreate while OS has a pending registration.
- Restore backup then reopen/reboot, with no duplicate future alerts.
- Battery/foreground-service compliance; test supported OEM restrictions.

### iOS device tests (required)
- Permission allowed/denied/provisional as applicable; Focus/silent-state documentation.
- Local scheduled notifications while foreground/background/app not running where supported.
- Notification category actions, tapped notification and dismissed notification (only where observable).
- Registration-window handling near system pending limits, rescheduling on allowed launch opportunities.
- Time-zone/time changes, device restart, repeating local notifications and quiet-hour policies.
- Import/export and duplicate-registration recovery.
- VoiceOver, Dynamic Type, reduced motion and degraded permission states.

### End-to-end flows (required)
1. Create a one-time reminder → wait → receive platform notification → Done → History → Insights.
2. Create recurring daily reminder → edit time → complete one occurrence → next still scheduled.
3. Snooze → receive re-alert → Skip → analytics classification consistent.
4. Missed/ignored reminder → overdue UNKNOWN, not false Skipped or Done.
5. Log independent numeric activity → graph → correct value → graph updates.
6. Export backup → corrupt/incorrect password failure safe → successful restore → schedules repaired.
7. Airplane mode → local reminder, tracking and reports still work.
8. Permission denied/revoked → capability message and fallback, no misleading green 'healthy' state.

## Release blockers

- Any corruption/loss of saved reminders or tracker data without recoverable explanation.
- Duplicate user-completion records from one action.
- Claiming notification presentation/user attention from an OS schedule request.
- Undocumented background/reboot scheduling failures under supported conditions.
- Broken migration/restore or security exposure of activity content.
- Core flow inaccessible on a supported platform/device.
- Claims of parity where native iOS/Android behaviour differs.

## Measurement and evidence

Define physical-device matrix after minimum OS target selection. For each device: record OS version, model, battery restrictions, permission configuration, exact/inexact eligibility, scheduled instant, observed trigger time, jitter, failures, and build SHA. Summarize success/failure by tested scenario and avoid extrapolating to every phone.

CI must run shared unit tests, static analysis, and platform builds where macOS tooling is available. Repository status must state if iOS build/test could not be executed.

## Exit criteria

All P0 requirements have test cases, automated shared tests pass, Android/iOS builds are verified, critical device scenarios are exercised and logged, backup/restore is integrity-tested, and there are no open release-blocking defects. Test evidence links must be attached to the status report.

**This file is a release checklist; it is not evidence that any tests or builds were performed.**
