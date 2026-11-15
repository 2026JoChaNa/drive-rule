package com.jochana.driverule.core.platform.audio

import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSpeechOutput
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/** One SDK connection per utterance: SDK callbacks have no utterance identifier. */
internal interface SpeechSession {
    fun start(text: String, onResult: (GuidanceSpeechResult) -> Unit)
    /** Must be idempotent, thread-safe, and release focus/listeners as well as playback. */
    fun release()
}

/** Callback ownership is independent of Android so cancellation/replacement can be tested. */
internal class SpeechSessionOutput(private val createSession: () -> SpeechSession) : GuidanceSpeechOutput {
    private val lock = Any()
    private var active: Attempt? = null
    private var closed = false

    override suspend fun speak(event: GuidanceEvent): GuidanceSpeechResult = suspendCancellableCoroutine { continuation ->
        synchronized(lock) {
            active?.let { finish(it, GuidanceSpeechResult.Interrupted) }
            if (closed || event.speechText.isBlank()) {
                continuation.resume(GuidanceSpeechResult.Unavailable)
                return@synchronized
            }
            val session = try {
                createSession()
            } catch (_: RuntimeException) {
                continuation.resume(GuidanceSpeechResult.Unavailable)
                return@synchronized
            } catch (_: LinkageError) {
                continuation.resume(GuidanceSpeechResult.Unavailable)
                return@synchronized
            }
            val attempt = Attempt(session, continuation)
            active = attempt
            continuation.invokeOnCancellation {
                synchronized(lock) { if (active === attempt) finish(attempt, null) }
            }
            if (active !== attempt || !continuation.isActive) return@synchronized
            try {
                session.start(event.speechText) { result ->
                    synchronized(lock) { if (active === attempt) finish(attempt, result) }
                }
            } catch (_: RuntimeException) {
                if (active === attempt) finish(attempt, GuidanceSpeechResult.Unavailable)
            } catch (_: LinkageError) {
                if (active === attempt) finish(attempt, GuidanceSpeechResult.Unavailable)
            }
        }
    }

    override fun stop() = synchronized(lock) {
        active?.let { finish(it, GuidanceSpeechResult.Interrupted) }
        Unit
    }

    override fun close() = synchronized(lock) {
        closed = true
        active?.let { finish(it, GuidanceSpeechResult.Interrupted) }
        Unit
    }

    private fun finish(attempt: Attempt, result: GuidanceSpeechResult?) {
        active = null // Invalidate before stop/release callbacks can run synchronously.
        try {
            attempt.session.release()
        } catch (_: RuntimeException) {
            // The platform session performs best-effort cleanup of each resource separately.
        } catch (_: LinkageError) {
            // An unavailable platform must not strand the caller or restore an old utterance.
        }
        if (result != null && attempt.continuation.isActive) attempt.continuation.resume(result)
    }

    private class Attempt(val session: SpeechSession, val continuation: CancellableContinuation<GuidanceSpeechResult>)
}
