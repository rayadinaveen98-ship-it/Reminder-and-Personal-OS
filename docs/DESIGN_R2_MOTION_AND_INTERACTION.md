# R2 — Identity, Welcome Motion & Interaction Storyboard

**Status:** Proposed sequence for selected Quiet Precision. Only the **style direction** has owner approval. Exact commercial name and vector mark are candidates.

## Identity proposal

Abstract focus/orbit symbol: precision circular outline, short inward pointer and an offset small orbiting dot. Intended associations: time, attention, direction, returning to what matters. A one-color variant must remain identifiable at notification glyph sizes and adapt to Android adaptive masks and iOS artwork. Avoid copied clock-brand silhouettes and do not use illustrative avatars from early generated boards as final product assets.

Deliverable variants for implementation: adaptive icon foreground/background and monochrome, iOS 1024px master, splash static vector, high contrast variant, dark/light contrast previews, notification status glyph (Android monochrome). Run distinctiveness/brand search before public naming. User can approve the mark separately from visual direction.

## Intro motion: target 1.8 seconds ONLY on first voluntary launch

| Time | Visual state | Accessibility |
|---|---|---|
| 0–250ms | Ink canvas; one softly illuminated point enters | Static image when reduce-motion on |
| 250–1050ms | Thin orbital circle draws with an ease-out; subtle pointer resolves | No strobing, no audio by default |
| 1050–1500ms | Satellite dot moves to precise resting coordinate; background fades to app canvas | No decorative particle storm |
| 1500–1800ms | Working-name wordmark fades once; transition into optional welcome | Screen reader receives one descriptive title |

**Launch semantics:** Do not deliberately hold ordinary cold starts for animation. Skip immediately for notification tap, action callback, restored session, import/restore error recovery, deep links and urgent reminder details. Respect iOS Reduce Motion and Android remove-animations settings. No animation should be required to understand the UI or take an action. The proposed timeline is subject to mid-range device and OS animation testing.

## Micro-interaction budget

- Primary-button press: visual response by next frame; pressed affordance scales 0.98 maximum, approximately 100ms; no excessive haptics.
- Add/save: a tiny confirmation transition under 180ms after durable write; show progress/errors honestly. **Never show Saved before store acknowledgment**.
- Reminder Done: 140–200ms confirmation; pending UI only when write is in flight; transient Undo only where domain correction is reversible and audited.
- Snooze: choose duration, confirm chosen next time and schedule eligibility; do not silently mark Done.
- Skip: explicit label, corrective edit accessible from history.
- Navigation: subtle fade/slide ~130–180ms, honor reduced motion; no transition should block a notification deep link.
- Graph range: short 150–200ms transition with axis/denominator labels stable; avoid charts spinning or resetting while changing 7/28/90.
- Bottom sheet: native-feeling detent, keyboard-safe and screen-reader accessible; dismissible unless a critical export/error resolution would lose required context.
- Long lists: render efficiently, no repeated explanatory content.
- Background: static; avoid constant GPU-heavy motion, large particle fields, continuous blur or autoplay media.

## Native accessibility and timing rules

- Stable reading order, announce outcome changes via platform accessibility API only after confirmed state.
- Expose a static intro alternative; never make motion the only conveyance.
- Android controls have ≥48dp accessible hit region; inspect iOS platform guidance and test dynamic text on device.
- Record timing on low/mid-range Android and tested iPhone; visual animation aesthetic never outranks reliable due time.

## Review states

Storyboard must eventually be approved with actual rendered frames at t=0/250/1050/1500/1800; verify both dark/light and motion-reduced behavior. HTML design study currently demonstrates the **static/animated concept only**. No native splash binary or OS cold-start measurement is provided at R2.
