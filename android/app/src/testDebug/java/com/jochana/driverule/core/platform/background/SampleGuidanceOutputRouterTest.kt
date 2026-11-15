package com.jochana.driverule.core.platform.background

import com.jochana.driverule.domain.guidance.GuidanceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleGuidanceOutputRouterTest {
    @Test fun foregroundUsesBannerAndDoesNotPostSystemNotification() {
        val fixture = Fixture(visible = true)
        assertTrue(fixture.router.show(event("one")))
        assertEquals("one", fixture.banner?.id)
        assertTrue(fixture.notifications.isEmpty())
        assertEquals(0, fixture.accepted)
    }

    @Test fun visibilityTransfersTheSameEventWithoutExtendingItsDeadline() {
        val fixture = Fixture(visible = true)
        fixture.router.show(event("one"))
        fixture.now = 3_000
        fixture.router.setVisible(false)
        assertNull(fixture.banner)
        assertEquals(listOf("one" to 5_000L), fixture.notifications)
        fixture.now = 5_000
        fixture.router.setVisible(true)
        assertEquals("one", fixture.banner?.id)
        fixture.now = 6_000
        fixture.router.setVisible(false)
        assertEquals(listOf("one" to 5_000L, "one" to 2_000L), fixture.notifications)
        // These are accepted notification requests, not unique events or actual exposures.
        assertEquals(2, fixture.accepted)
    }

    @Test fun repeatedVisibilityCallbacksDoNotRepost() {
        val fixture = Fixture(visible = false)
        fixture.router.show(event("one"))
        repeat(5) { fixture.router.setVisible(false) }
        assertEquals(1, fixture.notifications.size)
    }

    @Test fun notificationDenialStillAcceptsEventAndAllowsItsRemainingForegroundBanner() {
        val fixture = Fixture(visible = false, notificationAllowed = false)
        assertTrue(fixture.router.show(event("one")))
        assertEquals(0, fixture.accepted)
        assertNull(fixture.banner)
        fixture.now = 4_000
        fixture.router.setVisible(true)
        assertEquals("one", fixture.banner?.id)
    }

    @Test fun expiredEventCannotBeShownOrRestoredOnReturn() {
        val fixture = Fixture(visible = false)
        fixture.router.show(event("one", expires = 2_000))
        assertEquals(2_000L, fixture.notifications.single().second)
        fixture.now = 2_000
        fixture.router.setVisible(true)
        assertNull(fixture.banner)
        assertFalse(fixture.router.show(event("expired", expires = 2_000)))
        fixture.router.setVisible(false)
        assertEquals(1, fixture.notifications.size)
    }

    @Test fun newestCardReplacesOldOneAndCoordinatorDismissCannotResurrectIt() {
        val fixture = Fixture(visible = true)
        fixture.router.show(event("one"))
        fixture.now = 1_000
        fixture.router.show(event("two", expires = 20_000))
        assertEquals("two", fixture.banner?.id)
        fixture.router.setVisible(false)
        assertEquals("two" to 8_000L, fixture.notifications.single())
        fixture.router.dismiss()
        val requests = fixture.notifications.size
        fixture.router.setVisible(true)
        fixture.router.setVisible(false)
        assertNull(fixture.banner)
        assertEquals(requests, fixture.notifications.size)
        assertTrue(fixture.dismisses > 0)
    }

    private fun event(id: String, expires: Long = 8_000) = GuidanceEvent(id, "Sample", "Not a law", expires)

    private class Fixture(visible: Boolean, notificationAllowed: Boolean = true) {
        var now = 0L
        var banner: GuidanceEvent? = null
        var accepted = 0
        var dismisses = 0
        val notifications = mutableListOf<Pair<String, Long>>()
        val router = SampleGuidanceOutputRouter(
            elapsedRealtimeMs = { now },
            showBanner = { banner = it },
            postNotification = { event, remaining ->
                notifications += event.id to remaining
                notificationAllowed
            },
            dismissNotification = { dismisses++ },
            onNotificationAccepted = { accepted++ },
            initiallyVisible = visible,
        )
    }
}
