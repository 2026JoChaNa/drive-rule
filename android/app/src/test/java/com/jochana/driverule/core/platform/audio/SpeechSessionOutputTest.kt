package com.jochana.driverule.core.platform.audio

import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SpeechSessionOutputTest {
    @Test fun `SDK completion releases once and duplicate callbacks are ignored`() = runTest {
        val session = FakeSession()
        val output = SpeechSessionOutput { session }
        val result = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event()) }
        assertEquals("음성 안내 테스트입니다", session.text)
        session.callback(GuidanceSpeechResult.Completed)
        session.callback(GuidanceSpeechResult.Failed)
        assertEquals(GuidanceSpeechResult.Completed, result.await())
        assertEquals(1, session.releases)
    }

    @Test fun `replacement releases old connection and late old callback cannot finish new request`() = runTest {
        val old = FakeSession()
        val current = FakeSession()
        val sessions = ArrayDeque(listOf(old, current))
        val output = SpeechSessionOutput { sessions.removeFirst() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event("first")) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event("second")) }
        assertEquals(GuidanceSpeechResult.Interrupted, first.await())
        assertEquals(1, old.releases)
        old.callback(GuidanceSpeechResult.Completed)
        assertFalse(second.isCompleted)
        assertEquals(0, current.releases)
        current.callback(GuidanceSpeechResult.Completed)
        assertEquals(GuidanceSpeechResult.Completed, second.await())
    }

    @Test fun `cancellation during initialization releases connection and leaves next request usable`() = runTest {
        val old = FakeSession()
        val current = FakeSession()
        val sessions = ArrayDeque(listOf(old, current))
        val output = SpeechSessionOutput { sessions.removeFirst() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event()) }
        first.cancelAndJoin()
        assertEquals(1, old.releases)
        val second = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event("new")) }
        old.callback(GuidanceSpeechResult.Completed)
        assertFalse(second.isCompleted)
        current.callback(GuidanceSpeechResult.FocusDenied)
        assertEquals(GuidanceSpeechResult.FocusDenied, second.await())
        assertEquals(1, current.releases)
    }

    @Test fun `stop interrupts but close permanently prevents another SDK connection`() = runTest {
        val sessions = mutableListOf<FakeSession>()
        val output = SpeechSessionOutput { FakeSession().also(sessions::add) }
        val first = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event()) }
        output.stop()
        assertEquals(GuidanceSpeechResult.Interrupted, first.await())
        val second = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event("new")) }
        output.close()
        output.close()
        assertEquals(GuidanceSpeechResult.Interrupted, second.await())
        assertEquals(GuidanceSpeechResult.Unavailable, output.speak(event("closed")))
        assertEquals(2, sessions.size)
        assertEquals(listOf(1, 1), sessions.map { it.releases })
    }

    @Test fun `synchronous SDK failure still releases and completes`() = runTest {
        val session = FakeSession(startFailure = true)
        val output = SpeechSessionOutput { session }
        assertEquals(GuidanceSpeechResult.Unavailable, output.speak(event()))
        assertEquals(1, session.releases)
    }

    @Test fun `missing SDK is unavailable instead of throwing into service`() = runTest {
        val output = SpeechSessionOutput { throw NoClassDefFoundError("unavailable test SDK") }
        assertEquals(GuidanceSpeechResult.Unavailable, output.speak(event()))
    }

    @Test fun `release callback cannot turn interrupted into completed`() = runTest {
        val session = FakeSession(callbackDuringRelease = true)
        val output = SpeechSessionOutput { session }
        val result = async(start = CoroutineStart.UNDISPATCHED) { output.speak(event()) }
        output.stop()
        assertEquals(GuidanceSpeechResult.Interrupted, result.await())
        assertEquals(1, session.releases)
    }

    @Test fun `blank speech never opens SDK connection`() = runTest {
        val output = SpeechSessionOutput { error("must not create") }
        assertEquals(GuidanceSpeechResult.Unavailable, output.speak(event().copy(speechText = "  ")))
    }

    private fun event(id: String = "sample") = GuidanceEvent(id, "샘플", "화면 안내", 8_000,
        speechText = "음성 안내 테스트입니다")

    private class FakeSession(val startFailure: Boolean = false, val callbackDuringRelease: Boolean = false) : SpeechSession {
        var text: String? = null
        var releases = 0
        lateinit var callback: (GuidanceSpeechResult) -> Unit
        override fun start(text: String, onResult: (GuidanceSpeechResult) -> Unit) {
            this.text = text
            callback = onResult
            if (startFailure) throw IllegalStateException("test start failure")
        }
        override fun release() {
            releases++
            if (callbackDuringRelease) callback(GuidanceSpeechResult.Completed)
        }
    }
}
