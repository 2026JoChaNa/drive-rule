package com.jochana.driverule.feature.profile

import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NameSpeechControllerTest {
    @Test
    fun `construction and observing state never open a microphone`() = runTest {
        val fixture = Fixture(backgroundScope)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(NameSpeechState(), fixture.controller.state.value)
        assertTrue(fixture.engines.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun `partial text is only a preview and final text is trimmed without guessing a name`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        assertEquals(NameSpeechPhase.Connecting, fixture.controller.state.value.phase)

        engine.listener.onReady()
        engine.listener.onPartial("제 이름은 김")
        runCurrent()
        assertEquals(NameSpeechPhase.Listening, fixture.controller.state.value.phase)
        assertEquals("제 이름은 김", fixture.controller.state.value.partialText)
        assertNull(fixture.controller.state.value.result)

        engine.listener.onResult("  제 이름은 김하늘입니다  ")
        runCurrent()
        val result = fixture.controller.state.value.result
        assertEquals("제 이름은 김하늘입니다", result?.text)
        assertEquals(NameSpeechPhase.Idle, fixture.controller.state.value.phase)
        assertEquals("", fixture.controller.state.value.partialText)
        assertEquals(1, engine.closeCount)

        engine.listener.onResult("중복 결과")
        engine.listener.onError(NameSpeechError.Engine)
        runCurrent()
        assertEquals(result, fixture.controller.state.value.result)
        assertNull(fixture.controller.state.value.error)

        fixture.controller.consumeResult(requireNotNull(result).id + 1)
        assertEquals(result, fixture.controller.state.value.result)
        fixture.controller.consumeResult(result.id)
        assertNull(fixture.controller.state.value.result)
        fixture.controller.close()
    }

    @Test
    fun `cancel invalidates queued callbacks and old callbacks cannot affect a new session`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val old = fixture.engines.single()
        old.listener.onResult("취소 직전에 큐에 들어온 결과")
        fixture.controller.cancel()
        runCurrent()
        assertEquals(NameSpeechState(), fixture.controller.state.value)
        assertEquals(1, old.closeCount)

        fixture.controller.start()
        val current = fixture.engines.last()
        old.listener.onReady()
        old.listener.onPartial("이전 발화")
        old.listener.onResult("이전 운전자")
        old.listener.onEnded()
        old.listener.onError(NameSpeechError.Microphone)
        runCurrent()
        assertEquals(NameSpeechPhase.Connecting, fixture.controller.state.value.phase)
        assertEquals("", fixture.controller.state.value.partialText)
        assertNull(fixture.controller.state.value.result)
        assertEquals(0, current.closeCount)

        current.listener.onResult("현재 운전자")
        runCurrent()
        assertEquals("현재 운전자", fixture.controller.state.value.result?.text)
        fixture.controller.close()
    }

    @Test
    fun `listening gets twenty seconds after ready and a late result cannot replace timeout`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        runCurrent()
        advanceTimeBy(26_000)
        runCurrent()
        assertEquals(NameSpeechPhase.Connecting, fixture.controller.state.value.phase)
        engine.listener.onReady()
        runCurrent()
        advanceTimeBy(19_999)
        runCurrent()
        assertEquals(0, engine.closeCount)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(NameSpeechError.Timeout, fixture.controller.state.value.error)
        assertEquals(NameSpeechPhase.Idle, fixture.controller.state.value.phase)
        assertEquals(1, engine.closeCount)
        engine.listener.onResult("너무 늦은 결과")
        runCurrent()
        assertNull(fixture.controller.state.value.result)
        assertEquals(NameSpeechError.Timeout, fixture.controller.state.value.error)
        assertEquals(1, fixture.engines.size)
        fixture.controller.close()
    }

    @Test
    fun `initialization timeout releases a service that never becomes ready`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        runCurrent()
        advanceTimeBy(44_999)
        runCurrent()
        assertEquals(NameSpeechPhase.Connecting, fixture.controller.state.value.phase)
        assertEquals(0, engine.closeCount)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(NameSpeechError.Timeout, fixture.controller.state.value.error)
        assertEquals(NameSpeechPhase.Idle, fixture.controller.state.value.phase)
        assertEquals(1, engine.closeCount)
        engine.listener.onReady()
        engine.listener.onResult("준비 제한 이후 도착한 결과")
        runCurrent()
        assertNull(fixture.controller.state.value.result)
        assertEquals(NameSpeechError.Timeout, fixture.controller.state.value.error)
        fixture.controller.close()
    }

    @Test
    fun `cold initialization can finish after twenty seconds and still accept a final result`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        runCurrent()
        advanceTimeBy(26_000)
        runCurrent()
        assertEquals(NameSpeechPhase.Connecting, fixture.controller.state.value.phase)
        assertEquals(0, engine.closeCount)

        engine.listener.onReady()
        runCurrent()
        advanceTimeBy(19_500)
        runCurrent()
        assertEquals(NameSpeechPhase.Listening, fixture.controller.state.value.phase)
        engine.listener.onResult("김하늘")
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals("김하늘", fixture.controller.state.value.result?.text)
        assertNull(fixture.controller.state.value.error)
        assertEquals(1, engine.closeCount)
        fixture.controller.close()
    }

    @Test
    fun `duplicate ready callbacks cannot extend the listening timeout`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        engine.listener.onReady()
        runCurrent()
        advanceTimeBy(10_000)
        engine.listener.onReady()
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(NameSpeechPhase.Listening, fixture.controller.state.value.phase)
        assertEquals(0, engine.closeCount)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(NameSpeechError.Timeout, fixture.controller.state.value.error)
        assertEquals(1, engine.closeCount)
        fixture.controller.close()
    }

    @Test
    fun `recognition end waits for final text and ignores ready callbacks during processing`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        engine.listener.onPartial("김하")
        engine.listener.onEnded()
        engine.listener.onReady()
        runCurrent()
        assertEquals(NameSpeechPhase.Processing, fixture.controller.state.value.phase)
        assertEquals(0, engine.closeCount)

        advanceTimeBy(500)
        engine.listener.onResult("김하늘")
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("김하늘", fixture.controller.state.value.result?.text)
        assertNull(fixture.controller.state.value.error)
        assertEquals(1, engine.closeCount)
        fixture.controller.close()
    }

    @Test
    fun `end without final text reports no speech and repeated end cannot extend its grace`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        engine.listener.onEnded()
        runCurrent()
        advanceTimeBy(500)
        engine.listener.onEnded()
        runCurrent()
        advanceTimeBy(250)
        runCurrent()

        assertEquals(NameSpeechError.NoSpeech, fixture.controller.state.value.error)
        assertNull(fixture.controller.state.value.result)
        assertEquals(1, engine.closeCount)
        fixture.controller.close()
    }

    @Test
    fun `blank final is no speech even when a partial preview was nonblank`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        engine.listener.onPartial("미완성 이름")
        engine.listener.onResult(" \n\t ")
        runCurrent()

        assertEquals(NameSpeechError.NoSpeech, fixture.controller.state.value.error)
        assertNull(fixture.controller.state.value.result)
        assertEquals("", fixture.controller.state.value.partialText)
        assertEquals(1, engine.closeCount)
        fixture.controller.close()
    }

    @Test
    fun `engine error releases the session and never retries automatically`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        fixture.controller.start()
        assertEquals(1, fixture.engines.size)
        val engine = fixture.engines.single()
        engine.listener.onError(NameSpeechError.Microphone)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(NameSpeechError.Microphone, fixture.controller.state.value.error)
        assertEquals(1, engine.closeCount)
        assertEquals(1, fixture.engines.size)
        fixture.controller.close()
    }

    @Test
    fun `close is terminal and idempotent including queued callbacks`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        val engine = fixture.engines.single()
        engine.listener.onResult("화면이 닫힌 후 결과")
        fixture.controller.close()
        fixture.controller.close()
        fixture.controller.start()
        fixture.controller.cancel()
        runCurrent()

        assertEquals(NameSpeechState(), fixture.controller.state.value)
        assertEquals(1, engine.closeCount)
        assertEquals(1, fixture.engines.size)
    }

    @Test
    fun `owner scope cancellation releases an in-flight microphone session`() = runTest {
        val ownerJob = Job()
        val owner = CoroutineScope(backgroundScope.coroutineContext + ownerJob)
        val fixture = Fixture(owner)
        fixture.controller.start()
        runCurrent()
        owner.cancel()
        runCurrent()

        assertEquals(1, fixture.engines.single().closeCount)
        assertEquals(NameSpeechState(), fixture.controller.state.value)
        fixture.controller.start()
        assertEquals(1, fixture.engines.size)
    }

    @Test
    fun `synchronous startup failure is terminal instead of an endless connecting state`() = runTest {
        val engine = FakeEngine(startFailure = SecurityException())
        val controller = NameSpeechController({ engine }, backgroundScope)
        controller.start()
        runCurrent()

        assertEquals(NameSpeechError.PermissionDenied, controller.state.value.error)
        assertEquals(NameSpeechPhase.Idle, controller.state.value.phase)
        assertEquals(1, engine.closeCount)
        controller.close()
    }

    @Test
    fun `consumption of an earlier result cannot discard a newer result`() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start()
        fixture.engines.last().listener.onResult("첫 운전자")
        runCurrent()
        val first = fixture.controller.state.value.result
        assertNotNull(first)

        fixture.controller.start()
        fixture.engines.last().listener.onResult("다음 운전자")
        runCurrent()
        val second = fixture.controller.state.value.result
        assertNotNull(second)
        assertFalse(first?.id == second?.id)
        fixture.controller.consumeResult(requireNotNull(first).id)
        assertEquals(second, fixture.controller.state.value.result)
        fixture.controller.close()
    }

    private class Fixture(scope: CoroutineScope) {
        val engines = mutableListOf<FakeEngine>()
        val controller = NameSpeechController(
            engineFactory = { FakeEngine().also(engines::add) },
            scope = scope,
        )
    }

    private class FakeEngine(private val startFailure: Exception? = null) : NameSpeechEngine {
        lateinit var listener: NameSpeechEngine.Listener
        var closeCount = 0

        override fun start(listener: NameSpeechEngine.Listener) {
            this.listener = listener
            startFailure?.let { throw it }
        }

        override fun close() {
            closeCount++
        }
    }
}
