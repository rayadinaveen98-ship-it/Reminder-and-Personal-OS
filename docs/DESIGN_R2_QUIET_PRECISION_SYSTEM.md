# R2 — Quiet Precision Design System

**Date:** 2026-10-24  
**Selection:** Quiet Precision, selected by product owner after R1.  
**Stage:** UX/UI design, interactive prototype, implementation handoff; **not** Android/iOS implementation.  
**Working title:** Reminder and Personal OS. Consumer name and final logo are **not** approved by selection of a visual style.

## 1. Product design intent

Create a premium, visually controlled **personal command center** that remains extremely easy to use. The experience follows **Remember → Respond → Record → Understand → Improve**. No-response ≠ failed; notification scheduled ≠ delivered or read; a tracker log reflects a user-entered event; evidence-backed insights show source records.

The selected A direction replaces alternative Warm Observatory and Living Signal design explorations. Its signature is **cool blue/graphite, crisp typography, flat-to-soft surfaces, small optical indicators, disciplined spacing, and minimal but purposeful animation**. Do not add an unrequested pet, avatar, mandatory sign-in or welcome companion. The previous R1-generated artwork with a companion was illustrative and is not approved product scope.

## 2. Semantic color token contract

| Semantic role | Light | Dark | Use |
|---|---|---|---|
| Brand primary | `#3D63CF` | `#87A7FC` | Focus, principal actions, selected tab |
| On-primary content | `#FFFFFF` | `#101821` | Label on primary |
| Brand tonal | `#EEF3FF` | `#263656` | Low-emphasis focus card |
| Brand tonal content | `#355CBF` | `#B3C6FF` | Links and labels on tonal |
| Canvas | `#F6F8FC` | `#121B27` | Screen background |
| Surface | `#FFFFFF` | `#1D2938` | Cards and bottom navigation |
| Surface secondary | `#F1F5FA` | `#263346` | Controls / selected group base |
| Primary text | `#1D2937` | `#F3F6FA` | Body / headings |
| Secondary text | `#5F6E80` | `#A0AFC1` | Supporting copy |
| Outline | `#DFE6F0` | `#354457` | Structural lines |
| Positive | `#196E55` | `#79CDB1` | Explicit Done |
| Positive container | `#E8F6EF` | `#254639` | Confirmation |
| Caution | `#995B1E` | `#E3B279` | Overdue/unresolved, with written status |
| Caution container | `#FFF4E6` | `#4A3729` | Caution background |
| Negative | `#AB3850` | `#F39AA3` | Errors/destructive actions |
| Negative container | `#FFF0F2` | `#49303B` | Error background |

**Accessibility:** implement minimum 4.5:1 text contrast (WCAG AA normal text), 3:1 for large text; validate real compositions and disabled states, not just palette swatches. No status expressed solely by color. The concept uses limited 10–11px microcopy at desktop scaling; **production must permit platform Dynamic Type and font scaling**, test enlarged text and avoid fixed-height clipping.

## 3. Type, geometry and density

- Preferred: native system type (iOS SF family and Android appropriate system/Material); Inter is prototype fallback. Do not require online fonts.
- Headline 23/29, display 28/34, title 18/25, body 14/21, label 12/17, caption 11/16 (nominal design sizes; platform scalable).
- Use weight, spacing and alignment first; avoid massive typography, full-screen blocks of explanation, decorative charts or large redundant labels.
- Spacing scale (dp design reference): 4, 8, 12, 16, 20, 24, 32, 40, 48.
- Control radius 11; standard card 16; modal bottom sheet 24. Minimum Android interactive region 48×48dp; iOS should observe platform accessibility sizing; render visible icon smaller within a larger hit target.
- Honor navigation bars, notches/safe-area insets, compact displays, soft keyboard/IME, scroll restoration, and screen readers. The standalone HTML demonstration is not a full accessibility certification.

## 4. Component specification

**Shell:** app bar, segmented control, four-item bottom navigation (Today/Track/Insights/Library), independent floating Quick Add affordance, navigation back, secure sheet layer. Quick Add exposes Reminder, Activity log, Tracker, Routine and Goal.

**Reminder components:** FocusCard, ReminderRow, OutcomeActions (Done/Snooze/Skip), UnresolvedIndicator, RecurrencePicker, DateTimePicker, QuietHoursRule, ScheduledOccurrenceList, native reliability status and contextual fallback. Do not conflate an OS alert with the user's task outcome.

**Personal OS components:** TrackerRow, MeasurementEntry, TrackerTimeline, RoutineStep, GoalTarget, ProgressEvidence, 7/28/90 selector, Completed/Skipped/Unresolved breakdown, source-linked InsightCard, DataCoverageBanner, WeeklyReviewCard.

**Foundation components:** Button, TextField, Select, Toggle, AccessibleIconButton, StatusChip, Divider, Toast, ConfirmationSheet, Search, Tabs, EmptyState, Loading, Error, PermissionGate, PrivacyNotice.

**Sensitive controls:** private backup export and verify, portable import disabled until V0.5e is implemented and tested; wrong-password, corruption and rollback are distinct error states. No bogus 'healthy' notification statuses in previews.

## 5. Behavior and copy decisions

- Home: **upcoming + unresolved** first. Avoid untrustworthy percentage summaries when there is inadequate evidence.
- ‘Unresolved’ or ‘No response recorded’, **not** ‘Missed’/‘Failed’ as a verdict.
- ‘Done’, ‘Skip’ and ‘Snooze’ are explicit and independent per occurrence.
- The user can create an activity log with no reminder; goals only reflect eligible recorded values, not alert delivery.
- Insights must show window, sample size, metric denominator and supporting records. If insufficient data: ‘Not enough data’ with a relevant next step.
- A backup that can be verified but not imported is called ‘Export / Verify’; never imply it was restored.
- Error copy says what happened, what remains safe, and the next available action.
- Only request OS permission at the relevant moment, allow skip, and explain native capability limitations.

## 6. Cross-platform engineering handoff

Reuse native scheduling / persistence from verified V0.5d alpha and shared recurrence logic; **do not undertake unrequested full rewrite**. UI may share composable components if Android/iOS accessibility and build parity validate; retain SwiftUI/native bindings where necessary. View models consume domain interfaces and explicit capability statuses. Preserve event IDs and original time slots. All data writes use the domain repository, not presentation components. On Android check exact-alarm/notification permission/reboot behavior; on iOS respect Focus/local-notification pending-window limitations.

The **V0.5e portable import + rollback** safety gate remains mandatory before production migration of any populated store, change in storage ownership, or distribution marketed as lossless replacement of earlier alphas.

## 7. Deliverable inventory

- **R2 interactive design study:** `quiet_precision_r2_prototype.html` in conversation attachments; 34 selectable states; fake user records; theme control; demo-only quick add.
- **R2 visual contact sheet:** `quiet_precision_r2_screen_gallery.png` in conversation artifacts; 12 representative renders.
- **Token source:** `quiet_precision_r2_tokens.json` in conversation artifacts.
- **Identity candidate:** `quiet_precision_r2_icon_candidate.svg` in conversation artifacts (not final commercial mark).
- **Motion storyboard:** `docs/DESIGN_R2_MOTION_AND_INTERACTION.md`.
- **State inventory and gate criteria:** `docs/DESIGN_R2_SCREEN_MATRIX.md`.
- **Prototype test record:** `docs/DESIGN_R2_PROTOTYPE_QA.md`.

Attachments are **not native builds**. Component layout, animation and accessibility need actual-device sign-off prior to launch.

## 8. Open approvals

The product owner chose **visual direction A**. Brand name, exact icon geometry, optional voice capture, copy language(s), onboarding defaults and final screen-by-screen usability remain candidates pending feedback. The visual R2 package is ready for review, **not** a declaration that implementation is finished.

## 9. Normative design checks / external references

- Android developer guidance: https://developer.android.com/guide/topics/ui/accessibility/apps
- Android accessibility touch targets: https://support.google.com/accessibility/android/answer/7101858
- W3C WCAG 2.2 1.4.3 contrast and text resize: https://www.w3.org/TR/WCAG22/
