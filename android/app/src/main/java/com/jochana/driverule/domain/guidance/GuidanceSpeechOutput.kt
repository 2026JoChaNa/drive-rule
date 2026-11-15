package com.jochana.driverule.domain.guidance

/**
 * Independent voice slot. speak must suspend without blocking and cooperate with cancellation.
 * stop synchronously invalidates the active utterance, including pending platform initialization;
 * late callbacks and cancellation cleanup must never stop a newer utterance. stop/close are quick,
 * idempotent, thread-safe and non-throwing. Completion is not proof that the user heard/confirmed it.
 */
interface GuidanceSpeechOutput {
    suspend fun speak(event: GuidanceEvent): GuidanceSpeechResult
    fun stop()
    fun close()
}

enum class GuidanceSpeechResult { Completed, Unavailable, FocusDenied, Interrupted, Failed }

/** inProgress describes an attempt, not platform playback confirmation or user listening. */
data class GuidanceSpeechState(
    val eventId: String? = null,
    val inProgress: Boolean = false,
    val result: GuidanceSpeechResult? = null,
)

object UnavailableGuidanceSpeechOutput : GuidanceSpeechOutput {
    override suspend fun speak(event: GuidanceEvent) = GuidanceSpeechResult.Unavailable
    override fun stop() = Unit
    override fun close() = Unit
}
