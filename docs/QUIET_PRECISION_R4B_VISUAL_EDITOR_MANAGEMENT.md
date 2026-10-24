# R4b — Visual correction: Reminder Editor and Management

**Date:** 2026-10-24
**Baseline:** R4a real native Today launcher, approved Quiet Precision R2 reference, original reminder/data engines
**State:** Source implementation committed; native screenshot review and full CI must pass before product-design sign-off
**Scope:** Android UI only; iOS remains at prior native SwiftUI alpha pending visual parity phase

## Why this exists

R3e functional screens were not faithful to approved R2. R4a improved the Today launcher but did not migrate the editor/management experience. R4b extends the **actual reusable visual system**, not merely a CSS/prototype screenshot, to the highest-frequency controls while preserving user records.

## Changes

### Reminder Editor
- Reuses `QpVisual` semantic tokens, typography, tonal notices, label spacing, icon treatment and bounded cards.
- R2-like single-screen hierarchy: back control, REMINDER DETAILS, title field, date & time card, schedule controls, compact follow-up truth state and prominent save.
- Pre-populates replacement schedule rule mode and weekdays as previously implemented; data mutation methods and event audit behavior are unchanged.
- Title input enforces 200 characters in the presentation layer and existing validation remains authoritative.
- Supports new, edit one-off and replace future recurrence in same domain code path.
- Follow-up explicitly NOT configurable yet. No invented scheduling behavior.
- Editor ScrollView supports keyboard and small screens; dynamic font/IME QA still required.

### Management & Settings
- Unifies navigation header, R2 title/eyebrow, segmented Reminders/Settings surface and rounded cards with `QpVisual`.
- Existing recurrence pause/resume, archive, history, scheduler reliability/repair and backup export/verify routes are retained.
- No new tables, rule identities, notification permissions or backup importer.

### QA workflow repair
- R4a separate screenshot CI **failed because the dedicated Linux emulator lacked KVM access**; existing Android device CI had KVM setup.
- Dedicated visual workflow now enables KVM and uses same emulator flags as the proven successful Android device workflow.
- Real emulator capture now exports three screenshots: `quiet_precision_r4a_today.png`, `quiet_precision_r4b_editor.png`, `quiet_precision_r4b_management.png`. Captures are produced from actual Android rendered views.
- Added non-destructive UI assertions and updated R3b editor text smoke tests to R4b hierarchy.
- Screenshot creation is an artifact of automated tests, NOT automatic proof of visual fidelity.
- R4b sign-off requires manual screenshot-by-screenshot comparison to R2 gallery at supported mobile size, plus empty/populated, dark, large font, keyboard and screen-reader review.

## Data safety

- Android applicationId `dev.reminderos.alpha.v05d` unchanged.
- Android versionCode `17`, versionName `0.6.6-r4b-visual-alpha`.
- No modification to `ReminderDatabase`, `ReminderAlarmEngine`, recurrence logic, audit events, or encrypted backup formats.
- The previous alpha remains recoverable on its original feature branch.
- Debug-signing may differ between GitHub runners; do not uninstall a data-bearing older install. V0.5e portable RPOSB2 import and rollback still pending.

## Open work

This is not full 34-screen R2 fidelity. Visual remediation remains for History & Archive, Settings inner components, Backup, Welcome/onboarding, Track, Goal, Routine and Insights. iOS equivalent visual pass, visual regression snapshots, physical-device proof, accessible font scaling, and signed update path remain outstanding.
