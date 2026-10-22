# Cross-Platform Technical Feasibility — Initial Spike

**Date:** 2026-10-22  
**Status:** PARTIALLY VALIDATED — a JVM-only dependency-free core smoke test passed. Android/iOS builds and runtime delivery are NOT validated.

## Goal

Check whether the project can share reminder outcomes and analytics between Android and iOS while isolating platform notification delivery. Identify blocking toolchain and platform issues before producing a production scaffold.

## Documentation-grounded findings

| Topic | Finding | Validation status |
|---|---|---|
| Kotlin Multiplatform (KMP) | Suitable candidate for sharing models and business logic; supported by Google/JetBrains | Documentation only; no KMP Gradle build run |
| Compose Multiplatform | JetBrains identifies Android and iOS UI support as stable | Candidate; accessibility/performance/platform UX untested |
| Shared local database | Android documentation provides official KMP Room setup and driver options | Feasible candidate; Room vs SQLDelight not benchmarked |
| Android scheduling | AlarmManager offers exact paths subject to permissions and policy; special alarm access can be denied by default | Native adapter not implemented/tested |
| iOS local notifications | Apple's UNUserNotificationCenter provides system-scheduled local alerts when an app is backgrounded/not running | Native adapter not implemented/tested; Focus/permission/system limits remain |
| iOS builds | Mac and Xcode required to build/test iOS framework/app | Not available in this execution environment |
| Android builds | Android SDK/Gradle required | Neither installed in this execution environment |
| JVM smoke compilation | Kotlin 1.9.0 CLI and Java 21 installed | **PASS: 16 checks** on this Linux environment |

## Relevant primary documentation (checked 2026-10-22)

- Kotlin release / current versions: https://kotlinlang.org/docs/releases.html
- KMP quickstart and Xcode requirement: https://kotlinlang.org/docs/multiplatform/quickstart.html
- Compose Multiplatform stability: https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Android-backed KMP business logic support: https://developer.android.com/kotlin/multiplatform
- Room KMP setup: https://developer.android.com/kotlin/multiplatform/room
- Android exact-alarm permissions: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Apple local notifications: https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app

**Version caution:** Kotlin 2.4.21 is a recent stable release per official release notes, but this environment has Kotlin 1.9.0. Do not claim that the eventual KMP 2.4.x Gradle plugin, Compose, Android Gradle Plugin, KSP and Room versions were compatibility-tested here. Pin a mutually compatible set only after build verification.

## Actual executable spike

The source under `spikes/core-model/src/commonMain` uses Kotlin language/standard-library features only; it deliberately avoids Java time and Android framework APIs. It has been compiled **as JVM Kotlin**, not as a KMP target or iOS framework.

The smoke runner exercises:
1. Acknowledgement does not equal completion.
2. Duplicate action ID is idempotent.
3. Invalid snooze rejected.
4. Valid snooze leaves outcome UNKNOWN.
5. Done clears an outstanding snooze.
6. Resolved occurrence cannot silently switch outcomes.
7. Reopen produces an auditable correction.
8. Reopen duplicated ID is idempotent.
9. Explicit skip stored correctly.
10. Period filtering uses original due time.
11. Coverage / unknown proportions use scheduled denominator.
12. Known-outcome completion excludes unknown.
13. Empty samples produce null rather than misleading rates.
14. Duplicate occurrence IDs rejected.
15. Invalid reporting window rejected.
16. Acknowledge action accepted.

Run where `java` and `kotlinc` are available:
```sh
sh scripts/run_core_smoke.sh
```
Observed locally:
```text
PASS: 16 reminder core smoke checks (Kotlin JVM only)
```

This is **not** proof of safe transactional persistence, Android/iOS compilation, alarms or accurate OS delivery.

## Architecture recommendation after initial spike

- Continue with **KMP shared domain models and deterministic rules** as a candidate.
- Keep scheduler platform adapters separate. Share high-level desired state and capability/diagnostic contracts, not execution assumptions.
- Evaluate Room KMP and SQLDelight against schema migrations, encryption, query performance, backup integrity and iOS packaging before committing.
- Evaluate Compose Multiplatform against a native SwiftUI screen on iPhone for UX, VoiceOver, Dynamic Type, gestures, performance and binary size. Keep the option for iOS-native screens if shared UI has unacceptable trade-offs.
- Require actual Android/iOS builds and physical-device notification tests before marking the architecture validated.

## Blocking validation work (next)

1. Confirm supported Android/iOS version floors and set up compatible Gradle/Kotlin/AGP/Compose versions.
2. Obtain Android SDK and a macOS/Xcode build runner; verify `commonMain` compiles for `iosArm64` and `iosSimulatorArm64`.
3. Implement minimal Android notification adapter and iOS UNUserNotificationCenter adapter, then device-test platform constraints.
4. Choose KMP database library with a migration + restore round trip.
5. Verify recurrence/time-zone gap/fold semantics with a calendar/time library supporting both platforms.
6. Add Gradle-based common unit tests, build CI matrix, and a device reliability evidence log.

## Current status

- **PASS** — JVM smoke of portable reminder outcome reducer and percentage calculations.
- **PENDING** — actual KMP compilation, native notifications, permission flows, persistence, iOS & Android app builds, design system.
- No production app or binary exists. This is research + isolated proof-of-concept.
