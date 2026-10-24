# Design Exploration R1 — Three Visual Directions

**Date:** 2026-10-24  
**Stage:** Design exploration / user selection; no production app code changed  
**Working app title:** Reminder and Personal OS (not necessarily final consumer brand)  
**Source:** `docs/COMPLETE_PRODUCT_REDESIGN_PLAN_V1.md` on this draft branch

## Non-negotiable common product language

- Four navigation destinations: **Today, Track, Insights, Library**; Quick Add accessible in one tap; Settings reachable from top-level identity/preferences area.
- No dense dashboard, unneeded explanatory paragraphs, duplicated information or outsized titles. A primary user action should be clear at a glance.
- A reminder response has **Done / Snooze / Skip**, with **Unresolved** as a distinct honest default when no response is recorded.
- First launch: invite one reminder or tracker; progressive onboarding, not mandatory multi-page forms or unnecessary permissions.
- First-launch logo motion should be concise (target ~2.0 seconds). Ordinary launches and notification entry bypass it; reduced-motion users see a static mark. This is a proposal, not locked until testing.
- Every direction must show the SAME three primary journeys for fair comparison:
  1. Today: next two reminders, one overdue-unresolved item, simple progress based only on confirmed data, quick add.
  2. Track: a custom tracker library, prominent Log action, compact entries, measurement/count/check-in support.
  3. Insights: 7/28/90-day selector, explicit confirmed/unknown breakdown, minimal trend, one evidence-backed observation.
- All concept art is illustrative. Do not use made-up charts or completion stats in production; seed test data explicitly for previews.

## A. Quiet Precision — utility made beautiful

**Personality:** understated, accurate, orderly.  
**Visual signature:** crisp spacing, nearly flat surfaces, thin concentric circles and line/dot identity.  
**Color tokens (concept-only):**
```
ink #202833
canvas_light #F8F9FB
canvas_dark #121821
surface_light #FFFFFF
surface_dark #202A35
primary #577FE4
muted #748092
positive #408E75
warning #CB9361
outline #DCE2EA
```
**Typography:** SF Pro / system sans substitute; editorial medium weights, compact labels, tabular numerals.  
**Icon:** a single ring with a deliberately offset orbiting point and a short inward radius marker; no over-detailed gradients.
**Welcome 2.0s storyboard:** 0–350ms lone indicator blinks; 350–1050ms ring traces into view; 1050–1550ms indicator docks to ring; 1550–2000ms wordmark appears; no replay on normal launches.
**Today:** large but restrained date; next reminder with light blue hairline; compact grouped rows; unresolved pill; simple FAB.
**Track:** clean column list with thin separators, tap-to-log sheet and one sparkline in detail, not on every list row.
**Insights:** axis-labeled subtle line chart and crisp comparison rows with Unknown explicitly visible.
**Potential issue:** may feel too clinical if microcopy and empty states lack personality.

## B. Warm Observatory — humane and premium

**Personality:** supportive, thoughtful, warm, spacious yet efficient.  
**Visual signature:** soft natural paper-like background, sage status accents, restrained gold focus marker, graceful geometry.  
**Color tokens (concept-only):**
```
ink #29332C
canvas_light #F7F4EC
canvas_dark #202921
surface_light #FFFDF8
surface_dark #2A352D
primary #637E6B
muted #758175
highlight #B88F56
positive #567963
warning #BA8056
outline #DCE1D5
```
**Typography:** modern humanist sans with clear numerals; no novelty fonts for core data.  
**Icon:** small sun/compass/observation center represented as a central dot with one incomplete orbit and one accent point; abstract enough to stay distinctive.
**Welcome 2.0s storyboard:** 0–450ms single warm luminous dot; 450–1150ms two soft arcs appear; 1150–1600ms mark settles as ring with sage/amber cue; 1600–2000ms product wordmark fades into stable layout. On subsequent opens show direct content.
**Today:** calm greeting and date, large Upcoming focus card with readable time, compact two-row routine queue and unresolved row, gentle progress statement when enough explicit records exist.
**Track:** illustrated *icons* only where useful, tidy tracker tiles with clear units, large Log control and concise recent history.
**Insights:** tasteful soft-fill charts with labeled axes, a compact 28d analysis and short, source-linked guidance. No clutter or fake cheerleading.
**Potential issue:** balance warmth with visibility under dark mode or busy data; ensure sage/gold contrast does not carry status alone.

## C. Living Signal — future-facing intelligence

**Personality:** adaptive, energetic, ambitious, sophisticated.  
**Visual signature:** ink/navy depth, precision grids, violet/cyan signal points, slow restrained optical transitions.  
**Color tokens (concept-only):**
```
ink #F3F3FA
canvas_light #F5F5FB
canvas_dark #101528
surface_light #FFFFFF
surface_dark #1B2238
primary #A590FF
muted #9099B2
signal #78DCCF
positive #5FC7AE
warning #F0B67F
outline #343D57
```
**Typography:** contemporary geometric/system sans; strong but compact hierarchy, disciplined numeric alignment.  
**Icon:** two flowing signal paths converging on a single anchored focus point; legible with effects removed.
**Welcome 2.0s storyboard:** 0–400ms two signal particles enter; 400–1050ms strands converge; 1050–1550ms lock into a simple glyph; 1550–2000ms gradient gently resolves and wordmark appears. Motion stays below 60fps budget under test and honors reduced motion.
**Today:** dark primary focus surface with one purple-labeled upcoming reminder, grouped status chips and a slim mint activity accent; avoid gaming-style dashboards.
**Track:** clean dark list and vivid log control, one persistent quick capture; trend visuals only inside tracker details.
**Insights:** signal line graph and carefully placed small confidence/coverage annotations; transparent evidence links rather than AI mystique.
**Potential issue:** dark complexity can distract from urgent reminders and consume more energy. Gate flashy effects behind motion/performance settings.

## Shared welcome motion technical contract (design, not implementation)

- Zero blocking splash on deep links from notifications, incoming system intents, import dialogs or critical restore recovery.
- Start-time budget target: **up to 2 seconds only on voluntary first launch**; app content should be ready regardless of intro completion.
- Prefer animating opacity/transform of a small number of vector elements. Avoid huge particles, expensive blur, heavy GPU layers or continuous effects.
- Respect iOS Reduce Motion and Android Remove Animations; screen readers speak the product title once. Offer skip when appropriate.
- Export deliverables: icon SVG source, vector timeline, Android and iOS raster exports, static fallback, monochrome notification glyph, 2x/3x sizes and QA captures.

## Screen references and content model for comparable concepts

### Today
- Header: `Sat, 24 October`
- Hero: `Read for 20 minutes`, `7:30 PM`, action `Open`
- Next: `Pay electricity bill`, `Tomorrow`
- Unresolved: `Morning walk`, `No response recorded` (not framed as failed)
- Action: `+` to add reminder or activity

### Track
- `Reading` (duration min)
- `Water` (count)
- `Mood check-in` (self-reported check-in)
- Recent log: `Read 18 min`; clear edit/correction option
- Action: `Log activity`

### Insights
- Selector: `7d · 28d · 90d`
- Showcase both `Confirmed complete` and `Unresolved` based on sample fixture
- `Reading time` in min/week
- One linkable observation: `Reading logged on 4 of 7 days` with a view-records action; describe as factual, not motivational pressure
- Source item count and time-window notation in expanded detail.

These are **synthetic demonstration records only**, not the user's real entries or current app data.

## Selection scorecard

Score each direction 1–5 on:
1. Instantly understandable Today screen
2. Premium but distinctive brand mark at 48px
3. Readability and accessibility, light/dark
4. Clear reminder urgency vs unresolved distinction
5. Deep but uncluttered Track and Insights
6. Motion attractiveness vs launch/recovery speed
7. Android/iOS feasibility on mid-range devices
8. Longevity once real analytics and goal data expand

**Recommendation to evaluate first:** Warm Observatory. No direction is selected yet. After selection: lock icon and token variants; produce all-screen high fidelity layouts, animation keyframes, navigation prototype, accessibility tests and R3 data-safety gate before app migration.

## Decision status

- Brand name: OPEN
- Visual direction: OPEN
- App icon: OPEN
- Splash/welcome motion: OPEN
- Three illustrative screen concepts: SPECIFIED, not coded
- Redesigned native screens: NOT IMPLEMENTED
- V0.5e safe portable import: NOT IMPLEMENTED
