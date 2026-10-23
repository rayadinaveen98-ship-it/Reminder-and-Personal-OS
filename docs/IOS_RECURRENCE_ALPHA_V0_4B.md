# iOS V0.4b — Calendar recurrence and independent reminder outcomes

**Date:** 2026-10-23  
**Status:** Xcode iOS simulator app build and all 15 XCTest cases verified passing in GitHub Actions; physical-iPhone delivery unverified.

## Delivered

- Daily, selected-weekday and monthly repeating schedules using a standalone pure Swift calendar recurrence function.
- Repeat selection and weekday toggles in native SwiftUI, plus routine-level Pause, Resume, Archive and due occurrence History.
- Each dated recurrence slot receives its own UUID, stable local date key and explicit UNKNOWN, DONE or SKIPPED status.
- An ignored day never blocks the next day's reminder. Snoozing an occurrence changes only that occurrence's effective due time, never its original logical date or siblings.
- Monthly reminders on days 29–31 skip months that lack that day. Calendar arithmetic uses local wall-clock instead of fixed 24-hour milliseconds, with tests for spring and fall DST transitions.
- New snapshot **version 2** adds optional recurrence definitions and optional occurrence links. **Version 1 files are still readable**, and writes are atomic and versioned. Unsupported/corrupt data remains protected against destructive reset.
- Rolling materialization targets **seven future occurrences** per active routine, plus only the last 30 days of missed-date catch-up. The iOS notification scheduler still caps pending requests at 48 across all reminders.
- Routine pausing and archiving removes its pending native notifications on reconciliation. History is retained and visible for active routines.
- Native Done/Snooze/Skip notification actions and stale revision checks continue from V0.4a.

## Why this is not yet parity with Android

This V0.4b recurrence engine is **native Swift**, not an integrated Kotlin Multiplatform scheduler. It implements the same user-facing calendar categories but is not yet guaranteed to match every Android DST/time-zone edge case. Full parity will require shared domain rules and persistence.

The app only replenishes the rolling native notification window when code is run (such as app foreground or notification-action processing). **iOS does not guarantee launching third-party apps to replenish local notifications when the user never opens or responds.** If more than the seven materialized future occurrences pass while the app stays unopened, newer reminders can remain unscheduled until the next launch. This limitation must be addressed or clearly communicated before any production release.

A native OS notification request does not guarantee on-time presentation. Focus modes, revoked permissions, notification scheduling limits and other platform restrictions apply.

## Validation plan

- Xcode builds and executes the existing five persistence XCTests plus new calendar/recurrence tests on an iPhone simulator.
- New tests cover weekdays, empty weekday masks, missing monthly dates, leap day, DST spring/fall, routine creation with independent future slots, Done not affecting siblings, pause persistence and **backward reading of V1 snapshot data**.
- CI does **not** produce a signed iPhone-installable IPA and no physical-device scheduling or battery testing has been completed.
- The existing Android V0.3e APK is unaffected by this iOS-only branch.

## Next engineering gates

1. Confirm all iOS simulator tests and complete four-job CI pass; fix failures before labeling the milestone verified.
2. Add recurrence-definition editing with transaction-safe rescheduling and independent old occurrence history.
3. Select KMP shared business rules / cross-platform SQLite persistence implementation and test migration from both current local stores.
4. Plan reliable iOS replenishment within platform background limits, or use suitable native recurring notification triggers without misrepresenting individual occurrence history.
5. Complete iOS code signing, physical-iPhone notification action, denied permission, DST/travel and reboot QA, then full Personal OS analytics.

## Verified CI — October 23, 2026
- [KMP / iOS Actions run #39934657157](https://github.com/rayadinaveen98-ship-it/Reminder-and-Personal-OS/actions/runs/39934657157): **all four jobs passed** — iOS shared compilation, SwiftUI simulator app build, Xcode XCTest execution, Android compilation/unit checks, shared JVM, and SQLite contracts.
- iOS Simulator: **15/15 XCTest passed** (10 new V0.4b calendar/recurrence, history, pause and legacy V1 snapshot compatibility cases, plus 5 V0.4a persistence regressions).
- Uploaded `ios-reminders-persistent-alpha-simulator` artifact (~6.0 MB) contains an iOS Simulator `.app`; not a signed iPhone IPA.
- Original Android V0.3e debug app also compiled on this branch without changes to its feature scope.
- Physical iPhone notification delivery, Focus modes, time zone changes, unattended long-run recurrence and release signing remain unverified. **Do not label this a production-ready recurring-reminder engine.**
