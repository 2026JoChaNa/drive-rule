package com.jochana.driverule.core.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSpeechOutput
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explicit local synthesis. No fallback to Android's default/remote engine or app registration.
 * Callers own the event deadline. Cancellation is immediate to the caller; the next SDK instance
 * waits until the previous speech has ended and its connection is released.
 */
class PleosGuidanceSpeechOutput(context: Context) : GuidanceSpeechOutput {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val output = SpeechSessionOutput {
        PleosSession(appContext, mainHandler, ProcessPleosSessionQueue.sessions)
    }

    override suspend fun speak(event: GuidanceEvent): GuidanceSpeechResult = withContext(Dispatchers.Main.immediate) {
        if (SystemClock.elapsedRealtime() >= event.expiresAtElapsedMs) GuidanceSpeechResult.Interrupted
        else output.speak(event)
    }

    override fun stop() = output.stop()
    override fun close() = output.close()

    companion object {
        /**
         * Wait for process-wide TTS cleanup before opening the name STT session. No SDK is created.
         * False means cleanup could not be confirmed; waiting is cancellable. Callers must continue
         * suppressing new guidance while STT is active because this observes, not reserves, the queue.
         */
        suspend fun awaitQuiescence(): Boolean = ProcessPleosSessionQueue.sessions.awaitQuiescence()
    }
}

/** The remote stop targets a package, so service/adapter recreation must not bypass the barrier. */
private object ProcessPleosSessionQueue {
    val sessions = SpeechSessionQueue<PleosSession>(
        start = { it.begin() },
        reject = { it.rejectUnavailable() },
    )
}

private class PleosSession(
    private val context: Context,
    private val handler: Handler,
    private val queue: SpeechSessionQueue<PleosSession>,
) : SpeechSession {
    // Only this flag is accessed off main. All SDK and queue state lives on the main thread.
    private val released = AtomicBoolean(false)
    private var disposed = false
    private var sdk: TextToSpeech? = null
    private var registered = false
    private var initialized = false
    private var workSubmitted = false
    private var requested = false
    private var ended = false
    private var stopping = false
    private var stopTimeout: Runnable? = null
    private var text = ""
    private var completion: ((GuidanceSpeechResult) -> Unit)? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val attributes = AudioAttributes.Builder()
        // Informational spoken notification, not navigation, Gleo, safety or emergency audio.
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = onMain { onEnded(GuidanceSpeechResult.Completed, stopAcknowledged = false) }
        override fun onStop() = onMain { onEnded(GuidanceSpeechResult.Interrupted, stopAcknowledged = true) }
        override fun onError(errorType: ErrorType) = onMain { complete(errorType.toResult()) }
    }

    override fun start(text: String, onResult: (GuidanceSpeechResult) -> Unit) = onMain {
        if (released.get() || disposed) return@onMain
        this.text = text
        completion = onResult
        queue.enqueue(this)
    }

    fun rejectUnavailable() {
        complete(GuidanceSpeechResult.Unavailable)
    }

    /** The previous instance must finish its asynchronous cleanup before this is called. */
    fun begin() {
        if (released.get()) {
            finishRelease(cleanupConfirmed = true)
            return
        }
        guarded {
            val instance = TextToSpeech(context, Mode.ON_DEVICE)
            sdk = instance
            instance.initialize(object : OnInitializeListener {
                override fun onSuccess() = onMain initialized@ {
                    if (released.get() || disposed || sdk !== instance || initialized) return@initialized
                    initialized = true
                    guarded {
                        registered = true
                        instance.addEventListener(events)
                        instance.setAudioAttributes(attributes)
                        if (released.get()) return@guarded
                        val manager = context.getSystemService(AudioManager::class.java)
                        if (manager == null) {
                            complete(GuidanceSpeechResult.Unavailable)
                            return@guarded
                        }
                        audioManager = manager
                        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attributes)
                            .setAcceptsDelayedFocusGain(false)
                            .setWillPauseWhenDucked(true)
                            .setOnAudioFocusChangeListener({ change ->
                                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                                ) complete(GuidanceSpeechResult.Interrupted)
                            }, handler)
                            .build()
                        focusRequest = request
                        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            complete(GuidanceSpeechResult.FocusDenied)
                            return@guarded
                        }
                        if (released.get()) return@guarded
                        // These operations enqueue work in the SDK's serial coroutine scope.
                        // Do not enqueue locale before focus approval: release does not cancel that scope.
                        workSubmitted = true
                        instance.setLocale(Locale.KOREA)
                        if (released.get()) return@guarded
                        requested = true
                        instance.speak(text, true)
                    }
                }

                override fun onFailure(errorType: ErrorType) = onMain { complete(errorType.toResult()) }
            })
        }
    }

    private fun onEnded(result: GuidanceSpeechResult, stopAcknowledged: Boolean) {
        if (disposed || (!requested && !stopping)) return
        ended = true
        if (released.get()) {
            // After stop was queued, natural onDone does not prove the queued stop was processed.
            if (!stopping || stopAcknowledged) finishRelease(cleanupConfirmed = true)
        } else {
            complete(result)
        }
    }

    private fun complete(result: GuidanceSpeechResult) {
        if (!released.get() && !disposed) completion?.invoke(result)
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        onMain {
            completion = null
            if (disposed) return@onMain
            queue.removeWaiting(this)
            if (!queue.isCurrent(this) || !workSubmitted || ended) {
                // No playback was submitted, or a terminal callback already confirmed it ended.
                // An unnecessary stop may rebind the SDK after release.
                finishRelease(cleanupConfirmed = true)
            } else {
                awaitStop()
            }
        }
    }

    private fun awaitStop() {
        if (stopping || disposed) return
        stopping = true
        val instance = sdk
        if (instance == null) {
            finishRelease(cleanupConfirmed = false)
            return
        }
        val timeout = Runnable {
            // A late package-scoped stop could affect a new instance. Without an acknowledgement,
            // release resources and block synthesis for this process, including new adapters.
            Log.w("GuidanceSpeech", "Speech stop confirmation timed out; process restart required")
            finishRelease(cleanupConfirmed = false)
        }
        stopTimeout = timeout
        handler.postDelayed(timeout, STOP_ACK_TIMEOUT_MS)
        if (!cleanup { instance.stop() }) finishRelease(cleanupConfirmed = false)
    }

    private fun finishRelease(cleanupConfirmed: Boolean) {
        if (disposed) return
        disposed = true
        stopTimeout?.let(handler::removeCallbacks)
        stopTimeout = null
        completion = null
        val instance = sdk
        sdk = null
        var safeToStartNext = cleanupConfirmed
        if (instance != null) {
            if (registered) safeToStartNext = cleanup { instance.removeEventListener(events) } && safeToStartNext
            safeToStartNext = cleanup { instance.release() } && safeToStartNext
        }
        registered = false
        // Keep focus until the stop acknowledgement or bounded failure cleanup. There is no
        // automatic resume after focus loss; actual remote-service focus handling needs device QA.
        focusRequest?.let { request ->
            safeToStartNext = cleanup { audioManager?.abandonAudioFocusRequest(request) } && safeToStartNext
        }
        focusRequest = null
        audioManager = null
        queue.finish(this, safeToStartNext)
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }

    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (_: RuntimeException) {
            complete(GuidanceSpeechResult.Unavailable)
        } catch (_: LinkageError) {
            complete(GuidanceSpeechResult.Unavailable)
        }
    }

    private fun cleanup(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (_: RuntimeException) {
        Log.w("GuidanceSpeech", "Speech resource cleanup failed")
        false
    } catch (_: LinkageError) {
        Log.w("GuidanceSpeech", "Speech resource unavailable during cleanup")
        false
    }

    private fun ErrorType.toResult() = when (this) {
        ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API, ErrorType.UNAVAILABLE_LANGUAGE,
        ErrorType.UNAUTHENTICATED, ErrorType.NETWORK_ERROR -> GuidanceSpeechResult.Unavailable
        else -> GuidanceSpeechResult.Failed
    }

    private companion object {
        const val STOP_ACK_TIMEOUT_MS = 2_000L
    }
}
