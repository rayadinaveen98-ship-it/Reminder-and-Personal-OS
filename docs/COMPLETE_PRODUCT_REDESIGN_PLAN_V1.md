# Complete Product Redesign & Expansion Plan — Proposal V1

**Date:** 2026-10-24  
**Status:** PROPOSED FOR REVIEW — no visual direction or scope accepted yet  
**Source:** Android/iOS V0.5d alpha branch. No executable code changed in this plan.  
**Product:** Reminder and Personal OS (working title; brand identity not locked)

## Executive product assessment

The repository has an implemented and CI-tested native-reminders *developer alpha*: Android has recurring reminders, action handling, persistent SQLite and reliability safeguards; iOS has SwiftUI reminders and recurrence with simulator tests. KMP recurrence is executed in both apps. V0.5c adds shared snapshot validation; V0.5d adds cross-platform encrypted export/verification, not portable restore.

The complete Personal OS product roadmap is not finished. Full activity tracking, routines/goals, analytics, insight engine, broad accessibility/visual system, real-device release QA, signed installations and a production-grade app identity remain unfinished.

Do not treat implementation of this plan as already complete.

## Product promise

**Remember → Respond → Record → Understand → Improve.**

- **Reliable first:** due events, unknown outcomes, pause/snooze and permissions must behave predictably without network/AI.
- **A sophisticated system with a quiet surface:** uncluttered Today screen, fast capture, progressive disclosure, no duplicated text or dense dashboards.
- **User-entered evidence, not assumptions:** notification scheduled ≠ seen ≠ action completed. Unknown is not Skipped.
- **Private by design:** offline-first, no account needed, encrypted user-controlled backups, no raw data placed in Git.
- **Cross-platform quality:** same conceptual journeys and design language, but iOS and Android may have native controls/permission flows.
- **Purposeful motion:** high-quality and restrained; never blocks urgent reminder action, respects reduced-motion setting.
- **Advanced intelligence is traceable:** contextual hints and pattern reports link to source events, show sample size, and never silently move/remake schedules.

## Brand and visual identity (separate approval gate)

1. Review the *working product name* before committing to a market-facing name. Repository name can remain unchanged.
2. Explore 3 distinct visual directions before choosing one: **Quiet Precision** (modern typography, neutral graphite and one radiant accent); **Warm Observatory** (soft off-white, charcoal, sage, subtle warm gold, humane rounded geometry); **Living Signal** (deeper ink surfaces, clean gradients, sparse illuminated indicators). Final logo must be original, distinct and legible as a small icon.
3. Deliver app icon family: Android adaptive foreground/background and monochrome, iOS icon, notification small icon, splash mark, dark/light variants, accessibility contrast checks.
4. Welcome motion concept: a small point/signal progresses through daily moments, resolves into the brand symbol and app name. Proposed first-launch duration ~2 seconds with a skip/short-circuit path; subsequent cold starts should be essentially instant, not replay a multi-second cinematic intro. Provide static/reduced-motion equivalent and ensure action notification entry goes straight to the requested task.
5. Micro-interactions: restrained card elevation, status changes, segmented chart transitions, successful capture feedback and user-controllable haptics. Never use animation to disguise loading or make an app feel sluggish.
6. Design foundations: 8-point spacing system; safe-area and keyboard handling; minimum 44–48dp target; adaptive type, readable contrast, large text, TalkBack/VoiceOver, dynamic color options where useful, light/dark and reduced-motion variants.

## Information architecture

Main navigation (proposed, not locked):
- **Today:** next due, unresolved/overdue, quick actions, current goal/routine focus; no metric wall.
- **Track:** quick-log activity and measurement, custom trackers, recent entries and history.
- **Insights:** meaningful weekly/monthly changes with evidence, sample sizes, unknown-data context.
- **Library:** reminders, routines, trackers, goals, templates and archive, with search/filter.
- **Quick Add:** prominent single affordance available from primary tabs: reminder, log, routine or goal.
- **Settings:** profile/local preferences, notification and reliability diagnostics, privacy, backups and appearance.

Universal patterns: search, detail sheets, inline edits, editable filters, undo for reversible actions, empty/loading/error/offline states, recovery actions, clear navigation back to the origin.

## Complete screen and journey inventory

### A. Brand and first run
A01 App icon / splash static and animated variants.  
A02 First-launch welcome with clear single promise.  
A03 Getting started: default reminder behavior and local-data privacy.  
A04 Personalization: optional name, preferred daily window, language and theme; skippable.  
A05 Notification permission education and platform-native authorization only when useful.  
A06 Android exact-alarm capability / battery guidance and iOS delivery/Focus limitations, honestly scoped.  
A07 Create first reminder or use sample walkthrough.  
A08 First reminder confirmation + optional test alert.  
A09 Onboarding recap with easy re-entry from Help.

### B. Today and action
B01 Today/home with next reminders, unresolved status and one clear quick capture.  
B02 All upcoming/overdue queue with date grouping.  
B03 Occurrence detail and action history, including unknown vs Done/Skip distinction.  
B04 Quick Add selector and compact reminder composer.  
B05 Full reminder editor (title, due, notes, urgency, repeat, follow-up, sound/quiet-hours policy).  
B06 Recurrence editor / selected weekdays / monthly dates / exceptions.  
B07 Reminder/routine details, upcoming slots, edit/pause/archive.  
B08 Notification preview/action feedback and snooze choice.  
B09 History and corrections, including why a record is unresolved.  
B10 Scheduler reliability / delivery capability status with recovery and Settings routes.

### C. Track, routines and goals
C01 Track overview with recent activity and prominent Log button.  
C02 New custom tracker: check-in, count or numeric/unit.  
C03 Log entry sheet with recorded/actual time, note and correction controls.  
C04 Tracker detail with chart, events, trends and entries.  
C05 History browser: calendar/timeline, search and filtering.  
C06 Routine setup and management, optional reminder link.  
C07 Goal builder with target/period and explicit progress source.  
C08 Routine/goal progress detail, missed-data explanation and historical comparisons.

### D. Insights and review
D01 Insights overview with 7/28/90-day controls and minimal important metrics.  
D02 Completion breakdown that separates confirmed Done, Skip and Unknown; no deceptive percentages.  
D03 Tracker trend and comparison detail, units and corrections.  
D04 Weekly review: accomplished, unresolved, shifts and one or two actionable observations.  
D05 Insight detail: evidence links, sample count, timeframe, uncertainty and feedback.  
D06 Data-quality/coverage page for incomplete logs and scheduling capability caveats.

### E. Library, privacy and settings
E01 Unified Library with typed categories, archive/search and templates.  
E02 Item detail and duplicate/edit/archive flows.  
E03 Notifications/quiet hours/follow-up preferences.  
E04 Appearance, accessibility and language settings.  
E05 Private data: export, verify, safe import/restore, storage usage and delete controls.  
E06 Backup verification, wrong-password/corrupt-file and rollback/recovery states.  
E07 Diagnostics: permissions, job reconciliation, local log export (no raw personal data by default).  
E08 Help, app limitations, onboarding replay and release information.

**Design ALL states**: permission denied; no reminders; first-time new tracker; one-off/recurring; scheduled vs attempted vs outcome; offline; interrupted import; long title; different time zones; dark theme; large font; screen reader; OS version differences.

## Advanced capability plan

**P0 — product-complete fundamentals:** reliable reminders and occurrence actions; quiet hours and capped follow-up; tracking of check-in/count/measurement; history/edit/correction; linked routines and measurable goals; offline 7/28/90-day reports; source-backed insights; backup/restore; permission diagnostics; accessibility.

**P1 — meaningful depth:** named tracker templates, grouped areas of life, flexible weekly review, richer timeline/calendar, quick capture presets, per-reminder sound controls where OS supports them, privacy/app-lock evaluation, richer comparisons and widgets where practical.

**P2 — optional expansion after evidence and release stability:** conversational capture or summary assistant grounded in user data and explicitly controlled; encrypted sync; on-device smart suggestions; additional integrations. Never make core reminders contingent on AI/cloud or assume iOS unrestricted background execution.

## Design and engineering implementation constraints

- Use **design tokens and shared domain contracts**, not a reckless full rewrite. Keep the verified native scheduling and data code stable.
- Evaluate Compose Multiplatform for shared presentation where accessibility and iOS fidelity are acceptable; retain platform-native SwiftUI/Android UI where needed. The repository currently has a Kotlin shared domain, *not* a finished shared Compose UI.
- Keep a dedicated feature branch and builds with a distinct alpha package if signing identity would replace a user's old install; make explicit when old records do not migrate.
- Do not auto-upgrade, overwrite, clear or implicitly copy alpha data to another package. Preserve existing installations and evidence.
- Use feature-by-feature integration with testable navigation, loading, empty, error and recovery states; no screenshot-only redesign as release deliverable.
- Define UI acceptance: every user journey reachable, correct data persistence and native actions, dark/light, portrait sizes and keyboard safe areas, reduced motion, dynamic text, 44–48dp controls, performance and platform QA.
- Cross-platform UI parity means matching user capabilities and understandable patterns, **not** claiming identical OS scheduling guarantees.

## Safe sequencing / dependency gate

**Design can start before V0.5e.** Brand exploration, wireframes, complete screen mocks, clickable flows and design system do not manipulate reminder records.

**V0.5e safe portable import and rollback must be a hard gate BEFORE:** changing persistence ownership, overwriting populated stores, consolidating historical alpha identities, attempting platform-to-platform transfer, or distributing an update marketed as data-safe. Prefer guarded empty-store import first, backup-before-import, validation of references, atomic transactions/rollback, byte-for-byte failure preservation, full history/unknown/archives, and schedule regeneration only after commit. Portability is not yet automatic sync.

### Proposed milestone order (no time estimates)
1. **R0 — Audit and freeze:** record known V0.5d CI, working reference screenshots and existing app flows, source/data compatibility, and known limitations; do not merge unfinished alpha stack blindly.
2. **R1 — Product/brand direction:** agree vision, product name strategy, navigation, hierarchy, P0/P1/P2, design principles, choose one of three complete identity directions.
3. **R2 — Full experience design:** user journeys, all-screen wireframes, design tokens/components, responsive Android/iOS hi-fi mocks, motion storyboard and interactive flow; review before implementation.
4. **R3 — Data safety:** finish V0.5e portable import, rollback and negative-path tests, and preserve native alpha users' data.
5. **R4 — UI infrastructure:** cohesive themes/navigation, reused components, native adapters, visual regression/accessibility checks, then migrate UI screen by screen while preserving reminders.
6. **R5 — Feature completeness:** native reminder parity and robust tracker/routine/goal/history flows with backing data, tests and offline support.
7. **R6 — Intelligence:** validated calculations, insight tracebacks, weekly reviews and opt-in helpful suggestions; never fabricate completion.
8. **R7 — QA and release:** physical Android/iPhone tests; timing, reboot/background/Focus limitations, data migration, security, accessibility, performance, signed update chain and documented release gates.

## Decisions reserved for user review

- Market-facing product name and preferred visual direction.
- First-launch motion detail and whether a companion/mascot belongs in THIS product (not inferred from other apps).
- How prominent Track versus Today should feel once tracking is implemented.
- Whether optional intelligent capture is text-only in the first release or later also voice.
- Whether to expose separate goal/routine nav or keep both in Library.
- Brand-specific asset production after choosing an identity; do not implement unapproved artwork.

## Acceptance of this planning artifact

This is a **draft design and expansion roadmap only**. It does not approve brand, colors, motion, architecture rewrites or product-scope additions. It records work planned against the actual V0.5d code branch and exists for review. The pre-existing alpha code is unchanged.
