package com.jochana.driverule.core.platform.background

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.jochana.driverule.R
import com.jochana.driverule.app.guidance.GuidanceCoordinator
import com.jochana.driverule.core.platform.audio.PleosGuidanceSpeechOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSessionEndReason
import com.jochana.driverule.domain.guidance.GuidanceSpeechOutput
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Debug fixture host. No backend, vehicle signal, microphone, location or wake lock. */
class SampleGuidanceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: SampleGuidanceNotifications
    private lateinit var coordinator: GuidanceCoordinator
    private lateinit var router: SampleGuidanceOutputRouter
    private lateinit var speech: GuidanceSpeechOutput
    private var started = false
    private var finishing = false
    private var sequence = 0
    private val hostChanged: () -> Unit = {
        if (!finishing) {
            if (SampleGuidanceRuntime.state.value.status != SampleStatus.Running) {
                router.dismiss()
                speech.stop()
            } else {
                router.setVisible(SampleGuidanceRuntime.appVisible)
                if (!SampleGuidanceRuntime.state.value.speechAllowed) speech.stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications = SampleGuidanceNotifications(this).also { it.createChannels() }
        router = SampleGuidanceOutputRouter(SystemClock::elapsedRealtime, SampleGuidanceRuntime::banner,
            notifications::post, notifications::dismissCard, SampleGuidanceRuntime::posted,
            initiallyVisible = SampleGuidanceRuntime.appVisible)
        val adapter = PleosGuidanceSpeechOutput(applicationContext)
        speech = object : GuidanceSpeechOutput {
            override suspend fun speak(event: GuidanceEvent): GuidanceSpeechResult {
                // No notification permission dependency. Setup STT wins; a suppressed event
                // is not queued for later playback when the user leaves the name screen.
                if (!SampleGuidanceRuntime.state.value.speechAllowed) return GuidanceSpeechResult.Unavailable
                val result = adapter.speak(event)
                currentCoroutineContext().ensureActive()
                if (!finishing && result == GuidanceSpeechResult.Completed) SampleGuidanceRuntime.speechCompleted()
                return result
            }
            override fun stop() = adapter.stop()
            override fun close() = adapter.close()
        }
        coordinator = GuidanceCoordinator(scope, router,
            SystemClock::elapsedRealtime, Dispatchers.Main.immediate, onSessionEnded = { reason ->
            finish(if (reason == GuidanceSessionEndReason.TimedOut) SampleStatus.Expired else SampleStatus.Stopped)
        }, speechOutput = speech)
        SampleGuidanceRuntime.attach(hostChanged)
        scope.launch {
            coordinator.state.map { it.speechState }.distinctUntilChanged().collect {
                SampleGuidanceRuntime.speechState(it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (finishing) return START_NOT_STICKY
        if (intent?.action == ACTION_SAMPLE) {
            if (started) emitSample() else stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        if (SampleGuidanceRuntime.preferences(this).getBoolean("stopped_by_user", false)) {
            finish(SampleStatus.Stopped)
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, SampleGuidanceNotifications.SESSION_ID, notifications.ongoing(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: RuntimeException) {
            // Includes SecurityException and ForegroundServiceStartNotAllowedException.
            finish(SampleStatus.Failed)
            return START_NOT_STICKY
        }
        started = true
        SampleGuidanceRuntime.started()
        coordinator.start()
        scope.launch {
            delay(10_000)
            while (coordinator.state.value.running) {
                emitSample()
                delay(30_000)
            }
        }
        return START_NOT_STICKY
    }

    private fun emitSample() {
        // stopService is asynchronous: block already queued callbacks immediately after user stop.
        if (finishing || SampleGuidanceRuntime.state.value.status != SampleStatus.Running) return
        sequence++
        val title = when ((sequence - 1) % 3) {
            0 -> R.string.bg_sample_school
            1 -> R.string.bg_sample_turn
            else -> R.string.bg_sample_distance
        }
        val spokenText = when ((sequence - 1) % 3) {
            0 -> R.string.bg_sample_school_speech
            1 -> R.string.bg_sample_turn_speech
            else -> R.string.bg_sample_distance_speech
        }
        SampleGuidanceRuntime.received(getString(title))
        android.util.Log.i("DriveRuleEvents", "source=timer_sample sequence=$sequence background=${!SampleGuidanceRuntime.appVisible}")
        coordinator.submit(GuidanceEvent("sample-$sequence", getString(title),
            getString(R.string.bg_sample_body), SystemClock.elapsedRealtime() + 8_000,
            speechText = getString(spokenText)))
    }

    private fun finish(status: SampleStatus) {
        if (finishing) return
        finishing = true
        SampleGuidanceRuntime.status(status)
        SampleGuidanceRuntime.detach(hostChanged)
        coordinator.close()
        scope.cancel()
        notifications.dismissAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // stopService, timeout, OS disposal: all owned jobs and output are released.
        finishing = true
        if (SampleGuidanceRuntime.state.value.status == SampleStatus.Running)
            SampleGuidanceRuntime.status(SampleStatus.Stopped)
        SampleGuidanceRuntime.detach(hostChanged)
        coordinator.close()
        scope.cancel()
        notifications.dismissAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val ACTION_SAMPLE = "com.jochana.driverule.SAMPLE_GUIDANCE" }
}

class StopSampleGuidanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = SampleGuidanceRuntime.stop(context)
}
