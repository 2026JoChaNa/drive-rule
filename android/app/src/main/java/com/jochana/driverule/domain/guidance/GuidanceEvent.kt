package com.jochana.driverule.domain.guidance

/** An immutable event snapshot. Elapsed time is monotonic and local to this boot. */
data class GuidanceEvent(
    val id: String,
    val title: String,
    val message: String,
    val expiresAtElapsedMs: Long,
)

/**
 * A single output slot. Implementations post/cancel quickly and do not call the coordinator.
 * show=true means accepted/posted, never proof that the user saw the card.
 * Hosts may call stop during destruction, so dismiss must be safe and idempotent.
 */
interface GuidanceCardOutput {
    fun show(event: GuidanceEvent): Boolean
    fun dismiss()
}

enum class GuidanceSessionEndReason { Stopped, TimedOut }

data class GuidanceRuntimeState(
    val running: Boolean = false,
    val postedEvent: GuidanceEvent? = null,
    val sessionEndsAtElapsedMs: Long? = null,
)
