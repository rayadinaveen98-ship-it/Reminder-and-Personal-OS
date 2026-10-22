package dev.reminderos.core

/**
 * Defines desired OS scheduling operations; implementations live in Android and iOS
 * platform layers. A successful call means OS registration accepted, never delivery.
 */
enum class DeliveryCapability { PRECISE, APPROXIMATE_ONLY, NOT_AUTHORIZED, UNAVAILABLE }

data class SchedulerCapabilities(
    val capability: DeliveryCapability,
    val note: String? = null,
)

sealed class PlatformScheduleResult {
    data class Accepted(val platformHandle: String) : PlatformScheduleResult() {
        init { require(platformHandle.isNotBlank()) }
    }
    data class Rejected(val reasonCode: String) : PlatformScheduleResult() {
        init { require(reasonCode.isNotBlank()) }
    }
}

/**
 * The gateway takes already resolved absolute instants. Native platforms must apply
 * their permissions, notification actions, and scheduling restrictions.
 */
interface NativeScheduleGateway {
    fun capabilities(): SchedulerCapabilities
    fun schedule(intent: AlertIntent): PlatformScheduleResult
    fun cancel(registration: PlatformRegistration): Boolean
}

data class ExecutionReport(
    val attemptedCancellations: Int,
    val completedCancellations: Int,
    val attemptedRegistrations: Int,
    val acceptedRegistrations: List<PlatformRegistration>,
    val failedOperations: List<String>,
) {
    val fullyApplied: Boolean get() = failedOperations.isEmpty()
}

/**
 * Best-effort side-effect adapter for a pure ReconciliationPlan. Caller persists each
 * accepted result and retries failures; this class is not itself crash-atomic.
 *
 * If cancellation fails, do not register an overlapping replacement for the same
 * occurrence, to avoid two active alerts during partial platform failure.
 */
fun executeReconciliation(
    plan: ReconciliationPlan,
    gateway: NativeScheduleGateway,
): ExecutionReport {
    val failures = mutableListOf<String>()
    val blockedOccurrences = mutableSetOf<String>()
    var completedCancellations = 0
    val accepted = mutableListOf<PlatformRegistration>()

    for (command in plan.cancellations) {
        val success = gateway.cancel(command.registration)
        if (success) {
            completedCancellations++
        } else {
            blockedOccurrences.add(command.registration.occurrenceId)
            failures.add("CANCEL_FAILED:" + command.registration.occurrenceId)
        }
    }
    var attemptedRegistrations = 0
    for (command in plan.registrations) {
        val intent = command.intent
        if (intent.occurrenceId in blockedOccurrences) {
            failures.add("REGISTER_BLOCKED:" + intent.occurrenceId)
            continue
        }
        attemptedRegistrations++
        when (val result = gateway.schedule(intent)) {
            is PlatformScheduleResult.Accepted ->
                accepted.add(
                    PlatformRegistration(result.platformHandle, intent.occurrenceId,
                        intent.fireAtEpochMillis, intent.revision)
                )
            is PlatformScheduleResult.Rejected ->
                failures.add("REGISTER_FAILED:" + intent.occurrenceId + ":" + result.reasonCode)
        }
    }
    return ExecutionReport(
        attemptedCancellations = plan.cancellations.size,
        completedCancellations = completedCancellations,
        attemptedRegistrations = attemptedRegistrations,
        acceptedRegistrations = accepted,
        failedOperations = failures,
    )
}
