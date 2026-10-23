# iOS V0.4a — Native persistent reminder alpha

**Date:** 2026-10-23
**Scope:** iOS SwiftUI app compiled for Simulator. The production shared KMP database and full feature parity are NOT implemented.

## What exists

The former iOS preview scheduled a single 60-second test alert. V0.4a replaces that UI with native on-device one-time reminder management:
- Create and view multiple one-time reminders with title and due date/time.
- Persist reminder records and explicit action history as a versioned JSON snapshot in app-private Application Support, using atomic replacement and iOS file protection that allows post-first-unlock background notification action handling.
- Done, Skip and Snooze 10 minutes from inside the app or via iOS actionable local notification buttons.
- Keep UNKNOWN/unanswered outcomes separate from Done/Skipped.
- Reject obsolete notification actions using stable reminder UUID + revision in request userInfo. Snoozing increments revision.
- Archive locally without deleting history or changing an outcome.
- Reconcile future pending iOS notifications on app launch, creation, action and user-triggered repair. Overdue unanswered reminders remain visible as unresolved; the app doesn't send surprise late alarms.
- Ask for notification permission explicitly and show permission errors.
- Cap pending native alerts at 48. iOS has pending-notification practical limits and can suppress/delay delivery under Focus, notification settings and system behaviour. This developer alpha is NOT a critical alarm.
- Bundle ID `dev.reminderos.iosalpha` is separate from the old single-notification preview.

## Boundaries and remaining gaps

This is an **iOS-native SwiftUI/persistent file prototype**, not a KMP-shared database, not full parity with Android V0.3e. It does not yet have:
- Daily/weekly/monthly recurring rules and independent per-date occurrence records.
- Edit date/time, custom snooze duration, pause/resume, encrypted export/import, durable native scheduling outbox, reusable Kotlin business logic integration or migrations from Android SQLite.
- Long-term iOS notification reconciliation after an arbitrary background process kill. Pending OS requests survive many app lifecycle changes, but unexpected settings/permission events might require opening the app for repair.
- A signed iPhone IPA or TestFlight distribution. CI produces an **iOS Simulator .app** only; installing/running on a physical iPhone requires Xcode signing and the proper developer account.
- Physical-device notification delivery, iOS Focus, restart/recovery or battery tests.

A user pressing a notification action explicitly counts as the corresponding outcome, but a delivered, ignored or dismissed alert does **not** imply success or failure. Each change is persisted before the app asks iOS to reschedule/cancel the notification.

## Validation protocol

1. Compile the iOS Simulator project with Xcode via GitHub Actions. Avoid claiming a working build until the job finishes successfully.
2. Launch on Simulator, grant notifications; save several reminders; terminate/reopen the app and verify records persist.
3. Schedule a near-future test and verify Done/Snooze/Skip both in the app and from notification buttons.
4. Confirm that an older action callback cannot resolve an already snoozed reminder.
5. Archive an upcoming reminder; verify it disappears while its history is retained.
6. Verify the 48-pending cap doesn't pretend the rest were scheduled.
7. After physical iPhone signing, test Focus modes, permission denial, locked-screen actions, reboot and time-zone shifts.

## Architecture path

Android V0.3e remains the main functional reference with SQLite/outbox, recurrence and encrypted backups. The next cross-platform milestone is to consolidate its tested business rules and persistence into one portable KMP domain and database adapter. iOS must preserve its native UNUserNotificationCenter integration while using the shared model for recurring occurrence identity and explicit outcome analytics.

See Apple's documentation:
- https://developer.apple.com/documentation/usernotifications/handling-notifications-and-notification-related-actions
- https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app
