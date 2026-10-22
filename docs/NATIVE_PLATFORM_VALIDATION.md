# Native Platform Foundation — Build Validation Plan

**Date:** 2026-10-22  
**Stage:** KMP Gradle scaffold committed. Compilers, native APIs and end-user notification delivery must be validated.

## Stack selection (provisional)
- Kotlin Multiplatform Gradle plugin: 2.4.20
- Android Gradle Plugin: 9.3.1 (new Android KMP library plugin)
- Gradle: 9.5.0 (AGP 9.3 requires Gradle >= 9.5; compatible with Kotlin 2.4.20)
- Java: 21
- Android compileSDK: 36, minSDK candidate: 26
- Targets: Android KMP library, JVM smoke/test, iOS Arm64 + iOS Simulator Arm64
- No Compose UI, Room persistence, Android app shell or Xcode iOS app project yet.

The prototype Kotlin sources are included in `:shared` via a source-directory reference to the existing spike, avoiding divergent copies. Tests use `kotlin.test`.

Official documentation: 
- https://developer.android.com/kotlin/multiplatform/plugin
- https://developer.android.com/build/releases/about-agp
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html

## CI checks requested
- Linux: `gradle :shared:jvmTest :shared:compileKotlinJvm`
- Linux + Android SDK 36: `gradle :shared:compileAndroidMain`
- macOS + Xcode: `gradle :shared:compileKotlinIosSimulatorArm64`

Configured in `.github/workflows/kmp-foundation.yml`. CI creation is not proof of a successful build; read the resulting GitHub Actions run before marking verified. GitHub Actions workflow installation may require additional repository permissions.

## Native scheduling boundary

`NativeScheduleGateway` is an interface, not an alarm engine:
- `capabilities()` reports precise/approximate/denied/unavailable; never implies a presented alert.
- `schedule(intent)` accepts a resolved absolute instant and returns an opaque platform handle or error code.
- `cancel(registration)` must report failure as false.
- `executeReconciliation(...)` runs cancellations before registrations and blocks replacing an alert if cancellation failed.

This executor provides **best effort** only. It is not transactional; retry after process death requires durable operation journal, stable native handles, and a reconciliation pass. Implementing those requires selecting persistence and native adapters.

## Next native implementation steps
1. Make CI compile on both platforms and correct all configuration errors, documenting exact run SHA.
2. Add Android app entry point, broadcast receiver and AlarmManager adapter with explicit notification/exact-alarm permission checks and restart reconciliation.
3. Add iOS Xcode app project and UNUserNotificationCenter adapter with action categories and a bounded local notification queue.
4. Integrate durable registration repository and error replay; unit test against in-memory native adapter and physical devices.
5. Choose portable time-zone resolution policy and verify DST gap/fold behaviour in both targets.
6. Record device-specific failure modes. Do not present app as emergency or medical-grade alarm.

## Limitations
A CI compile check is not a real-world notification reliability test. Neither OS guarantees delivery while the device is powered down, and permissions/system settings can suppress or defer alerts. We will not claim a functional Android APK/iOS IPA until actual app entry points and signed builds exist.
