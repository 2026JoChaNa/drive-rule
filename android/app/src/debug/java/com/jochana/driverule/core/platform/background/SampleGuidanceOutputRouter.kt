package com.jochana.driverule.core.platform.background

import com.jochana.driverule.domain.guidance.GuidanceCardOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent

/**
 * Service-owned routing of the same short-lived event. Visibility never submits/replays an
 * event and never extends its lifetime. The coordinator remains the only timer owner.
 * Main-thread commands only; all callbacks are immediate/non-throwing.
 */
internal class SampleGuidanceOutputRouter(
    private val elapsedRealtimeMs: () -> Long,
    private val showBanner: (GuidanceEvent?) -> Unit,
    private val postNotification: (GuidanceEvent, Long) -> Boolean,
    private val dismissNotification: () -> Unit,
    private val onNotificationAccepted: () -> Unit,
    initiallyVisible: Boolean,
) : GuidanceCardOutput {
    private var visible = initiallyVisible
    private var active: GuidanceEvent? = null
    private var deadline = 0L

    override fun show(event: GuidanceEvent): Boolean {
        val now = elapsedRealtimeMs()
        if (event.expiresAtElapsedMs <= now) return false
        active = event
        deadline = minOf(event.expiresAtElapsedMs, now + 8_000)
        route()
        // Accepted by this router, even if the optional system notification is denied.
        // This is not evidence of a displayed banner/notification or of user exposure.
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
}
