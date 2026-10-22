# Calendar Recurrence & Native Scheduler Reconciliation — Spike 2

**Date:** 2026-10-22  
**Status:** Tested as Kotlin/JVM only. NOT verified as an Android/iOS application.

## Purpose

Continue the platform-independent reminder foundation created in the [initial feasibility spike](TECH_FEASIBILITY_SPIKE.md). Cross-platform reliability requires two separate concerns:

1. Decide *which local calendar dates* a reminder applies to, without confusing daily schedules with elapsed 24-hour intervals.
2. Decide *which native OS notification registrations* are missing, stale, duplicated or unchanged, without silently changing the user's outcomes.

Neither concern requires an OS alarm call; both can be implemented as a pure Kotlin common-code candidate and tested in isolation.

## Added code

- `spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/Recurrence.kt`
  - Gregorian `CalendarDate`, validation, leap years, ISO weekdays.
  - Wall-clock hour/minute, fixed-IANA-zone vs follow-device-zone policy.
  - One-time, daily, selected-weekday and monthly day-of-month recurrence rules.
  - Inclusive date-window generation and bounded results.
  - For monthly dates not present in a month (e.g. February 31), **skip that month**, never silently shift the date.
- `spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/SchedulerReconciler.kt`
  - OS-neutral desired alert vs platform registration model.
  - Pure reconcile plan: cancel stale/duplicate registrations; register missing/revised alerts; leave matching registrations untouched.
  - Explicit duplicate identity validation and deterministic order (cancel before register).
- `spikes/core-model/src/jvmSmoke/kotlin/dev/reminderos/core/RecurrenceSmoke.kt`
  - 37 smoke assertions for recurrence, leap years, invalid dates, zone-mode validation, idempotent plans and duplicate reconciliation.
- `scripts/run_recurrence_smoke.sh` repeatable executable Kotlin/JVM test.

## Test evidence

Environment: Linux, Kotlin CLI `kotlinc-jvm 1.9.0`, Java 21. Commands:

```sh
sh scripts/run_core_smoke.sh
sh scripts/run_recurrence_smoke.sh
```

Observed outputs:
```text
PASS: 16 reminder core smoke checks (Kotlin JVM only)
PASS: 37 recurrence and reconciliation JVM smoke checks
```

**Total:** 53 local JVM checks across the existing and new spike code. These are *not* Android/iOS compilation tests, persistence integration tests or notification delivery tests.

## Deliberate architecture boundaries

Calendar generation outputs a `LocalSlot(date, clock)`, not an absolute UTC epoch. To calculate a native OS firing instant, the platform adapter must:

1. Select device or fixed IANA time zone.
2. Resolve nonexistent and ambiguous local wall times according to the accepted gap/fold policy.
3. Convert that local slot to the required OS scheduling representation.
4. Persist intended occurrence and schedule metadata transactionally where possible.
5. Register/cancel native requests, record results, and reconcile after supported lifecycle events.

The native OS registration store is a projection of authoritative local reminder state; platform handles should not be moved across backup/restore or across Android/iOS.

The planner accepts one active alert intent per occurrence. It does not decide snooze/follow-up policies, priority, permission eligibility, OS delivery or what constitutes completion; these must be resolved by the higher-level reminder engine.

## Known limitations and unresolved cases

- `WallClock.fixedZoneId` requires a non-blank string but does not yet validate it as an actual IANA identifier.
- The date representation only supports Gregorian years 1900–9999; full calendar edge/DST tests are pending.
- Maximum scanned horizon is 3,660 days; result cap is 1–1,000, with default 64. A cap may terminate scanning before horizon enforcement; callers must still supply bounded windows.
- Native DST gaps/overlaps, clock changes, traveling, user edits, iOS pending notification limits, Android exact-alarm permissions, cancellation errors and eventual consistency remain unimplemented.
- Planner outputs desired operations only; executing some commands before a process crash requires a transactional persistence and retry protocol.
- Full Kotlin Multiplatform Gradle compilation, Android APK, iOS app and physical-device testing remain unavailable in this environment.

## Next implementation target

Set up a supported Gradle/KMP toolchain with Android SDK and macOS/Xcode runner. Then add a thin native platform adapter contract, persisted registration records and a real Android/iOS notification scheduling spike. Verify the time-zone resolution choices before promoting this candidate code into a production shared module.

## References

- [Kotlin Multiplatform platform code](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
- [Android AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
- [Apple local notification scheduling](https://developer.apple.com/documentation/UserNotifications/scheduling-a-notification-locally-from-your-app)
