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
import com.jochana.driverule.domain.guidance.GuidanceCardOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSessionEndReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debug fixture host. No backend, vehicle signal, microphone, location or wake lock. */
class SampleGuidanceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: SampleGuidanceNotifications
    private lateinit var coordinator: GuidanceCoordinator
    private var started = false
    private var finishing = false
    private var sequence = 0

    override fun onCreate() {
        super.onCreate()
        notifications = SampleGuidanceNotifications(this).also { it.createChannels() }
        coordinator = GuidanceCoordinator(scope, object : GuidanceCardOutput {
            override fun show(event: GuidanceEvent): Boolean {
                val accepted = notifications.post(event)
                if (accepted) SampleGuidanceRuntime.posted()
                // Notification denial must not disable event detection (future voice output).
                return accepted
            }
            override fun dismiss() = notifications.dismissCard()
        }, SystemClock::elapsedRealtime, Dispatchers.Main.immediate, onSessionEnded = { reason ->
            finish(if (reason == GuidanceSessionEndReason.TimedOut) SampleStatus.Expired else SampleStatus.Stopped)
        })
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
        SampleGuidanceRuntime.received(getString(title))
        android.util.Log.i("DriveRuleEvents", "source=timer_sample sequence=$sequence background=${!SampleGuidanceRuntime.appVisible}")
        coordinator.submit(GuidanceEvent("sample-$sequence", getString(title),
            getString(R.string.bg_sample_body), SystemClock.elapsedRealtime() + 8_000))
    }

    private fun finish(status: SampleStatus) {
        if (finishing) return
        finishing = true
        SampleGuidanceRuntime.status(status)
        coordinator.stop()
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
        coordinator.stop()
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
