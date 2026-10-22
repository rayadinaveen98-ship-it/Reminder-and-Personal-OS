package dev.reminderos.core

/**
 * An OS-neutral planner for aligning persisted reminder intentions with OS registrations.
 * The planner has no side effects. Execute commands via an Android or iOS adapter and
 * persist results transactionally; on failure the next reconciliation may retry.
 */
data class AlertIntent(val occurrenceId: String, val fireAtEpochMillis: Long, val revision: Long) {
    init {
        require(occurrenceId.isNotBlank())
        require(revision >= 0L)
    }
}

data class PlatformRegistration(
    val platformHandle: String,
    val occurrenceId: String,
    val fireAtEpochMillis: Long,
    val revision: Long,
) {
    init {
        require(platformHandle.isNotBlank())
        require(occurrenceId.isNotBlank())
        require(revision >= 0L)
    }
}

sealed class ScheduleCommand {
    data class Cancel(val registration: PlatformRegistration) : ScheduleCommand()
    data class Register(val intent: AlertIntent) : ScheduleCommand()
}

data class ReconciliationPlan(val commands: List<ScheduleCommand>, val unchanged: Int) {
    val cancellations: List<ScheduleCommand.Cancel> get() = commands.filterIsInstance<ScheduleCommand.Cancel>()
    val registrations: List<ScheduleCommand.Register> get() = commands.filterIsInstance<ScheduleCommand.Register>()
}

/**
 * Removes stale and duplicate OS registrations; schedules missing or revised intents.
 * Cancellation commands precede registration commands. Equal inputs yield no work.
 * Caller must supply only desired, eligible future alerts, including applicable snoozes.
 */
fun planReconciliation(
    desired: List<AlertIntent>,
    registered: List<PlatformRegistration>,
): ReconciliationPlan {
    require(desired.map { it.occurrenceId }.toSet().size == desired.size) {
        "Multiple active intents for the same occurrence"
    }
    require(registered.map { it.platformHandle }.toSet().size == registered.size) {
        "Duplicate native platform handles"
    }
    val existingByOccurrence = registered.groupBy { it.occurrenceId }
    val desiredByOccurrence = desired.associateBy { it.occurrenceId }
    val cancellations = mutableListOf<ScheduleCommand.Cancel>()
    val additions = mutableListOf<ScheduleCommand.Register>()
    var unchanged = 0

    for ((occurrenceId, existing) in existingByOccurrence) {
        val intent = desiredByOccurrence[occurrenceId]
        if (intent != null && existing.size == 1 &&
            existing.single().fireAtEpochMillis == intent.fireAtEpochMillis &&
            existing.single().revision == intent.revision) {
            unchanged++
        } else {
            existing.forEach { cancellations.add(ScheduleCommand.Cancel(it)) }
        }
    }
    for (intent in desired) {
        val existing = existingByOccurrence[intent.occurrenceId].orEmpty()
        val valid = existing.size == 1 && existing.single().fireAtEpochMillis == intent.fireAtEpochMillis &&
            existing.single().revision == intent.revision
        if (!valid) additions.add(ScheduleCommand.Register(intent))
    }
    return ReconciliationPlan(
        commands = cancellations.sortedBy { it.registration.platformHandle } +
            additions.sortedWith(compareBy({ it.intent.fireAtEpochMillis }, { it.intent.occurrenceId })),
        unchanged = unchanged,
    )
}
