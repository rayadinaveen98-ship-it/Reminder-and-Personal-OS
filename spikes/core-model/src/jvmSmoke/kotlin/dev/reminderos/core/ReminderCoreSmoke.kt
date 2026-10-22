package dev.reminderos.core

private var checks = 0
private fun checkThat(condition: Boolean, description: String) {
    check(condition) { "FAILED: $description" }
    checks++
}

fun main() {
    val original = OccurrenceState("occ-1", dueAtEpochMillis = 1_000)
    val acknowledged = applyAction(original, Action("a1", ActionKind.ACKNOWLEDGE, 1_100))
    checkThat(acknowledged is ApplyResult.Applied, "acknowledge accepted")
    val afterAck = (acknowledged as ApplyResult.Applied).next
    checkThat(afterAck.outcome == Outcome.UNKNOWN && afterAck.acknowledged,
        "acknowledge never implies completion")
    checkThat(applyAction(afterAck, Action("a1", ActionKind.ACKNOWLEDGE, 1_100)) is ApplyResult.Duplicate,
        "duplicate action identity ignored")
    checkThat(applyAction(afterAck, Action("bad", ActionKind.SNOOZE, 1_100, 1_100)) is ApplyResult.Rejected,
        "invalid snooze rejected")
    val snoozed = (applyAction(afterAck,
        Action("s1", ActionKind.SNOOZE, 1_100, 2_000)) as ApplyResult.Applied).next
    checkThat(snoozed.outcome == Outcome.UNKNOWN && snoozed.snoozedUntilEpochMillis == 2_000L,
        "snooze retains unknown outcome")
    val done = (applyAction(snoozed, Action("d1", ActionKind.DONE, 1_500)) as ApplyResult.Applied).next
    checkThat(done.outcome == Outcome.DONE && done.snoozedUntilEpochMillis == null,
        "Done clears pending snooze")
    checkThat(applyAction(done, Action("skip2", ActionKind.SKIP, 1_600)) is ApplyResult.Rejected,
        "resolved occurrence cannot become skipped without reopening")
    val reopened = (applyAction(done, Action("r1", ActionKind.REOPEN, 1_800)) as ApplyResult.Applied).next
    checkThat(reopened.outcome == Outcome.UNKNOWN && reopened.recordedActions.size == 4,
        "reopen retains audit-relevant action history")
    checkThat(applyAction(reopened, Action("r1", ActionKind.REOPEN, 1_800)) is ApplyResult.Duplicate,
        "duplicate reopen idempotent")
    val skipped = (applyAction(reopened, Action("s2", ActionKind.SKIP, 1_900)) as ApplyResult.Applied).next
    checkThat(skipped.outcome == Outcome.SKIPPED, "explicit skip only")

    val ten = listOf(
        *Array(6) { i -> OccurrenceState("done-$"+"i", 1_000, Outcome.DONE) },
        OccurrenceState("skip", 1_000, Outcome.SKIPPED),
        *Array(3) { i -> OccurrenceState("unknown-$"+"i", 1_000) },
        OccurrenceState("outside", 3_000, Outcome.DONE),
    )
    val summary = summarize(ten, 0, 2_000)
    checkThat(summary.eligibleCount == 10 && summary.doneCount == 6 &&
        summary.skippedCount == 1 && summary.unknownCount == 3, "eligible outcome counts")
    checkThat(summary.confirmedCompletionCoverage == 0.6 && summary.unknownShare == 0.3,
        "completion coverage and missing-data rate")
    checkThat(kotlin.math.abs((summary.knownOutcomeCompletionRate ?: 0.0) - 6.0 / 7.0) < 0.000001,
        "known outcome denominator excludes unknown")
    val empty = summarize(emptyList(), 0, 1)
    checkThat(empty.confirmedCompletionCoverage == null &&
        empty.knownOutcomeCompletionRate == null && empty.unknownShare == null,
        "no data produces null rates")
    checkThat(runCatching { summarize(listOf(original, original), 0, 2_000) }.isFailure,
        "duplicate occurrence identity rejected")
    checkThat(runCatching { summarize(emptyList(), 5, 5) }.isFailure,
        "invalid period rejected")
    println("PASS: $"+"checks reminder core smoke checks (Kotlin JVM only)")
}
