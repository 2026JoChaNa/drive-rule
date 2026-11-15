package com.jochana.driverule.core.platform.speech

import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceAwareNameSpeechEngineTest {
    @Test fun `microphone is not created until guidance cleanup is acknowledged`() = runTest {
        val quiet = CompletableDeferred<Boolean>()
        val delegate = FakeEngine()
        var created = 0
        val engine = GuidanceAwareNameSpeechEngine(StandardTestDispatcher(testScheduler), { quiet.await() }) {
            created++
            delegate
        }
        val listener = RecordingListener()
        engine.start(listener)
        runCurrent()
        assertEquals(0, created)
        quiet.complete(true)
        runCurrent()
        assertEquals(1, created)
        assertTrue(delegate.started)
        delegate.listener.onReady()
        delegate.listener.onResult("테스트")
        runCurrent()
        assertEquals(listOf("ready", "result"), listener.events)
        engine.close()
        assertEquals(1, delegate.closed)
    }

    @Test fun `leaving name input during cleanup cannot open microphone later`() = runTest {
        val quiet = CompletableDeferred<Boolean>()
        var created = false
        val engine = GuidanceAwareNameSpeechEngine(StandardTestDispatcher(testScheduler), { quiet.await() }) {
            created = true
            FakeEngine()
        }
        val listener = RecordingListener()
        engine.start(listener)
        runCurrent()
        engine.close()
        quiet.complete(true)
        runCurrent()
        assertFalse(created)
        assertTrue(listener.events.isEmpty())
    }

    @Test fun `unconfirmed TTS cleanup reports unavailable without microphone access`() = runTest {
        var created = false
        val engine = GuidanceAwareNameSpeechEngine(StandardTestDispatcher(testScheduler), { false }) {
            created = true
            FakeEngine()
        }
        val listener = RecordingListener()
        engine.start(listener)
        runCurrent()
        assertFalse(created)
        assertEquals(listOf(NameSpeechError.Unavailable), listener.errors)
        engine.close()
    }

    @Test fun `close suppresses queued and late SDK results and releases once`() = runTest {
        val delegate = FakeEngine()
        val engine = GuidanceAwareNameSpeechEngine(StandardTestDispatcher(testScheduler), { true }) { delegate }
        val listener = RecordingListener()
        engine.start(listener)
        runCurrent()
        delegate.listener.onResult("queued")
        engine.close()
        engine.close()
        delegate.listener.onReady()
        runCurrent()
        assertTrue(listener.events.isEmpty())
        assertEquals(1, delegate.closed)
    }

    @Test fun `repeated start never duplicates microphone session`() = runTest {
        var created = 0
        val engine = GuidanceAwareNameSpeechEngine(StandardTestDispatcher(testScheduler), { true }) {
            created++
            FakeEngine()
        }
        engine.start(RecordingListener())
        engine.start(RecordingListener())
        runCurrent()
        assertEquals(1, created)
        engine.close()
        engine.start(RecordingListener())
        runCurrent()
        assertEquals(1, created)
    }

    @Test fun `SDK construction failure reaches controller instead of crashing coroutine`() = runTest {
        val engine = GuidanceAwareNameSpeechEngine(StandardTestDispatcher(testScheduler), { true }) {
            throw NoClassDefFoundError("SDK unavailable")
        }
        val listener = RecordingListener()
        engine.start(listener)
        runCurrent()
        assertEquals(listOf(NameSpeechError.Unavailable), listener.errors)
        engine.close()
    }

    private class FakeEngine : NameSpeechEngine {
        var started = false
        var closed = 0
        lateinit var listener: NameSpeechEngine.Listener
        override fun start(listener: NameSpeechEngine.Listener) {
            started = true
            this.listener = listener
        }
        override fun close() { closed++ }
    }

    private class RecordingListener : NameSpeechEngine.Listener {
        val events = mutableListOf<String>()
        val errors = mutableListOf<NameSpeechError>()
        override fun onReady() { events += "ready" }
        override fun onPartial(text: String) { events += "partial" }
        override fun onResult(text: String) { events += "result" }
        override fun onEnded() { events += "ended" }
        override fun onError(error: NameSpeechError) { errors += error }
    }
}
