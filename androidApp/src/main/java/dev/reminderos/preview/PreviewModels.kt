package dev.reminderos.preview

/**
 * Native Android test-harness contracts, intentionally isolated from the KMP module
 * until a production Android gateway bridge and compatible module build are validated.
 */
internal enum class PreviewDeliveryCapability { PRECISE, APPROXIMATE_ONLY, NOT_AUTHORIZED, UNAVAILABLE }
internal data class PreviewCapabilities(val capability: PreviewDeliveryCapability, val note: String? = null)
internal data class PreviewAlert(val occurrenceId: String, val fireAtEpochMillis: Long, val revision: Long)
internal data class PreviewRegistration(
    val platformHandle: String,
    val occurrenceId: String,
    val fireAtEpochMillis: Long,
    val revision: Long,
)
internal sealed class PreviewScheduleResult {
    data class Accepted(val platformHandle: String) : PreviewScheduleResult()
    data class Rejected(val reasonCode: String) : PreviewScheduleResult()
}
internal interface PreviewScheduleGateway {
    fun capabilities(): PreviewCapabilities
    fun schedule(intent: PreviewAlert): PreviewScheduleResult
    fun cancel(registration: PreviewRegistration): Boolean
}
