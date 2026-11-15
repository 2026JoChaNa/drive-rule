package com.jochana.driverule.app.guidance

import com.jochana.driverule.domain.guidance.GuidanceCardOutput
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSessionEndReason
import com.jochana.driverule.domain.guidance.GuidanceSpeechOutput
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import com.jochana.driverule.domain.guidance.GuidanceSpeechState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceCoordinatorSpeechTest {
    @Test
    fun `rejected card still attempts speech with the event speech text`() = runTest {
        withFixture { fixture ->
            fixture.cards.accept = false
            val event = event("audio-only").copy(speechText = "Audio-only sample guidance")
            fixture.coordinator.start()
            fixture.coordinator.submit(event)
            runCurrent()

            assertNull(fixture.coordinator.state.value.postedEvent)
            assertEquals(listOf(event), fixture.speech.calls.map { it.event })
            assertSpeech(fixture.coordinator, "audio-only", inProgress = true)

            fixture.speech.calls.single().complete(GuidanceSpeechResult.Completed)
            runCurrent()
            assertSpeech(fixture.coordinator, "audio-only", result = GuidanceSpeechResult.Completed)
            assertNull(fixture.coordinator.state.value.postedEvent)
            assertTrue(fixture.coordinator.state.value.running)
        }
    }

    @Test
    fun `focus denial preserves the card and duplicate event does not retry speech`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("denied"))
            runCurrent()
            fixture.speech.calls.single().complete(GuidanceSpeechResult.FocusDenied)
            runCurrent()

            assertSpeech(fixture.coordinator, "denied", result = GuidanceSpeechResult.FocusDenied)
            assertEquals("denied", fixture.coordinator.state.value.postedEvent?.id)
            advanceTimeBy(4_000)
            fixture.coordinator.submit(event("denied"))
            runCurrent()
            assertEquals(1, fixture.speech.calls.size)
            assertEquals("denied", fixture.coordinator.state.value.postedEvent?.id)

            advanceTimeBy(4_000)
            runCurrent()
            assertNull(fixture.coordinator.state.value.postedEvent)
            assertSpeech(fixture.coordinator, "denied", result = GuidanceSpeechResult.FocusDenied)
        }
    }

    @Test
    fun `speech exception becomes failure without ending the session or dismissing its card`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("failed"))
            runCurrent()
            fixture.speech.calls.single().fail()
            runCurrent()

            assertSpeech(fixture.coordinator, "failed", result = GuidanceSpeechResult.Failed)
            assertEquals("failed", fixture.coordinator.state.value.postedEvent?.id)
            assertTrue(fixture.coordinator.state.value.running)
            assertTrue(fixture.ended.isEmpty())

            fixture.coordinator.submit(event("recovered"))
            runCurrent()
            fixture.speech.calls.last().complete(GuidanceSpeechResult.Completed)
            runCurrent()
            assertSpeech(fixture.coordinator, "recovered", result = GuidanceSpeechResult.Completed)
        }
    }

    @Test
    fun `default unavailable speech leaves the posted card usable`() = runTest {
        val cards = RecordingCardOutput()
        val coordinator = GuidanceCoordinator(
            scope = backgroundScope,
            output = cards,
            elapsedRealtimeMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        try {
            coordinator.start()
            coordinator.submit(event("unsupported"))
            runCurrent()

            assertSpeech(coordinator, "unsupported", result = GuidanceSpeechResult.Unavailable)
            assertEquals("unsupported", coordinator.state.value.postedEvent?.id)
            assertTrue(coordinator.state.value.running)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun `replacement cancels and stops the previous speech before starting the next`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("old"))
            runCurrent()
            val old = fixture.speech.calls.single()
            val stopCountBeforeReplacement = fixture.speech.stopCount

            fixture.coordinator.submit(event("new"))
            runCurrent()

            assertTrue(old.cancelled)
            assertTrue(old.finished)
            assertTrue(fixture.speech.stopCount > stopCountBeforeReplacement)
            val startOfNew = fixture.speech.operations.indexOf("speak:new")
            assertTrue(startOfNew >= 0)
            assertTrue(fixture.speech.operations.take(startOfNew).last() == "stop")
            assertEquals(listOf("old", "new"), fixture.speech.calls.map { it.event.id })
            assertSpeech(fixture.coordinator, "new", inProgress = true)
            assertEquals("new", fixture.coordinator.state.value.postedEvent?.id)
        }
    }

    @Test
    fun `card display timeout neither cancels speech nor gets restarted by speech completion`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("long-speech", expiresAt = 30_000))
            runCurrent()
            val speech = fixture.speech.calls.single()
            val stopCountBeforeCardTimeout = fixture.speech.stopCount

            advanceTimeBy(8_000)
            runCurrent()
            assertNull(fixture.coordinator.state.value.postedEvent)
            assertSpeech(fixture.coordinator, "long-speech", inProgress = true)
            assertFalse(speech.finished)
            assertEquals(stopCountBeforeCardTimeout, fixture.speech.stopCount)

            advanceTimeBy(2_000)
            speech.complete(GuidanceSpeechResult.Completed)
            runCurrent()
            assertSpeech(fixture.coordinator, "long-speech", result = GuidanceSpeechResult.Completed)
            assertNull(fixture.coordinator.state.value.postedEvent)
            assertEquals(1, fixture.cards.shown.size)
            assertTrue(fixture.coordinator.state.value.running)
        }
    }

    @Test
    fun `event expiry cancels speech even when card output was unavailable`() = runTest {
        withFixture { fixture ->
            fixture.cards.accept = false
            fixture.coordinator.start()
            fixture.coordinator.submit(event("expires", expiresAt = 2_000))
            runCurrent()
            val speech = fixture.speech.calls.single()
            val stopCountBeforeExpiry = fixture.speech.stopCount

            advanceTimeBy(2_000)
            runCurrent()

            assertTrue(speech.cancelled)
            assertTrue(fixture.speech.stopCount > stopCountBeforeExpiry)
            assertSpeech(fixture.coordinator, "expires", result = GuidanceSpeechResult.Interrupted)
            assertNull(fixture.coordinator.state.value.postedEvent)
            assertTrue(fixture.coordinator.state.value.running)
            assertTrue(fixture.ended.isEmpty())
        }
    }

    @Test
    fun `event expiry reports interruption before an uncooperative speech call finishes`() = runTest {
        withFixture { fixture ->
            fixture.speech.ignoreNextCancellation = true
            fixture.coordinator.start()
            fixture.coordinator.submit(event("expires", expiresAt = 2_000))
            runCurrent()
            val speech = fixture.speech.calls.single()

            advanceTimeBy(2_000)
            runCurrent()

            assertFalse(speech.finished)
            assertSpeech(fixture.coordinator, "expires", result = GuidanceSpeechResult.Interrupted)
            assertTrue(fixture.coordinator.state.value.running)

            speech.complete(GuidanceSpeechResult.Completed)
            runCurrent()
            assertTrue(speech.finished)
            assertSpeech(fixture.coordinator, "expires", result = GuidanceSpeechResult.Interrupted)
        }
    }

    @Test
    fun `session deadline cancels speech before the event expires`() = runTest {
        withFixture(sessionDuration = 5_000) { fixture ->
            fixture.coordinator.start()
            advanceTimeBy(4_000)
            fixture.coordinator.submit(event("last", expiresAt = 30_000))
            runCurrent()
            val speech = fixture.speech.calls.single()
            val stopCountBeforeDeadline = fixture.speech.stopCount

            advanceTimeBy(1_000)
            runCurrent()

            assertTrue(speech.cancelled)
            assertTrue(fixture.speech.stopCount > stopCountBeforeDeadline)
            assertSpeech(fixture.coordinator, "last", result = GuidanceSpeechResult.Interrupted)
            assertFalse(fixture.coordinator.state.value.running)
            assertNull(fixture.coordinator.state.value.postedEvent)
            assertEquals(listOf(GuidanceSessionEndReason.TimedOut), fixture.ended)
            assertFalse(fixture.speech.closed)
        }
    }

    @Test
    fun `late noncancellable completion cannot replace or stop the newer speech`() = runTest {
        withFixture { fixture ->
            fixture.speech.ignoreNextCancellation = true
            fixture.coordinator.start()
            fixture.coordinator.submit(event("old"))
            runCurrent()
            val old = fixture.speech.calls.single()
            fixture.coordinator.submit(event("new"))
            runCurrent()
            val stopsBeforeLateCompletion = fixture.speech.stopCount

            old.complete(GuidanceSpeechResult.Completed)
            runCurrent()

            assertTrue(old.finished)
            assertSpeech(fixture.coordinator, "new", inProgress = true)
            assertEquals("new", fixture.coordinator.state.value.postedEvent?.id)
            assertFalse(fixture.speech.calls.last().finished)
            assertEquals(stopsBeforeLateCompletion, fixture.speech.stopCount)
        }
    }

    @Test
    fun `late noncancellable failure cannot overwrite the replacement completion`() = runTest {
        withFixture { fixture ->
            fixture.speech.ignoreNextCancellation = true
            fixture.coordinator.start()
            fixture.coordinator.submit(event("old"))
            runCurrent()
            val old = fixture.speech.calls.single()
            fixture.coordinator.submit(event("new"))
            runCurrent()
            fixture.speech.calls.last().complete(GuidanceSpeechResult.Completed)
            runCurrent()
            val stopsBeforeLateFailure = fixture.speech.stopCount

            old.fail()
            runCurrent()

            assertTrue(old.finished)
            assertSpeech(fixture.coordinator, "new", result = GuidanceSpeechResult.Completed)
            assertEquals("new", fixture.coordinator.state.value.postedEvent?.id)
            assertEquals(stopsBeforeLateFailure, fixture.speech.stopCount)
            assertTrue(fixture.coordinator.state.value.running)
        }
    }

    @Test
    fun `stopped session may restart the same event ID without accepting its old completion`() = runTest {
        withFixture { fixture ->
            fixture.speech.ignoreNextCancellation = true
            fixture.coordinator.start()
            fixture.coordinator.submit(event("same"))
            runCurrent()
            val oldSessionSpeech = fixture.speech.calls.single()
            fixture.coordinator.stop()

            assertSpeech(fixture.coordinator, "same", result = GuidanceSpeechResult.Interrupted)
            assertFalse(fixture.speech.closed)
            fixture.coordinator.start()
            fixture.coordinator.submit(event("same"))
            runCurrent()
            assertEquals(2, fixture.speech.calls.size)
            val stopsBeforeOldCompletion = fixture.speech.stopCount

            oldSessionSpeech.complete(GuidanceSpeechResult.Completed)
            runCurrent()

            assertSpeech(fixture.coordinator, "same", inProgress = true)
            assertFalse(fixture.speech.calls.last().finished)
            assertEquals(stopsBeforeOldCompletion, fixture.speech.stopCount)
            assertEquals(listOf(GuidanceSessionEndReason.Stopped), fixture.ended)

            fixture.speech.calls.last().complete(GuidanceSpeechResult.Completed)
            runCurrent()
            assertSpeech(fixture.coordinator, "same", result = GuidanceSpeechResult.Completed)
            assertTrue(fixture.coordinator.state.value.running)
        }
    }

    @Test
    fun `close cancels speech releases the adapter and permanently prevents restart`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("active"))
            runCurrent()
            val speech = fixture.speech.calls.single()
            fixture.coordinator.close()
            runCurrent()

            assertTrue(speech.cancelled)
            assertTrue(fixture.speech.closed)
            assertSpeech(fixture.coordinator, "active", result = GuidanceSpeechResult.Interrupted)
            assertFalse(fixture.coordinator.state.value.running)
            assertNull(fixture.coordinator.state.value.postedEvent)

            fixture.coordinator.close()
            fixture.coordinator.start()
            fixture.coordinator.submit(event("ignored"))
            runCurrent()
            assertFalse(fixture.coordinator.state.value.running)
            assertEquals(listOf("active"), fixture.speech.calls.map { it.event.id })
            assertEquals(listOf(GuidanceSessionEndReason.Stopped), fixture.ended)
        }
    }

    @Test
    fun `owner scope cancellation closes speech resources and prevents restart`() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cards = RecordingCardOutput()
        val speech = ControlledSpeechOutput()
        val coordinator = GuidanceCoordinator(
            scope = owner,
            output = cards,
            elapsedRealtimeMs = { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            speechOutput = speech,
        )
        try {
            speech.ignoreNextCancellation = true
            coordinator.start()
            coordinator.submit(event("owned"))
            runCurrent()
            owner.cancel()
            runCurrent()

            // Cleanup must not wait for an old platform callback that ignores cancellation.
            assertFalse(speech.calls.single().finished)
            assertTrue(speech.closed)
            assertFalse(coordinator.state.value.running)
            assertNull(coordinator.state.value.postedEvent)
            assertSpeech(coordinator, "owned", result = GuidanceSpeechResult.Interrupted)

            coordinator.start()
            coordinator.submit(event("ignored"))
            speech.calls.single().complete(GuidanceSpeechResult.Completed)
            runCurrent()
            assertFalse(coordinator.state.value.running)
            assertEquals(listOf("owned"), speech.calls.map { it.event.id })
            assertSpeech(coordinator, "owned", result = GuidanceSpeechResult.Interrupted)
        } finally {
            coordinator.close()
            speech.releasePending()
            owner.cancel()
            runCurrent()
        }
    }

    @Test
    fun `expired and stopped events do not start speech`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.submit(event("before-start"))
            fixture.coordinator.start()
            fixture.coordinator.submit(event("expired", expiresAt = 0))
            fixture.coordinator.stop()
            fixture.coordinator.submit(event("after-stop"))
            runCurrent()

            assertTrue(fixture.speech.calls.isEmpty())
            assertTrue(fixture.cards.shown.isEmpty())
            assertFalse(fixture.coordinator.state.value.speechState.inProgress)
        }
    }

    @Test
    fun `stop before dispatcher execution prevents the queued speech from starting`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("queued"))
            fixture.coordinator.stop()
            runCurrent()

            assertTrue(fixture.speech.calls.isEmpty())
            assertSpeech(fixture.coordinator, "queued", result = GuidanceSpeechResult.Interrupted)
            assertFalse(fixture.coordinator.state.value.running)
        }
    }

    @Test
    fun `replacement before dispatcher execution starts only the newest speech`() = runTest {
        withFixture { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("queued-old"))
            fixture.coordinator.submit(event("queued-new"))
            runCurrent()

            assertEquals(listOf("queued-new"), fixture.speech.calls.map { it.event.id })
            assertSpeech(fixture.coordinator, "queued-new", inProgress = true)
            assertEquals("queued-new", fixture.coordinator.state.value.postedEvent?.id)
        }
    }

    @Test
    fun `event that expires while dispatch is queued never reaches the speech adapter`() = runTest {
        var elapsedTime = 0L
        withFixture(elapsedRealtimeMs = { elapsedTime }) { fixture ->
            fixture.coordinator.start()
            fixture.coordinator.submit(event("queued-expired", expiresAt = 2_000))
            // Move the injected monotonic clock without giving the dispatcher an execution turn.
            elapsedTime = 2_000
            runCurrent()

            assertTrue(fixture.speech.calls.isEmpty())
            assertSpeech(fixture.coordinator, "queued-expired", result = GuidanceSpeechResult.Interrupted)
            assertNull(fixture.coordinator.state.value.postedEvent)
            assertTrue(fixture.coordinator.state.value.running)
        }
    }

    @Test
    fun `concurrent duplicate submissions reserve only one card and speech attempt`() = runTest {
        withFixture { fixture ->
            val workers = Executors.newFixedThreadPool(4)
            val ready = CountDownLatch(4)
            val submitTogether = CountDownLatch(1)
            fixture.coordinator.start()
            try {
                val submissions = List(4) {
                    workers.submit {
                        ready.countDown()
                        check(submitTogether.await(5, TimeUnit.SECONDS))
                        repeat(8) { fixture.coordinator.submit(event("concurrent")) }
                    }
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                submitTogether.countDown()
                submissions.forEach { it.get(5, TimeUnit.SECONDS) }
                runCurrent()

                assertEquals(listOf("concurrent"), fixture.cards.shown.map { it.id })
                assertEquals(listOf("concurrent"), fixture.speech.calls.map { it.event.id })
                assertSpeech(fixture.coordinator, "concurrent", inProgress = true)
                fixture.coordinator.stop()
                runCurrent()
                assertTrue(fixture.speech.calls.single().cancelled)
                assertSpeech(fixture.coordinator, "concurrent", result = GuidanceSpeechResult.Interrupted)
            } finally {
                submitTogether.countDown()
                workers.shutdownNow()
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    private suspend fun TestScope.withFixture(
        sessionDuration: Long = GuidanceCoordinator.MAX_SESSION_DURATION_MS,
        elapsedRealtimeMs: (() -> Long)? = null,
        block: suspend TestScope.(Fixture) -> Unit,
    ) {
        val cards = RecordingCardOutput()
        val speech = ControlledSpeechOutput()
        val ended = mutableListOf<GuidanceSessionEndReason>()
        val coordinator = GuidanceCoordinator(
            scope = backgroundScope,
            output = cards,
            elapsedRealtimeMs = elapsedRealtimeMs ?: { testScheduler.currentTime },
            dispatcher = StandardTestDispatcher(testScheduler),
            sessionDurationMs = sessionDuration,
            onSessionEnded = { ended += it },
            speechOutput = speech,
        )
        try {
            block(Fixture(coordinator, cards, speech, ended))
        } finally {
            coordinator.close()
            // Release the deliberately uncooperative fake even if an assertion fails.
            speech.releasePending()
            runCurrent()
        }
    }

    private fun assertSpeech(
        coordinator: GuidanceCoordinator,
        eventId: String,
        inProgress: Boolean = false,
        result: GuidanceSpeechResult? = null,
    ) {
        assertEquals(GuidanceSpeechState(eventId, inProgress, result), coordinator.state.value.speechState)
    }

    private fun event(id: String, expiresAt: Long = 900_000) =
        GuidanceEvent(id, "Sample", "Sample only", expiresAt)

    private data class Fixture(
        val coordinator: GuidanceCoordinator,
        val cards: RecordingCardOutput,
        val speech: ControlledSpeechOutput,
        val ended: MutableList<GuidanceSessionEndReason>,
    )

    private class RecordingCardOutput : GuidanceCardOutput {
        val shown = mutableListOf<GuidanceEvent>()
        var accept = true

        override fun show(event: GuidanceEvent): Boolean {
            shown += event
            return accept
        }

        override fun dismiss() = Unit
    }

    private class ControlledSpeechOutput : GuidanceSpeechOutput {
        val calls = mutableListOf<SpeechCall>()
        val operations = mutableListOf<String>()
        var stopCount = 0
        var closed = false
        var ignoreNextCancellation = false

        override suspend fun speak(event: GuidanceEvent): GuidanceSpeechResult {
            val call = SpeechCall(event)
            val ignoreCancellation = ignoreNextCancellation
            ignoreNextCancellation = false
            calls += call
            operations += "speak:${event.id}"
            try {
                return if (ignoreCancellation) {
                    withContext(NonCancellable) { call.result.await() }
                } else {
                    call.result.await()
                }
            } catch (cancelled: CancellationException) {
                call.cancelled = true
                throw cancelled
            } finally {
                call.finished = true
            }
        }

        override fun stop() {
            stopCount++
            operations += "stop"
        }

        override fun close() {
            closed = true
        }

        fun releasePending() {
            calls.forEach { it.complete(GuidanceSpeechResult.Interrupted) }
        }
    }

    private class SpeechCall(val event: GuidanceEvent) {
        val result = CompletableDeferred<GuidanceSpeechResult>()
        var cancelled = false
        var finished = false

        fun complete(value: GuidanceSpeechResult) {
            result.complete(value)
        }

        fun fail() {
            result.completeExceptionally(IllegalStateException("Sample speech failure"))
        }
    }
}
