package com.jochana.driverule.core.platform.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jochana.driverule.DriveRuleApplication
import com.jochana.driverule.MainActivity
import com.jochana.driverule.R
import com.jochana.driverule.app.guidance.VehicleMonitoringStatus
import com.jochana.driverule.app.guidance.MonitoringIdlePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Real signal host with service-owned matching to reviewed local law cards. */
class VehicleMonitoringService : Service() {
    private val runtime by lazy { (application as DriveRuleApplication).appContainer.vehicleMonitoring }
    private var started = false
    private var ended = false
    private var foregroundNotification: Notification? = null
    private var locationMode = false
    private var guidance: VehicleEventGuidance? = null
    private var finalStatus = VehicleMonitoringStatus.Stopped
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ended) return START_NOT_STICKY
        if (VehicleMonitoringControl.stoppedByUser(this)) {
            finish(VehicleMonitoringStatus.Stopped)
            return START_NOT_STICKY
        }
        if (started) {
            if (intent?.action == ACTION_REFRESH_LOCATION) refreshLocationMode(allowPromotion = runtime.appVisible.value)
            return START_NOT_STICKY
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            finish(VehicleMonitoringStatus.Unavailable)
            return START_NOT_STICKY
        }
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL,
                getString(R.string.vehicle_monitor_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            })
            val open = PendingIntent.getActivity(this, 801, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getBroadcast(this, 802,
                Intent(this, StopVehicleMonitoringReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_drive_rule_notification)
                .setContentTitle(getString(R.string.vehicle_monitor_notification_title))
                .setContentText(getString(R.string.vehicle_monitor_notification_body))
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, getString(R.string.vehicle_monitor_stop), stop)
                .build()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            foregroundNotification = notification
            started = true
            runtime.status(VehicleMonitoringStatus.Running)
            refreshLocationMode(allowPromotion = runtime.appVisible.value)
            guidance = VehicleEventGuidance(this,
                (application as DriveRuleApplication).appContainer, scope)
            scope.launch {
                val idle = MonitoringIdlePolicy()
                while (!ended) {
                    // Revocations stop location even while another app is visible. This timer
                    // never promotes a background service or retries a failed location start.
                    refreshLocationMode(allowPromotion = false)
                    if (idle.shouldStop(SystemClock.elapsedRealtime(), runtime.appVisible.value,
                            runtime.vehicle.value, runtime.navigation.value)) {
                        // No useful background monitoring evidence: allow BG→cached transition.
                        // This is not a trip end and does not change the user's stop preference.
                        finish(VehicleMonitoringStatus.Idle)
                        return@launch
                    }
                    delay(1_000)
                }
            }
        } catch (_: RuntimeException) {
            finish(VehicleMonitoringStatus.Failed)
        } catch (_: LinkageError) {
            finish(VehicleMonitoringStatus.Unavailable)
        }
        // No boot receiver, alarm, wake lock or sticky resurrection after an OS/user stop.
        return START_NOT_STICKY
    }

    private fun refreshLocationMode(allowPromotion: Boolean) {
        if (!started || ended) return
        val available = GuidanceLocationAccess.available(this)
        if (!available) {
            runtime.setLocationMonitoring(false)
            if (locationMode) {
                locationMode = false
                try { updateForegroundType() } catch (_: RuntimeException) { /* sampling is already stopped */ }
            }
            return
        }
        if (!locationMode && allowPromotion && runtime.appVisible.value) {
            locationMode = true
            try { updateForegroundType() }
            catch (_: RuntimeException) { locationMode = false }
        }
        runtime.setLocationMonitoring(locationMode)
    }

    private fun updateForegroundType() {
        val notification = foregroundNotification ?: return
        val base = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val location = if (locationMode && Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, base or location)
    }

    private fun finish(status: VehicleMonitoringStatus) {
        ended = true
        finalStatus = status
        // Do not accept a new start until Android has disposed this instance.
        runtime.status(VehicleMonitoringStatus.Stopping)
        guidance?.close()
        guidance = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runtime.status(finalStatus)
        guidance?.close()
        guidance = null
        ended = true
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_REFRESH_LOCATION = "com.jochana.driverule.REFRESH_VISIBLE_LOCATION"
        const val CHANNEL = "vehicle-monitoring"
        const val NOTIFICATION_ID = 8101
    }
}

internal object VehicleMonitoringControl {
    private fun preferences(context: Context) = context.getSharedPreferences("vehicle-monitoring", Context.MODE_PRIVATE)
    fun stoppedByUser(context: Context) = preferences(context).getBoolean("stopped_by_user", false)
    fun enabled(context: Context) { preferences(context).edit().putBoolean("stopped_by_user", false).apply() }
    fun stop(context: Context) {
        preferences(context).edit().putBoolean("stopped_by_user", true).apply()
        val runtime = (context.applicationContext as DriveRuleApplication).appContainer.vehicleMonitoring
        // Release listeners immediately; stopService itself is asynchronous.
        runtime.status(VehicleMonitoringStatus.Stopping)
        if (!context.stopService(Intent(context, VehicleMonitoringService::class.java)))
            runtime.status(VehicleMonitoringStatus.Stopped)
    }
}

class StopVehicleMonitoringReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = VehicleMonitoringControl.stop(context)
}
