package com.jochana.driverule.core.platform.speech

import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Defers microphone creation until preceding guidance has actually released the SDK.
 * Call start/close on the owner UI thread. The name screen must also suppress new guidance.
 */
internal class GuidanceAwareNameSpeechEngine(
    dispatcher: CoroutineDispatcher,
    private val awaitQuiet: suspend () -> Boolean,
    private val createDelegate: () -> NameSpeechEngine,
) : NameSpeechEngine {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var started = false
    private var closed = false
    private var delegate: NameSpeechEngine? = null

    override fun start(listener: NameSpeechEngine.Listener) {
        if (closed || started) return
        started = true
        scope.launch {
            try {
                val quiet = awaitQuiet()
                ensureActive()
                if (closed) return@launch
                if (!quiet) {
                    listener.onError(NameSpeechError.Unavailable)
                    return@launch
                }
                val engine = createDelegate()
                delegate = engine
                engine.start(guardedListener(listener))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                if (!closed) listener.onError(NameSpeechError.PermissionDenied)
            } catch (_: Exception) {
                if (!closed) listener.onError(NameSpeechError.Unavailable)
            } catch (_: LinkageError) {
                if (!closed) listener.onError(NameSpeechError.Unavailable)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        val engine = delegate
        delegate = null
        engine?.close()
    }

    private fun guardedListener(listener: NameSpeechEngine.Listener) =
        object : NameSpeechEngine.Listener {
            override fun onReady() = deliver { listener.onReady() }
            override fun onPartial(text: String) = deliver { listener.onPartial(text) }
            override fun onResult(text: String) = deliver { listener.onResult(text) }
            override fun onEnded() = deliver { listener.onEnded() }
            override fun onError(error: NameSpeechError) = deliver { listener.onError(error) }
        }

    private fun deliver(action: () -> Unit) {
        scope.launch { if (!closed) action() }
    }
}
