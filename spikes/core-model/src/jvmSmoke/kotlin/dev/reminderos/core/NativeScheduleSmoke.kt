package dev.reminderos.core

private var checks = 0
private fun verify(condition: Boolean, label: String) { check(condition) { label }; checks++ }

private class Fake : NativeScheduleGateway {
    val events = mutableListOf<String>()
    var failCancel = false
    var failRegister = false
    override fun capabilities() = SchedulerCapabilities(DeliveryCapability.PRECISE)
    override fun cancel(registration: PlatformRegistration): Boolean {
        events.add("cancel:" + registration.occurrenceId)
        return !failCancel
    }
    override fun schedule(intent: AlertIntent): PlatformScheduleResult {
        events.add("register:" + intent.occurrenceId)
        return if (failRegister) PlatformScheduleResult.Rejected("DENIED")
            else PlatformScheduleResult.Accepted("os-" + intent.occurrenceId)
    }
}

fun main() {
    val reg = PlatformRegistration("handle", "o", 100, 1)
    val intent = AlertIntent("o", 200, 2)
    val plan = planReconciliation(listOf(intent), listOf(reg))
    val denied = Fake().apply { failCancel = true }
    val blocked = executeReconciliation(plan, denied)
    verify(denied.events == listOf("cancel:o"), "do not register if cancel failed")
    verify(blocked.failedOperations == listOf("CANCEL_FAILED:o", "REGISTER_BLOCKED:o"), "record both failures")
    verify(blocked.attemptedCancellations == 1 && blocked.completedCancellations == 0, "cancellation metrics")
    verify(blocked.attemptedRegistrations == 0 && blocked.acceptedRegistrations.isEmpty(), "no attempt to register")
    verify(!blocked.fullyApplied, "blocked is not full success")
    val success = Fake()
    val applied = executeReconciliation(plan, success)
    verify(success.events == listOf("cancel:o", "register:o"), "cancel before replace")
    verify(applied.fullyApplied && applied.completedCancellations == 1, "all succeeded")
    verify(applied.acceptedRegistrations.single().fireAtEpochMillis == 200L, "correct deadline")
    val failed = Fake().apply { failRegister = true }
    val noReg = executeReconciliation(planReconciliation(listOf(intent), emptyList()), failed)
    verify(noReg.failedOperations == listOf("REGISTER_FAILED:o:DENIED"), "native rejection preserved")
    verify(noReg.acceptedRegistrations.isEmpty() && !noReg.fullyApplied, "not falsely successful")
    val idle = executeReconciliation(planReconciliation(listOf(intent),
        listOf(PlatformRegistration("h", "o", 200, 2))), Fake())
    verify(idle.fullyApplied && idle.attemptedRegistrations == 0 &&
        idle.attemptedCancellations == 0, "idempotent no-op")
    val separate = executeReconciliation(
        planReconciliation(listOf(intent, AlertIntent("other", 400, 1)), listOf(reg)), denied)
    verify(separate.acceptedRegistrations.single().occurrenceId == "other",
        "one cancel failure doesn't block unrelated")
    println("PASS: $checks native gateway JVM smoke checks")
}
