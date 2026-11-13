package com.jochana.driverule.app.guidance

import com.jochana.driverule.domain.guidance.GuidanceCardOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceEventPolicy
import com.jochana.driverule.domain.guidance.GuidanceRuntimeState
import com.jochana.driverule.domain.guidance.GuidanceSessionEndReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service-owned, synchronous commands with serialized state/output changes. Only timers use
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
) {
    private val lock = Any()
    private val policy = GuidanceEventPolicy()
    private val mutableState = MutableStateFlow(GuidanceRuntimeState())
    val state: StateFlow<GuidanceRuntimeState> = mutableState.asStateFlow()
    private var generation = 0L
    private var cardRevision = 0L
    private var cardTimer: Job? = null
    private var sessionTimer: Job? = null

    init {
        require(displayDurationMs > 0) { "Display duration must be positive" }
        require(sessionDurationMs in 1L..MAX_SESSION_DURATION_MS) { "Session duration must be at most ten minutes" }
        scope.coroutineContext[Job]?.invokeOnCompletion { stop() }
    }

    fun start() = synchronized(lock) {
        if (!scope.isActive) return@synchronized
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
        cardTimer?.cancel()
        cardTimer = null
        if (mutableState.value.postedEvent != null) output.dismiss()
        mutableState.value = mutableState.value.copy(postedEvent = null)
        // Reserve the event ID even if posting fails: there is no implicit retry loop.
        if (!output.show(event)) return@synchronized
        mutableState.value = mutableState.value.copy(postedEvent = event)
        val sessionGeneration = generation
        val revision = cardRevision
        val deadline = minOf(now + displayDurationMs, event.expiresAtElapsedMs, sessionDeadline)
        cardTimer = scope.launch(dispatcher) {
            delay((deadline - elapsedRealtimeMs()).coerceAtLeast(0))
            synchronized(lock) {
                if (generation == sessionGeneration && cardRevision == revision && mutableState.value.running) {
                    output.dismiss()
                    mutableState.value = mutableState.value.copy(postedEvent = null)
                    cardTimer = null
                }
            }
        }
    }

    fun stop() = synchronized(lock) {
        if (mutableState.value.running) endSession(GuidanceSessionEndReason.Stopped)
    }

    /** Called under lock, so completion cannot race a new start and stop its new host session. */
    private fun endSession(reason: GuidanceSessionEndReason) {
        generation++
        cardRevision++
        cardTimer?.cancel()
        sessionTimer?.cancel()
        cardTimer = null
        sessionTimer = null
        policy.reset()
        mutableState.value = GuidanceRuntimeState()
        output.dismiss()
        onSessionEnded(reason)
    }

    companion object {
        const val MAX_SESSION_DURATION_MS = 10 * 60 * 1_000L
    }
}
