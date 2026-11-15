package com.jochana.driverule.core.platform.background

import com.jochana.driverule.domain.guidance.GuidanceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceOutputRouterTest {
    @Test fun foregroundPreservesPublishedTextAndProvenanceWithoutPostingANotification() {
        val fixture = Fixture(visible = true)
        val original = event("one").copy(title = "게시 제목\n원문", message = "게시 본문 원문",
            contextLabel = "시행 상태 · 출처")
        assertTrue(fixture.router.show(original))
        assertSame(original, fixture.banner)
        assertTrue(fixture.notifications.isEmpty())
        assertEquals(0, fixture.accepted)
    }

    @Test fun visibilityTransfersSameEventWithOnlyItsRemainingOriginalDeadline() {
        val fixture = Fixture(visible = true)
        val original = event("one", expires = 30_000)
        fixture.router.show(original)
        fixture.now = 3_000
        fixture.router.setVisible(false)
        assertNull(fixture.banner)
        assertSame(original, fixture.notifications.single().first)
        assertEquals(5_000L, fixture.notifications.single().second)
        fixture.now = 5_000
        fixture.router.setVisible(true)
        assertSame(original, fixture.banner)
        fixture.now = 6_000
        fixture.router.setVisible(false)
        assertEquals(listOf(5_000L, 2_000L), fixture.notifications.map { it.second })
        assertEquals(2, fixture.accepted) // Posting requests, not unique events or user exposure.
    }

    @Test fun notificationDenialDoesNotRejectEventOrItsRemainingForegroundOutput() {
        val fixture = Fixture(visible = false, notificationAllowed = false)
        assertTrue(fixture.router.show(event("one")))
        assertEquals(0, fixture.accepted)
        fixture.now = 4_000
        fixture.router.setVisible(true)
        assertEquals("one", fixture.banner?.id)
    }

    @Test fun repeatedVisibilityCallbacksDoNotRepostOrExtendTheEvent() {
        val fixture = Fixture(visible = false)
        fixture.router.show(event("one"))
        repeat(5) { fixture.now += 1_000; fixture.router.setVisible(false) }
        assertEquals(1, fixture.notifications.size)
        fixture.now = 8_000
        fixture.router.setVisible(true)
        assertNull(fixture.banner)
    }

    @Test fun earlierEventExpiryWinsAndCannotBeRestoredOnForegroundReturn() {
        val fixture = Fixture(visible = false)
        fixture.now = 10_000
        fixture.router.show(event("one", expires = 12_000))
        assertEquals(2_000L, fixture.notifications.single().second)
        fixture.now = 12_000
        fixture.router.setVisible(true)
        assertNull(fixture.banner)
        assertFalse(fixture.router.show(event("expired", expires = 12_000)))
        fixture.router.setVisible(false)
        assertEquals(1, fixture.notifications.size)
    }

    @Test fun replacementAndRepeatedDismissalCannotRestoreAnOlderCard() {
        val fixture = Fixture(visible = true)
        fixture.router.show(event("one"))
        fixture.now = 1_000
        fixture.router.show(event("two", expires = 20_000))
        assertEquals("two", fixture.banner?.id)
        fixture.router.setVisible(false)
        assertEquals("two", fixture.notifications.single().first.id)
        assertEquals(8_000L, fixture.notifications.single().second)
        fixture.router.dismiss()
        fixture.router.dismiss()
        fixture.router.setVisible(true)
        fixture.router.setVisible(false)
        assertNull(fixture.banner)
        assertEquals(1, fixture.notifications.size)
        assertTrue(fixture.dismisses > 0)
    }

    private fun event(id: String, expires: Long = 8_000) = GuidanceEvent(id, "게시 제목", "게시 본문", expires)

    private class Fixture(visible: Boolean, notificationAllowed: Boolean = true) {
        var now = 0L
        var banner: GuidanceEvent? = null
        var accepted = 0
        var dismisses = 0
        val notifications = mutableListOf<Pair<GuidanceEvent, Long>>()
        val router = GuidanceOutputRouter(
            elapsedRealtimeMs = { now },
            showBanner = { banner = it },
            postNotification = { event, remaining ->
                notifications += event to remaining
                notificationAllowed
            },
            dismissNotification = { dismisses++ },
            onNotificationAccepted = { accepted++ },
            initiallyVisible = visible,
        )
    }
}
