# R2 — Screen Inventory, Journeys and Acceptance Matrix

**Direction:** Quiet Precision, selected 2026-10-24.  
**Coverage:** 34 selected routes in standalone interactive HTML demonstration. Native Android/iOS screens not yet migrated.  
**Status levels:** **DEMO** = clickable reference using fake in-memory data; **ALPHA** = relevant reminder-domain backend already exists on one/both platforms; **PLANNED** = scope requires new backend.

## Group A: Identity and setup (4)

| Route | UI intent | Acceptance design checks |
|---|---|---|
| splash | Orbital brand static/first-run sequence | First-run only, reduced-motion static fallback, no notification deep-link delay; logo candidate not final |
| welcome | Explain Remember/Record/Understand loop | One primary CTA, optional try-first path, no mandatory login |
| permissions | Contextual alert permission education | Skip accessible; deny is not failure; actual OS capability state never invented |
| first | First reminder or first tracker | Two clear choices and direct Today exit; user not forced into long configuration |

## Group B: Today/reminders (7)

| Route | UI intent | Acceptance design checks |
|---|---|---|
| today | One clear next event and unresolved queue | Avoid empty vanity graphs, show next action immediately |
| upcoming | Group future and overdue occurrences | Date groups, filters and accessible recurrence meaning |
| reminder | Single occurrence detail | Done/Snooze/Skip distinct; no-response stays Unknown |
| editor | Create/edit | Date, time, recurrence, follow-up, notes; error feedback and true save acknowledgment |
| recurrence | Repeat and quiet-hours behavior | Weekday, monthly and local-zone semantics clear; independent per-date outcomes |
| history | Outcome and correction timeline | Action source and time; undo/correction audited; skipped differs from unresolved |
| delivery | Capability / diagnostic state | Do not claim guaranteed delivery, focus bypass or alarm precision |

## Group C: Tracking/routines/goals (8)

| Route | UI intent | Acceptance design checks |
|---|---|---|
| track | Activity capture and tracker directory | Log without reminder; clear one-tap action |
| tracker | Tracker and trend | Explicit unit and source records; corrections affect derived chart |
| log | Numeric/count/check-in event | Time, unit, validation, note, manual entry evidence |
| trackers | Tracker creation | Distinct check-in/count/measurement types, units and optional constraints |
| routine | Multi-step routine composer | Partial completion never automatically completes unrelated steps |
| goal | Target library | Explicit target / timeframe / link to eligible entries |
| goaldetail | Goal detail | Derive progress from recorded events; unknown vs target remaining clear |
| timeline | Searchable / editable event timeline | Correction and backfill information, audit trace |

## Group D: Insights/reviews (6)

| Route | UI intent | Acceptance design checks |
|---|---|---|
| insights | 7/28/90-day top-level picture | Declared denominators, sample sizes, null state for insufficient data |
| breakdown | Done/Skipped/Unknown | Count all eligible due slots; unknown is neither Done nor Skipped |
| trend | Tracker numeric trend | Axis, unit, period and source visibility |
| weekly | Actionable retrospective | Only supported factual observations, not pseudo-personal coaching |
| evidence | Insight origin detail | Source event links, small-sample caveat, dismiss/feedback |
| coverage | Missing-data quality | Time-zone, denominator, permission and event reliability limitations |

## Group E: Library/settings/privacy (9)

| Route | UI intent | Acceptance design checks |
|---|---|---|
| library | Manage all definitions | Reminders, trackers, routines, goals; independent search/filter |
| search | Search and archive | Preserves archived history; accessible empty results |
| settings | One preferences home | Privacy and reliability clearly discoverable |
| notification | Quiet hours and follow-up | Opt-in follow-up, capped per occurrence, platform-native sound limitations |
| appearance | Dark/light, accessibility | Scaled text, semantic status labels, reduced-motion |
| backup | Export/verify status | Distinguish existing RPOSB2 export/verify from legacy RPOSB1 restore |
| restore | V0.5e future importer | Disabled until tested; preflight, atomicity, rollback and confirmation |
| diagnostics | Native scheduling / permissions health | Display only queried state; no synthetic all-green health report |
| help | Explain limitations in plain language | Non-emergency nature, quiet-hours behavior, privacy |

## Shared states required for EVERY flow

No data, permissions denied, exact-alarm unavailable, app offline, app killed/backgrounded, duplicate action, action conflict, old recurrence revision, insufficient analytics events, backfilled/corrected entry, export wrong passphrase, import unsupported version, rollback after disk error, Android reboot, iOS app lifecycle, narrow/small device, keyboard/safe area, large dynamic text, dark mode, reduced motion, VoiceOver/TalkBack.

## Core UI navigation and action contracts

1. **Reminder:** Today → Reminder Detail → Done/Skip/Snooze → persisted event → Today/History/Insights; replay duplicate action must not duplicate outcome.
2. **Recurring:** Library → Repeat editor → updated future schedule → independent current/next logical slots.
3. **Capture:** Track → Log → validated event → recent log → tracker chart → corrected history.
4. **Goal:** Library → Goal builder → linked tracker/routine → actual progress from explicit records.
5. **Evidence:** Insights → date range → concrete observation → sources → corrected source updates next snapshot.
6. **Restore (future):** Settings → Backup → Export/Verify → only after V0.5e, import guard → empty compatible store → atomic restore → post-commit schedule repair.

## P0/P1 split for engineering

**P0 before marketing release:** reliable reminders, recurrence, explicit outcome recording, tracker definitions/events, routine/goal links, 7/28/90 valid reports, evidence trace, import/restore safety, offline use, native capability checks, accessibility and physical-device QA.

**P1:** richer templates, flexible multi-period reviews, advanced customization, accessibility improvements beyond minimum, optional widgets, structured quick capture. **P2:** opt-in AI/sync/advanced integrations after reliable P0.

## R2 visual-design quality gate

Pass: every route present and navigable with fake data, light/dark demonstrated, interaction labels coherent, no invented operational states, component tokens documented, first-run storyboard specified, and return path to prior flow. Known incompletes: final brand approval, native UI implementation, language localization, full validation/correction UX, real Android/iOS viewport QA, focus order and screen reader QA, platform-specific menus and file picker dialogs.

The HTML study is **not** a test of OS delivery, SQLite/Ios JSON durability, cross-platform import or actual analytics computation.
