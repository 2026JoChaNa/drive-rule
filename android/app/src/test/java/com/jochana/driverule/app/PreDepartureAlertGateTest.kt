package com.jochana.driverule.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreDepartureAlertGateTest {
    @Test
    fun `gate opens exactly once`() {
        val gate = PreDepartureAlertGate()
        assertTrue(gate.claim())
        assertFalse(gate.claim())
        assertFalse(gate.claim())
    }

    @Test
    fun `concurrent claims still allow only one alert`() {
        val gate = PreDepartureAlertGate()
        val wins = AtomicInteger()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        repeat(32) { pool.execute { start.await(); if (gate.claim()) wins.incrementAndGet() } }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, wins.get())
    }
}
