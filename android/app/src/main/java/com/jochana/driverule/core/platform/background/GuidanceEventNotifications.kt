package com.jochana.driverule.core.platform.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jochana.driverule.R
import com.jochana.driverule.domain.guidance.GuidanceEvent

/** Optional read-only event output. It never owns, replaces or cancels the service notification. */
internal class GuidanceEventNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        try {
            manager?.createNotificationChannel(NotificationChannel(CHANNEL,
                context.getString(R.string.guidance_event_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.guidance_event_channel_description)
                setSound(null, null)
                enableVibration(false)
            })
        } catch (_: RuntimeException) {
            // Optional visual output must not stop independent event processing or speech.
        }
    }

    /** Accepted by NotificationManager, not proof of a heads-up display or that the user read it. */
    @SuppressLint("MissingPermission") // Permission, app and event-channel checks precede notify.
    fun post(event: GuidanceEvent, remainingMs: Long): Boolean {
        if (remainingMs <= 0) return false
        return try {
            val notifications = manager ?: return false
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
            if (!notifications.areNotificationsEnabled()) return false
            val channel = notifications.getNotificationChannel(CHANNEL) ?: return false
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
            notifications.notify(CARD_ID, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_drive_rule_notification)
                .setContentTitle(event.title)
                .setContentText(event.message)
                .setSubText(event.contextLabel)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Honest category: AAOS does not guarantee EVENT heads-up presentation.
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(remainingMs)
                .setAutoCancel(false)
                // No click intent, action, full-screen intent or background Activity launch.
                .build())
            true
        } catch (_: RuntimeException) { false }
    }

    fun dismiss() {
        try { manager?.cancel(CARD_ID) } catch (_: RuntimeException) { /* Idempotent teardown. */ }
    }

    private companion object {
        const val CHANNEL = "guidance_events_v1"
        const val CARD_ID = 8102
    }
}
