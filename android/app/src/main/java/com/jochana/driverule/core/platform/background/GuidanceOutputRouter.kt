package com.jochana.driverule.core.platform.background

import com.jochana.driverule.domain.guidance.GuidanceCardOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent

/**
 * Service-owned routing of an already selected published card. Main-thread commands only.
 * Visibility transfers the existing event; it never submits speech or extends its deadline.
 * The coordinator owns dismissal timers. Callbacks must return immediately without throwing.
 */
internal class GuidanceOutputRouter(
    private val elapsedRealtimeMs: () -> Long,
    private val showBanner: (GuidanceEvent?) -> Unit,
    private val postNotification: (GuidanceEvent, Long) -> Boolean,
    private val dismissNotification: () -> Unit,
    private val onNotificationAccepted: () -> Unit = {},
    initiallyVisible: Boolean,
) : GuidanceCardOutput {
    private var visible = initiallyVisible
    private var active: GuidanceEvent? = null
    private var deadline = 0L

    override fun show(event: GuidanceEvent): Boolean {
        val now = elapsedRealtimeMs()
        if (event.expiresAtElapsedMs <= now) return false
        active = event
        deadline = minOf(event.expiresAtElapsedMs, now + DISPLAY_DURATION_MS)
        route()
        // Accepted by the router, including denied notifications or a banner that does not fit.
        // Neither this result nor NotificationManager acceptance proves user exposure.
        return true
    }

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        route()
    }

    override fun dismiss() {
        active = null
        deadline = 0L
        showBanner(null)
        dismissNotification()
    }

    private fun route() {
        val event = active ?: return
        val remainingMs = deadline - elapsedRealtimeMs()
        if (remainingMs <= 0) {
            dismiss()
            return
        }
        if (visible) {
            dismissNotification()
            showBanner(event)
        } else {
            showBanner(null)
            if (postNotification(event, remainingMs)) onNotificationAccepted()
        }
    }

    private companion object { const val DISPLAY_DURATION_MS = 8_000L }
}
