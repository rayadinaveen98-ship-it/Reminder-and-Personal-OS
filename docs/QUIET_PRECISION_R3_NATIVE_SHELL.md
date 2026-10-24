# Quiet Precision — R3 native UI vertical slice

**Date:** 2026-10-24  
**Branch:** `feature/quiet-precision-native-shell-r3`  
**Status:** Android and iOS source implemented; GitHub CI triggered; do not claim build success until checks finish.  
**Review:** draft PR #20, stacked on R2 design PR #19 and V0.5d native reminder alpha.

## Scope implemented

### Android

- `QuietPrecisionActivity` now launches first, via manifest intent-filter swap.
- Uses **the same `ReminderDatabase`**, `ReminderAlarmEngine`, app ID, events and alarm identities as V0.5d: **no schema migrations**.
- Today: read actual saved reminders, distinguish future from overdue/unresolved, show direct **Done / Snooze 10m / Skip** via revision-guarded `store.act`; Skip asks for confirmation.
- Library: actual native recurrence rules and visible reminder occurrences. Management actions continue in existing `MainActivity`.
- Insights: accurately labeled all-time visible reminder counts (not 7/28/90-day personal analytics). Track: explicit unfinished feature state.
- Entry to original editor, recurring schedules, permissions, diagnostics and legacy+RPOSB2 export/verification remains available via **Tools**. Old `MainActivity` not deleted.
- System theme dynamic values use Quiet Precision light/dark tokens; buttons have minimum 48dp target. Native screen reader / dynamic type QA is pending.

### iOS

- New SwiftUI `QuietPrecisionHomeView` is the app's initial `WindowGroup` view.
- Four tabs: Today, Track, Insights, Library, with real `IOSReminderStore.shared.visibleReminders` and `activeRules` where applicable.
- Done / Snooze 10m / Skip directly call the existing revision-gated `store.respond`; skip confirmation.
- Old `PreviewContentView` remains accessible as a sheet with real create/edit workflows, recurrence, backup/export and verify.
- `IOSReminderStore` file location/JSON schema, bundle ID, local notification handlers and registration logic are unchanged.
- No iPhone IPA or physical-iPhone notification reliability claim.

### Added non-destructive smoke tests

- Android `QuietPrecisionShellInstrumentedTest`: launch shell; visible tabs; select Track and verify missing implementation is honestly described. It never resets existing records.
- iOS `QuietPrecisionHomeViewTests`: confirm SwiftUI view can be hosted. Does not infer scheduling correctness from a view render.

## Architecture boundaries

`R2 UI → Existing native domain → Existing persistence/scheduling`.

There are **no new table definitions, migrations, fake tracker values or forced data imports**. This first slice intentionally routes editing and backup to tested legacy native views rather than rebuilding them in the same commit.

## Regression checklist / next gates

- Android: Gradle `assembleDebug`, testDebugUnitTest and compile Android instrumentation APK; emulator test launch and click through, actual Done/Snooze/Skip persisted and alarm reconciled; reboot/notification permission, exact alarm/Doze, responsive layout, TalkBack, dark mode.
- iOS: Xcode Simulator compile and XCTest, UI view appearance/navigation, action responses and sheet; VoiceOver/Dynamic Type, Reduce Motion, Focus, notification permissions on physical iPhone.
- Confirm no duplicate events after stale notification taps or repeated onResume.
- Ensure package/bundle continuity and stable signing before distributing as an update; do **not** uninstall old alphas or claim old-package data migrates automatically.

## Unimplemented intentionally

- R2 onboarding flow and first-use logo motion: design ready, native implementation pending brand icon approval and launch performance tests.
- Native full remake of Reminder editor, advanced recurrence, backup and diagnostics: remains accessible through legacy tools; phase R3b.
- Activity tracking, goals, 7/28/90 analyses and personal insight engine: no backing data or production screens; future P0 work.
- V0.5e portable RPOSB2 import and rollback: required **before** any destructive storage migration.
- Final name, final logo, signed releases, native offline/large-text QA and physical hardware delivery certification.

**Decision:** Work on native UI can proceed safely without portable import **only** while keeping the existing stores and app identities unchanged. R3 does not claim product completion.
