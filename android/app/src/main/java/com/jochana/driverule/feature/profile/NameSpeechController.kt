package com.jochana.driverule.feature.profile

import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class NameSpeechPhase { Idle, Connecting, Listening, Processing }

data class NameSpeechResult(val id: Long, val text: String)

data class NameSpeechState(
    val phase: NameSpeechPhase = NameSpeechPhase.Idle,
    val partialText: String = "",
    val result: NameSpeechResult? = null,
    val error: NameSpeechError? = null,
)

/** Call public methods on the owner's UI thread; SDK callbacks are dispatched into [scope]. */
class NameSpeechController(
    private val engineFactory: () -> NameSpeechEngine,
    scope: CoroutineScope,
    private val timeoutMillis: Long = 20_000,
    private val initializationTimeoutMillis: Long = 45_000,
) {
    private val controllerScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]),
    )
    private val mutableState = MutableStateFlow(NameSpeechState())
    val state: StateFlow<NameSpeechState> = mutableState.asStateFlow()

    private var generation = 0L
    private var activeGeneration: Long? = null
    private var nextResultId = 0L
    private var engine: NameSpeechEngine? = null
    private var timeoutJob: Job? = null
    private var endedJob: Job? = null
    private var closed = false

    init {
        require(timeoutMillis > 0) { "Recognition timeout must be positive" }
        require(initializationTimeoutMillis > 0) { "Initialization timeout must be positive" }
        controllerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                close()
            }
        }
    }

    fun start() {
        if (closed || activeGeneration != null || !controllerScope.isActive) return
        val session = ++generation
        activeGeneration = session
        mutableState.value = NameSpeechState(phase = NameSpeechPhase.Connecting)
        armTimeout(session, initializationTimeoutMillis)
        try {
            val newEngine = engineFactory()
            engine = newEngine
            newEngine.start(listenerFor(session))
        } catch (cancelled: CancellationException) {
            cancel()
            throw cancelled
        } catch (_: SecurityException) {
            if (isCurrent(session)) finishError(NameSpeechError.PermissionDenied)
        } catch (_: Exception) {
            if (isCurrent(session)) finishError(NameSpeechError.Unavailable)
        } catch (_: LinkageError) {
            if (isCurrent(session)) finishError(NameSpeechError.Unavailable)
        }
    }

    fun cancel() {
        if (closed) return
        val cleanupError = releaseSession()
        mutableState.value = NameSpeechState(error = cleanupError)
    }

    fun consumeResult(id: Long) {
        if (closed || mutableState.value.result?.id != id) return
        mutableState.value = mutableState.value.copy(result = null)
    }

    fun close() {
        if (closed) return
        closed = true
        val cleanupError = releaseSession()
        mutableState.value = NameSpeechState(error = cleanupError)
        controllerScope.cancel()
    }

    private fun listenerFor(session: Long) = object : NameSpeechEngine.Listener {
        override fun onReady() = dispatch(session) {
            // Cold model loading has its own limit. Only the first ready starts the listening clock.
            // Ignore repeated ready callbacks and a ready delivered after recognition has ended.
            if (mutableState.value.phase == NameSpeechPhase.Connecting) {
                mutableState.value = mutableState.value.copy(phase = NameSpeechPhase.Listening)
                armTimeout(session, timeoutMillis)
            }
        }

        override fun onPartial(text: String) = dispatch(session) {
            mutableState.value = mutableState.value.copy(partialText = text)
        }

        override fun onResult(text: String) = dispatch(session) {
            val candidate = text.trim()
            if (candidate.isEmpty()) {
                finishError(NameSpeechError.NoSpeech)
            } else {
                val cleanupError = releaseSession()
                mutableState.value = NameSpeechState(
                    result = NameSpeechResult(++nextResultId, candidate),
                    error = cleanupError,
                )
            }
        }

        override fun onEnded() = dispatch(session) {
            if (endedJob != null) return@dispatch
            mutableState.value = mutableState.value.copy(phase = NameSpeechPhase.Processing)
            // Pleos does not guarantee the ordering of final text and recognition end callbacks.
            endedJob = controllerScope.launch {
                delay(FINAL_RESULT_GRACE_MILLIS)
                if (isCurrent(session)) finishError(NameSpeechError.NoSpeech)
            }
        }

        override fun onError(error: NameSpeechError) = dispatch(session) {
            finishError(error)
        }
    }

    private fun dispatch(session: Long, action: () -> Unit) {
        controllerScope.launch {
            if (isCurrent(session)) action()
        }
    }

    private fun isCurrent(session: Long) = !closed && activeGeneration == session

    private fun armTimeout(session: Long, durationMillis: Long) {
        timeoutJob?.cancel()
        timeoutJob = controllerScope.launch {
            delay(durationMillis)
            if (isCurrent(session)) finishError(NameSpeechError.Timeout)
        }
    }

    private fun finishError(error: NameSpeechError) {
        releaseSession()
        mutableState.value = NameSpeechState(error = error)
    }

    private fun releaseSession(): NameSpeechError? {
        activeGeneration = null
        generation++
        timeoutJob?.cancel()
        timeoutJob = null
        endedJob?.cancel()
        endedJob = null
        val previousEngine = engine
        engine = null
        return try {
            previousEngine?.close()
            null
        } catch (_: Exception) {
            NameSpeechError.Engine
        } catch (_: LinkageError) {
            NameSpeechError.Unavailable
        }
    }

    private companion object {
        const val FINAL_RESULT_GRACE_MILLIS = 750L
    }
}
