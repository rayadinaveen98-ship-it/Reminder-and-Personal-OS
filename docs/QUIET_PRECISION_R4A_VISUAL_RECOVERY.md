# R4a — Quiet Precision Native Visual Recovery (Android)

**Status:** First native visual-fidelity slice; CI and screenshot comparison required before approval.
**Source:** Approved R2 design, especially `quiet_precision_r2_mobile.png`, `quiet_precision_r2_screen_gallery.png`, `docs/DESIGN_R2_QUIET_PRECISION_SYSTEM.md`.
**Parent:** R3e archive/history feature branch.
**Platform:** Android first, iOS parity deferred to next focused slice; iOS existing app remains unchanged.

## Recovered R2 composition

- Safe-area status bar; dated greeting (“Good morning/afternoon/evening”); compact Settings icon.
- Outlined blue-tinted NEXT REMINDER focus card with time, primary and secondary action.
- Three real summary tiles: future pending reminders from `schedulingHealth.activeFuture`; overdue unknown from `schedulingHealth.overdueUnknown`; no invented performance percentage (Not enough data).
- “Up next” is a two-row rounded card with optical native vector icons, stable spacing, dividers and tappable rows.
- “Needs your response” lists unresolved overdue; no-response is not classified as Skip.
- Four icon-led bottom tabs with centered elevated Quick Add. Quick Add opens the current reminder editor; unsupported Track/Goal operations are explicitly marked Planned.
- Library routes to current schedule management and full History & Archive; Insights displays only honest all-time existing reminder outcomes; Track remains clearly planned, not fake.
- Native light and dark tokens, typography, 48dp minimum tap areas and accessibility descriptions; no network fonts.
- No replacement or changes to `ReminderDatabase`, recurrence rules, notification receiver IDs, encrypted backups or package name.

## Implementation files

- `QpVisual.kt`: reusable color, typography, cards, controls, optical icon holders and spacing.
- `QuietPrecisionVisualActivity.kt`: genuine launcher with the intended composition; previous `QuietPrecisionActivity` retained nonexported as fallback for inspection.
- `res/drawable/qp_ic_*.xml`: custom scalable line icons used throughout navigation, settings and upcoming rows.
- Manifest switches launcher only. Android versionCode 16 / versionName 0.6.5-r4a-visual-alpha, original applicationId unchanged.

## Testing and delivery gate

- `QuietPrecisionVisualShellInstrumentedTest`: validates semantic view hierarchy and captures native Today pixels from a clean Android emulator.
- `.github/workflows/quiet-precision-visual-qa.yml` uploads actual emulator screenshot to a GitHub artifact; must be **manually compared** against R2 reference in review. A passing label assertion or compiling activity does not prove visual fidelity.
- Existing Android emulator persistence/notification and cross-platform CI remain mandatory.
- Before user acceptance: review visual evidence including dense data, empty state, large font, dark mode, narrow screen; collect real device feedback; don't claim complete 34-screen R2 parity from this slice.
- **No signed in-place upgrade guarantee**. Do not uninstall a data-bearing alpha until verified portable restore exists.

## Remaining (not done in R4a)

Onboarding/welcome/orbital motion; polished Android editor, management, archive, settings and backup tools; matching iOS visual system; real Track/Goals/Routines; evidence analytics; V0.5e portable import & rollback; physical-device/screen-reader/performance QA. Those require separate acceptance gates. No new brand identity is approved by this technical implementation.
