package com.jochana.driverule.app.guidance

import com.jochana.driverule.domain.guidance.GuidanceCardOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSessionEndReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceCoordinatorTest {
    @Test
    fun `start is idempotent and does not move the ten minute deadline`() = runTest {
        val fixture = fixture()
        fixture.coordinator.start()
        val deadline = fixture.coordinator.state.value.sessionEndsAtElapsedMs
        advanceTimeBy(30_000)
        fixture.coordinator.start()
        assertEquals(deadline, fixture.coordinator.state.value.sessionEndsAtElapsedMs)
        advanceTimeBy(570_000)
        runCurrent()
        assertFalse(fixture.coordinator.state.value.running)
        assertEquals(listOf(GuidanceSessionEndReason.TimedOut), fixture.ended)
    }

    @Test
    fun `duplicate ID does not post again or extend its eight second lifetime`() = runTest {
        val fixture = fixture()
        fixture.coordinator.start()
        fixture.coordinator.submit(event("same"))
        advanceTimeBy(4_000)
        fixture.coordinator.submit(event("same"))
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(listOf("same"), fixture.output.shown.map { it.id })
        assertNull(fixture.coordinator.state.value.postedEvent)
        assertTrue(fixture.coordinator.state.value.running)
        assertEquals(1, fixture.output.dismissals)
        fixture.coordinator.stop()
    }

    @Test
    fun `expired candidates are rejected and cards cannot outlive their event`() = runTest {
        val fixture = fixture()
        fixture.coordinator.start()
        fixture.coordinator.submit(event("expired", expiresAt = 0))
        assertTrue(fixture.output.shown.isEmpty())
        fixture.coordinator.submit(event("soon", expiresAt = 2_000))
        advanceTimeBy(2_000)
        runCurrent()
        assertNull(fixture.coordinator.state.value.postedEvent)
        assertEquals(1, fixture.output.dismissals)
        fixture.coordinator.stop()
    }

    @Test
    fun `latest card replaces the old one and its old timer cannot dismiss the replacement`() = runTest {
        val fixture = fixture()
        fixture.coordinator.start()
        fixture.coordinator.submit(event("old"))
        advanceTimeBy(4_000)
        fixture.coordinator.submit(event("new"))
        assertEquals(1, fixture.output.dismissals)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals("new", fixture.coordinator.state.value.postedEvent?.id)
        assertEquals(1, fixture.output.dismissals)
        advanceTimeBy(4_000)
        runCurrent()
        assertNull(fixture.coordinator.state.value.postedEvent)
        assertEquals(2, fixture.output.dismissals)
        fixture.coordinator.stop()
    }

    @Test
    fun `stop is immediate and restart resets history without reviving stale timers`() = runTest {
        val fixture = fixture(sessionDuration = 20_000)
        fixture.coordinator.start()
        fixture.coordinator.submit(event("same"))
        advanceTimeBy(3_000)
        fixture.coordinator.stop()
        fixture.coordinator.stop()
        fixture.coordinator.submit(event("stopped"))
        assertEquals(1, fixture.output.shown.size)
        assertEquals(listOf(GuidanceSessionEndReason.Stopped), fixture.ended)
        assertNull(fixture.coordinator.state.value.postedEvent)

        fixture.coordinator.start()
        fixture.coordinator.submit(event("same"))
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("same", fixture.coordinator.state.value.postedEvent?.id)
        advanceTimeBy(12_000)
        runCurrent()
        assertTrue(fixture.coordinator.state.value.running) // old session deadline was 20 s.
        advanceTimeBy(3_000)
        runCurrent()
        assertFalse(fixture.coordinator.state.value.running)
        assertEquals(listOf(GuidanceSessionEndReason.Stopped, GuidanceSessionEndReason.TimedOut), fixture.ended)
    }

    @Test
    fun `duplicate memory retains only the latest sixty four accepted IDs`() = runTest {
        val fixture = fixture()
        fixture.coordinator.start()
        repeat(65) { fixture.coordinator.submit(event("event-$it")) }
        fixture.coordinator.submit(event("event-64"))
        assertEquals(65, fixture.output.shown.size)
        fixture.coordinator.submit(event("event-0"))
        assertEquals(66, fixture.output.shown.size)
        assertEquals("event-0", fixture.coordinator.state.value.postedEvent?.id)
        fixture.coordinator.stop()
    }

    @Test
    fun `rejected output is never described as posted and is not automatically retried`() = runTest {
        val fixture = fixture()
        fixture.output.accept = false
        fixture.coordinator.start()
        fixture.coordinator.submit(event("unsupported"))
        fixture.output.accept = true
        fixture.coordinator.submit(event("unsupported"))
        assertNull(fixture.coordinator.state.value.postedEvent)
        assertTrue(fixture.coordinator.state.value.running)
        assertEquals(1, fixture.output.shown.size)
        fixture.coordinator.stop()
    }

    @Test
    fun `session timeout dismisses a card even if its display timer would run longer`() = runTest {
        val fixture = fixture(sessionDuration = 10_000)
        fixture.coordinator.start()
        advanceTimeBy(9_000)
        fixture.coordinator.submit(event("last"))
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(fixture.coordinator.state.value.running)
        assertNull(fixture.coordinator.state.value.postedEvent)
        assertEquals(listOf(GuidanceSessionEndReason.TimedOut), fixture.ended)
        assertEquals(1, fixture.output.dismissals)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, fixture.output.dismissals)
    }

    @Test
    fun `cancelling the owner scope releases the output and prevents a new start`() = runTest {
        val owner = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val output = RecordingOutput()
        val ended = mutableListOf<GuidanceSessionEndReason>()
        val coordinator = GuidanceCoordinator(
            scope = owner,
            output = output,
            elapsedRealtimeMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            onSessionEnded = { ended += it },
        )
        coordinator.start()
        coordinator.submit(event("active"))
        runCurrent()
        owner.cancel()
        runCurrent()
        assertFalse(coordinator.state.value.running)
        assertEquals(1, output.dismissals)
        assertEquals(listOf(GuidanceSessionEndReason.Stopped), ended)
        coordinator.start()
        assertFalse(coordinator.state.value.running)
    }

    private fun TestScope.fixture(sessionDuration: Long = GuidanceCoordinator.MAX_SESSION_DURATION_MS): Fixture {
        val output = RecordingOutput()
        val ended = mutableListOf<GuidanceSessionEndReason>()
        val coordinator = GuidanceCoordinator(
            scope = backgroundScope,
            output = output,
            elapsedRealtimeMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            sessionDurationMs = sessionDuration,
            onSessionEnded = { ended += it },
        )
        return Fixture(coordinator, output, ended)
    }

    private fun event(id: String, expiresAt: Long = 900_000) = GuidanceEvent(id, "Sample", "Sample only", expiresAt)

    private data class Fixture(
        val coordinator: GuidanceCoordinator,
        val output: RecordingOutput,
        val ended: MutableList<GuidanceSessionEndReason>,
    )

    private class RecordingOutput : GuidanceCardOutput {
        val shown = mutableListOf<GuidanceEvent>()
        var dismissals = 0
        var accept = true
        override fun show(event: GuidanceEvent): Boolean {
            shown += event
            return accept
        }

        override fun dismiss() {
            dismissals++
        }
    }
}
