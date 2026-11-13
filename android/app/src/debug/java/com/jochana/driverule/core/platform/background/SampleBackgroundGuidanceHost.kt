package com.jochana.driverule.core.platform.background

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jochana.driverule.R
import com.jochana.driverule.app.BackgroundGuidanceHost
import com.jochana.driverule.feature.home.SampleGuidancePanel

class SampleBackgroundGuidanceHost(private val activity: ComponentActivity) : BackgroundGuidanceHost {
    private var visible = false
    private val notifications = SampleGuidanceNotifications(activity)
    private val preferences = SampleGuidanceRuntime.preferences(activity)
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Optional card notifications. The event service keeps running regardless of this choice.
    }

    override fun onVisible() {
        visible = true
        SampleGuidanceRuntime.appVisible = true
        if (SampleGuidanceRuntime.autoStartAttempted) return
        SampleGuidanceRuntime.autoStartAttempted = true
        if (preferences.getBoolean("stopped_by_user", false)) {
            SampleGuidanceRuntime.status(SampleStatus.Stopped)
        } else requestStart()
    }

    override fun onHidden() { visible = false; SampleGuidanceRuntime.appVisible = false } // Never stop the service for an app switch.

    private fun requestStart() {
        if (!visible || SampleGuidanceRuntime.state.value.status in setOf(SampleStatus.Running, SampleStatus.Starting)) return
        SampleGuidanceRuntime.autoStartAttempted = true
        notifications.createChannels()
        startService()
    }

    private fun startService() {
        if (!visible) return
        preferences.edit().putBoolean("stopped_by_user", false).apply()
        SampleGuidanceRuntime.status(SampleStatus.Starting)
        try {
            ContextCompat.startForegroundService(activity, Intent(activity, SampleGuidanceService::class.java))
        } catch (_: RuntimeException) { SampleGuidanceRuntime.status(SampleStatus.Failed) }
    }

    @Composable override fun Controls() {
        val state by SampleGuidanceRuntime.state.collectAsStateWithLifecycle()
        val text = when (state.status) {
            SampleStatus.Idle -> R.string.bg_status_idle
            SampleStatus.Starting -> R.string.bg_status_starting
            SampleStatus.Running -> R.string.bg_status_running
            SampleStatus.Stopped -> R.string.bg_status_stopped
            SampleStatus.Expired -> R.string.bg_status_expired
            SampleStatus.Failed -> R.string.bg_status_failed
        }
        SampleGuidancePanel(stringResource(text), state.status == SampleStatus.Running, state.postedCount,
            state.receivedCount, state.backgroundEventCount, state.lastEventTitle,
            onStart = { requestStart() },
            onStop = { SampleGuidanceRuntime.stop(activity) },
            onSample = {
                if (visible && SampleGuidanceRuntime.state.value.status == SampleStatus.Running)
                    activity.startService(Intent(activity, SampleGuidanceService::class.java).setAction(SampleGuidanceService.ACTION_SAMPLE))
            }, onSettings = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    (!preferences.getBoolean("notification_permission_requested", false) ||
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                    preferences.edit().putBoolean("notification_permission_requested", true).apply()
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            })
    }
}
