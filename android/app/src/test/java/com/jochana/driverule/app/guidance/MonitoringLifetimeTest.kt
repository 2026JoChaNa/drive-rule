package com.jochana.driverule.app.guidance

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringLifetimeTest {
    private val calls = mutableListOf<String>()
    private val lifetime = MonitoringLifetime(
        { calls += "vehicle start" }, { calls += "vehicle stop" },
        { calls += "navigation start" }, { calls += "navigation stop" },
    )

    @Test fun `app switch keeps background subscriptions alive`() {
        val activity = Any()
        lifetime.visible(activity, true)
        lifetime.background(true)
        lifetime.visible(activity, false)
        assertEquals(listOf("vehicle start", "navigation start"), calls)
        lifetime.background(false)
        assertEquals(listOf("vehicle start", "navigation start", "navigation stop", "vehicle stop"), calls)
    }

    @Test fun `stopping background session preserves visible UI restrictions`() {
        lifetime.visible(Any(), true)
        lifetime.background(true)
        lifetime.background(false)
        assertEquals(listOf("vehicle start", "navigation start", "navigation stop"), calls)
    }

    @Test fun `overlapping recreated Activities never disconnect current UI`() {
        val old = Any()
        val new = Any()
        lifetime.visible(old, true)
        lifetime.visible(new, true)
        lifetime.visible(old, false)
        assertEquals(listOf("vehicle start"), calls)
        lifetime.visible(new, false)
        assertEquals(listOf("vehicle start", "vehicle stop"), calls)
    }

    @Test fun `repeated commands do not duplicate listeners`() {
        val activity = Any()
        repeat(3) { lifetime.visible(activity, true); lifetime.background(true) }
        repeat(3) { lifetime.visible(activity, false); lifetime.background(false) }
        assertEquals(listOf("vehicle start", "navigation start", "navigation stop", "vehicle stop"), calls)
    }

    @Test fun `permission refresh reconnects only currently owned sources`() {
        lifetime.refreshPermissions()
        assertEquals(emptyList<String>(), calls)
        lifetime.visible(Any(), true)
        lifetime.refreshPermissions()
        assertEquals(listOf("vehicle start", "vehicle stop", "vehicle start"), calls)
    }

    @Test fun `background restart starts fresh sources after complete disposal`() {
        lifetime.background(true)
        lifetime.background(false)
        lifetime.background(true)
        assertEquals(listOf("vehicle start", "navigation start", "navigation stop", "vehicle stop",
            "vehicle start", "navigation start"), calls)
    }
}
