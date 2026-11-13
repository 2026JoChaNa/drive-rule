package com.jochana.driverule.core.platform.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jochana.driverule.MainActivity
import com.jochana.driverule.R
import com.jochana.driverule.domain.guidance.GuidanceEvent

internal class SampleGuidanceNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(NotificationChannel(SESSION_CHANNEL,
            context.getString(R.string.bg_session_channel), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CARD_CHANNEL,
            context.getString(R.string.bg_card_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.bg_card_channel_description)
            setSound(null, null)
            enableVibration(false)
        })
    }

    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        manager.areNotificationsEnabled() && listOf(SESSION_CHANNEL, CARD_CHANNEL).all {
            manager.getNotificationChannel(it)?.importance != NotificationManager.IMPORTANCE_NONE
        }

    fun ongoing(): Notification = base(SESSION_CHANNEL)
        .setContentTitle(context.getString(R.string.bg_ongoing_title))
        .setContentText(context.getString(R.string.bg_ongoing_body))
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(0, context.getString(R.string.bg_stop_action), PendingIntent.getBroadcast(context, 1,
            Intent(context, StopSampleGuidanceReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    /** Accepted by NotificationManager, not proof of a heads-up display or user exposure. */
    @SuppressLint("MissingPermission") // Explicit permission/app/channel check immediately before notify.
    fun post(event: GuidanceEvent): Boolean {
        if (!allowed()) return false
        return try {
            manager.notify(CARD_ID, base(CARD_CHANNEL)
                .setContentTitle(event.title).setContentText(event.message)
                // Honest category: AAOS does not promise EVENT heads-up notifications.
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setTimeoutAfter(8_000).setAutoCancel(true).build())
            true
        } catch (_: SecurityException) { false }
    }

    fun dismissCard() = manager.cancel(CARD_ID)
    fun dismissAll() { manager.cancel(CARD_ID); manager.cancel(SESSION_ID) }
    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_sample_notification)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

    companion object {
        const val SESSION_CHANNEL = "sample_background_session_v1"
        const val CARD_CHANNEL = "sample_background_cards_v1"
        const val SESSION_ID = 4100
        const val CARD_ID = 4101
    }
}
