package dev.reminderos.core

/**
 * Platform-independent reminder outcomes. A notification firing or being dismissed
 * never changes UNKNOWN to DONE or SKIPPED.
 */
enum class Outcome { UNKNOWN, DONE, SKIPPED }

enum class ActionKind { DONE, SKIP, SNOOZE, ACKNOWLEDGE, REOPEN }

data class Action(
    val id: String,
    val kind: ActionKind,
    val atEpochMillis: Long,
    val snoozeUntilEpochMillis: Long? = null,
)

data class RecordedAction(val id: String, val kind: ActionKind, val atEpochMillis: Long)

data class OccurrenceState(
    val id: String,
    val dueAtEpochMillis: Long,
    val outcome: Outcome = Outcome.UNKNOWN,
    val acknowledged: Boolean = false,
    val snoozedUntilEpochMillis: Long? = null,
    val recordedActions: List<RecordedAction> = emptyList(),
)

sealed class ApplyResult {
    data class Applied(val next: OccurrenceState) : ApplyResult()
    data class Duplicate(val unchanged: OccurrenceState) : ApplyResult()
    data class Rejected(val reason: String) : ApplyResult()
}

/** Pure reducer; caller must persist action identity and state in a single transaction. */
fun applyAction(state: OccurrenceState, action: Action): ApplyResult {
    if (action.id.isBlank()) return ApplyResult.Rejected("Action ID must not be blank")
    if (state.recordedActions.any { it.id == action.id }) return ApplyResult.Duplicate(state)

    val next = when (action.kind) {
        ActionKind.DONE -> {
            if (state.outcome != Outcome.UNKNOWN) return ApplyResult.Rejected("Already resolved")
            state.copy(outcome = Outcome.DONE, snoozedUntilEpochMillis = null)
        }
        ActionKind.SKIP -> {
            if (state.outcome != Outcome.UNKNOWN) return ApplyResult.Rejected("Already resolved")
            state.copy(outcome = Outcome.SKIPPED, snoozedUntilEpochMillis = null)
        }
        ActionKind.SNOOZE -> {
            if (state.outcome != Outcome.UNKNOWN) return ApplyResult.Rejected("Already resolved")
            val until = action.snoozeUntilEpochMillis
                ?: return ApplyResult.Rejected("Snooze target required")
            if (until <= action.atEpochMillis) return ApplyResult.Rejected("Snooze must be in future")
            state.copy(snoozedUntilEpochMillis = until)
        }
        ActionKind.ACKNOWLEDGE -> {
            if (state.outcome != Outcome.UNKNOWN) return ApplyResult.Rejected("Already resolved")
            state.copy(acknowledged = true)
        }
        ActionKind.REOPEN -> {
            if (state.outcome == Outcome.UNKNOWN) return ApplyResult.Rejected("Already unresolved")
            state.copy(outcome = Outcome.UNKNOWN, snoozedUntilEpochMillis = null)
        }
    }
    return ApplyResult.Applied(
        next.copy(recordedActions = next.recordedActions +
            RecordedAction(action.id, action.kind, action.atEpochMillis))
    )
}

data class CompletionSummary(
    val eligibleCount: Int,
    val doneCount: Int,
    val skippedCount: Int,
    val unknownCount: Int,
    val confirmedCompletionCoverage: Double?,
    val knownOutcomeCompletionRate: Double?,
    val unknownShare: Double?,
)

/** Window is [startInclusive, endExclusive); original due time selects membership. */
fun summarize(
    occurrences: List<OccurrenceState>,
    startInclusiveEpochMillis: Long,
    endExclusiveEpochMillis: Long,
): CompletionSummary {
    require(endExclusiveEpochMillis > startInclusiveEpochMillis) { "Invalid period" }
    require(occurrences.map { it.id }.toSet().size == occurrences.size) { "Duplicate occurrence ID" }
    val due = occurrences.filter { it.dueAtEpochMillis >= startInclusiveEpochMillis &&
        it.dueAtEpochMillis < endExclusiveEpochMillis }
    val done = due.count { it.outcome == Outcome.DONE }
    val skipped = due.count { it.outcome == Outcome.SKIPPED }
    val unknown = due.size - done - skipped
    val known = done + skipped
    return CompletionSummary(
        eligibleCount = due.size,
        doneCount = done,
        skippedCount = skipped,
        unknownCount = unknown,
        confirmedCompletionCoverage = if (due.isEmpty()) null else done.toDouble() / due.size,
        knownOutcomeCompletionRate = if (known == 0) null else done.toDouble() / known,
        unknownShare = if (due.isEmpty()) null else unknown.toDouble() / due.size,
    )
}
