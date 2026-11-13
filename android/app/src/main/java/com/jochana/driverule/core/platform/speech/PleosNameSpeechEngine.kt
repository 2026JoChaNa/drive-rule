package com.jochana.driverule.core.platform.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechError
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Uses the vehicle's microphone through Pleos; never falls back to remote recognition. */
class PleosNameSpeechEngine(context: Context) : NameSpeechEngine {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private var sdk: SpeechToText? = null
    private var listener: NameSpeechEngine.Listener? = null
    private var resultListenerRegistered = false
    private var recognitionRequested = false

    private val resultListener = object : ResultListener {
        override fun onReady() = deliver { onReady() }
        override fun onStartedRecognition() = deliver { onReady() }
        override fun onUpdated(stt: String, completed: Boolean) = deliver {
            if (completed) onResult(stt) else onPartial(stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = deliver { onEnded() }
        override fun onError(errorType: ErrorType) = deliver { onError(errorType.toDomainError()) }
    }

    override fun start(listener: NameSpeechEngine.Listener) {
        if (closed.get() || !started.compareAndSet(false, true)) return
        onMain {
            if (closed.get()) return@onMain
            this.listener = listener
            guarded {
                val instance = SpeechToText(applicationContext, Mode.ON_DEVICE)
                sdk = instance
                instance.initialize(object : OnInitializeListener {
                    override fun onSuccess() = onMain initialized@ {
                        if (closed.get() || sdk !== instance || recognitionRequested) return@initialized
                        recognitionRequested = true
                        guarded {
                            resultListenerRegistered = true
                            instance.addListener(resultListener)
                            if (!closed.get()) instance.setLocale(Locale.KOREA)
                            if (!closed.get()) instance.request()
                        }
                    }

                    override fun onFailure(errorType: ErrorType) = deliver {
                        onError(errorType.toDomainError())
                    }
                })
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onMain {
            listener = null
            val instance = sdk
            sdk = null
            if (instance != null) {
                cleanup("stop") { instance.stop() }
                if (resultListenerRegistered) {
                    cleanup("remove listener") { instance.removeListener(resultListener) }
                }
                cleanup("release") { instance.release() }
            }
            resultListenerRegistered = false
        }
    }

    private fun deliver(event: NameSpeechEngine.Listener.() -> Unit) = onMain {
        if (!closed.get()) listener?.event()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }

    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (_: SecurityException) {
            deliver { onError(NameSpeechError.PermissionDenied) }
        } catch (_: RuntimeException) {
            deliver { onError(NameSpeechError.Unavailable) }
        } catch (_: LinkageError) {
            deliver { onError(NameSpeechError.Unavailable) }
        }
    }

    private fun cleanup(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (_: RuntimeException) {
            // Log only a fixed operation name, never recognized text, audio, or exception details.
            Log.w(TAG, "Speech cleanup failed: $operation")
        } catch (_: LinkageError) {
            Log.w(TAG, "Speech cleanup unavailable: $operation")
        }
    }

    private fun ErrorType.toDomainError(): NameSpeechError = when (this) {
        ErrorType.PERMISSION_DENIED -> NameSpeechError.PermissionDenied
        ErrorType.MIC_ERROR -> NameSpeechError.Microphone
        ErrorType.TIMEOUT -> NameSpeechError.Timeout
        ErrorType.UNAVAILABLE_LANGUAGE -> NameSpeechError.UnsupportedLanguage
        ErrorType.NOT_SUPPORTED_API,
        ErrorType.NETWORK_ERROR,
        ErrorType.UNAUTHENTICATED,
        -> NameSpeechError.Unavailable
        ErrorType.AUDIO_ERROR,
        ErrorType.INVALID_REQUEST_ERROR,
        ErrorType.ON_DEVICE_ENGINE_ERROR,
        ErrorType.SERVER_INTERNAL_ERROR,
        ErrorType.UNKNOWN,
        -> NameSpeechError.Engine
    }

    private companion object {
        const val TAG = "NameSpeech"
    }
}
