package com.jochana.driverule.app.guidance

import com.jochana.driverule.domain.guidance.GuidanceCardOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceEventPolicy
import com.jochana.driverule.domain.guidance.GuidanceRuntimeState
import com.jochana.driverule.domain.guidance.GuidanceSessionEndReason
import com.jochana.driverule.domain.guidance.GuidanceSpeechOutput
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import com.jochana.driverule.domain.guidance.GuidanceSpeechState
import com.jochana.driverule.domain.guidance.UnavailableGuidanceSpeechOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout

/**
 * Service-owned commands with serialized state/output changes. Timers and voice attempts use
 * the supplied scope/dispatcher; observing state cannot start an event or keep a service alive.
 * Output methods and onSessionEnded must return quickly and must not throw.
 */
class GuidanceCoordinator(
    private val scope: CoroutineScope,
    private val output: GuidanceCardOutput,
    private val elapsedRealtimeMs: () -> Long,
    private val dispatcher: CoroutineDispatcher,
    private val displayDurationMs: Long = 8_000,
    private val sessionDurationMs: Long = MAX_SESSION_DURATION_MS,
    private val onSessionEnded: (GuidanceSessionEndReason) -> Unit = {},
    private val speechOutput: GuidanceSpeechOutput = UnavailableGuidanceSpeechOutput,
) {
    private val lock = Any()
    private val policy = GuidanceEventPolicy()
    private val mutableState = MutableStateFlow(GuidanceRuntimeState())
    val state: StateFlow<GuidanceRuntimeState> = mutableState.asStateFlow()
    private var generation = 0L
    private var cardRevision = 0L
    private var cardTimer: Job? = null
    private var sessionTimer: Job? = null
    private var speechJob: Job? = null
    private var ownerWatcher: Job? = null
    private var closed = false

    init {
        require(displayDurationMs > 0) { "Display duration must be positive" }
        require(sessionDurationMs in 1L..MAX_SESSION_DURATION_MS) { "Session duration must be at most ten minutes" }
        // A sibling watcher can stop output on cancellation even while an obsolete adapter call
        // is unwinding. Waiting for the owner's final completion alone could defer that cleanup.
        ownerWatcher = scope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                close()
            }
        }
    }

    fun start() = synchronized(lock) {
        if (closed || !scope.isActive) return@synchronized
        val now = elapsedRealtimeMs()
        if (mutableState.value.running) {
            if (now >= requireNotNull(mutableState.value.sessionEndsAtElapsedMs)) {
                endSession(GuidanceSessionEndReason.TimedOut)
            }
            return@synchronized
        }
        generation++
        policy.reset()
        val sessionGeneration = generation
        val deadline = now + sessionDurationMs
        mutableState.value = GuidanceRuntimeState(running = true, sessionEndsAtElapsedMs = deadline)
        sessionTimer = scope.launch(dispatcher) {
            delay((deadline - elapsedRealtimeMs()).coerceAtLeast(0))
            synchronized(lock) {
                if (generation == sessionGeneration && mutableState.value.running) {
                    endSession(GuidanceSessionEndReason.TimedOut)
                }
            }
        }
    }

    fun submit(event: GuidanceEvent) = synchronized(lock) {
        if (!mutableState.value.running) return@synchronized
        val now = elapsedRealtimeMs()
        val sessionDeadline = requireNotNull(mutableState.value.sessionEndsAtElapsedMs)
        if (now >= sessionDeadline) {
            endSession(GuidanceSessionEndReason.TimedOut)
            return@synchronized
        }
        if (!policy.accept(event, now)) return@synchronized

        cardRevision++
        cancelSpeech()
        cardTimer?.cancel()
        cardTimer = null
        if (mutableState.value.postedEvent != null) output.dismiss()
        mutableState.value = mutableState.value.copy(postedEvent = null)
        val sessionGeneration = generation
        val revision = cardRevision
        // Reserve the event ID even when either output rejects it: no implicit retry loop.
        // Card acceptance is independent of whether voice is available, permitted or completed.
        if (output.show(event)) {
            mutableState.value = mutableState.value.copy(postedEvent = event)
            val deadline = minOf(now + displayDurationMs, event.expiresAtElapsedMs, sessionDeadline)
            cardTimer = scope.launch(dispatcher) {
                delay((deadline - elapsedRealtimeMs()).coerceAtLeast(0))
                synchronized(lock) {
                    if (isCurrent(sessionGeneration, revision)) {
                        output.dismiss()
                        mutableState.value = mutableState.value.copy(postedEvent = null)
                        cardTimer = null
                    }
                }
            }
        }
        startSpeech(event, minOf(event.expiresAtElapsedMs, sessionDeadline), sessionGeneration, revision)
    }

    fun stop() = synchronized(lock) {
        if (mutableState.value.running) endSession(GuidanceSessionEndReason.Stopped)
    }

    /** Permanently releases this coordinator's output; hosts can call it again during teardown. */
    fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        ownerWatcher?.cancel()
        ownerWatcher = null
        if (mutableState.value.running) endSession(GuidanceSessionEndReason.Stopped) else cancelSpeech()
        speechOutput.close()
    }

    private fun startSpeech(event: GuidanceEvent, deadline: Long, sessionGeneration: Long, revision: Long) {
        mutableState.value = mutableState.value.copy(speechState = GuidanceSpeechState(event.id, inProgress = true))
        // Assign before starting: Main.immediate or a synchronous adapter result must not leave a
        // completed job installed over the next event's job.
        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            supervisorScope {
                val attempt = synchronized(lock) {
                    if (!isCurrent(sessionGeneration, revision)) return@synchronized null
                    if (elapsedRealtimeMs() >= deadline) {
                        finishSpeech(sessionGeneration, revision, GuidanceSpeechResult.Interrupted)
                        return@synchronized null
                    }
                    // Enter speak under the command lock on the owned dispatcher. A queued old
                    // attempt cannot start between another thread's stop and replacement event.
                    async(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            speechOutput.speak(event)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            GuidanceSpeechResult.Failed
                        }
                    }
                } ?: return@supervisorScope
                val result = try {
                    withTimeout((deadline - elapsedRealtimeMs()).coerceAtLeast(0)) { attempt.await() }
                } catch (_: TimeoutCancellationException) {
                    attempt.cancel()
                    GuidanceSpeechResult.Interrupted
                } catch (cancelled: CancellationException) {
                    synchronized(lock) { finishSpeech(sessionGeneration, revision, GuidanceSpeechResult.Interrupted) }
                    throw cancelled
                }
                synchronized(lock) {
                    val boundedResult = if (elapsedRealtimeMs() >= deadline) GuidanceSpeechResult.Interrupted else result
                    finishSpeech(sessionGeneration, revision, boundedResult)
                }
            }
        }
        speechJob = job
        job.start()
    }

    /** State and global stop calls are both generation-guarded; stale completion only unwinds. */
    private fun finishSpeech(sessionGeneration: Long, revision: Long, result: GuidanceSpeechResult) {
        if (!isCurrent(sessionGeneration, revision) || !mutableState.value.speechState.inProgress) return
        speechJob = null
        mutableState.value = mutableState.value.copy(
            speechState = mutableState.value.speechState.copy(inProgress = false, result = result),
        )
        if (result == GuidanceSpeechResult.Interrupted || result == GuidanceSpeechResult.Failed) speechOutput.stop()
    }

    private fun isCurrent(sessionGeneration: Long, revision: Long): Boolean =
        generation == sessionGeneration && cardRevision == revision && mutableState.value.running

    private fun cancelSpeech() {
        speechJob?.cancel()
        speechJob = null
        if (mutableState.value.speechState.inProgress) {
            mutableState.value = mutableState.value.copy(
                speechState = mutableState.value.speechState.copy(inProgress = false, result = GuidanceSpeechResult.Interrupted),
            )
            speechOutput.stop()
        }
    }

    /** Called under lock, so completion cannot race a new start and stop its new host session. */
    private fun endSession(reason: GuidanceSessionEndReason) {
        generation++
        cardRevision++
        cancelSpeech()
        cardTimer?.cancel()
        sessionTimer?.cancel()
        cardTimer = null
        sessionTimer = null
        policy.reset()
        mutableState.value = GuidanceRuntimeState(speechState = mutableState.value.speechState)
        output.dismiss()
        onSessionEnded(reason)
    }

    companion object {
        const val MAX_SESSION_DURATION_MS = 10 * 60 * 1_000L
    }
}
