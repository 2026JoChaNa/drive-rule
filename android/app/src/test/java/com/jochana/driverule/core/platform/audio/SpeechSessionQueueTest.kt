package com.jochana.driverule.core.platform.audio

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechSessionQueueTest {
    @Test fun `replacement waits until old SDK cleanup is confirmed`() {
        val started = mutableListOf<Any>()
        val rejected = mutableListOf<Any>()
        val queue = SpeechSessionQueue<Any>({ started += it }, { rejected += it })
        val old = Any()
        val current = Any()
        queue.enqueue(old)
        queue.enqueue(current)
        assertEquals(listOf(old), started)
        assertTrue(queue.isCurrent(old))
        queue.finish(old, cleanupConfirmed = true)
        assertEquals(listOf(old, current), started)
        assertTrue(queue.isCurrent(current))
        queue.finish(old, cleanupConfirmed = false)
        assertTrue(queue.isCurrent(current))
        assertTrue(rejected.isEmpty())
    }

    @Test fun `stop timeout rejects waiting and future sessions without starting SDK`() = runTest {
        val started = mutableListOf<Any>()
        val rejected = mutableListOf<Any>()
        val queue = SpeechSessionQueue<Any>({ started += it }, { rejected += it })
        val old = Any()
        val waiting = Any()
        val future = Any()
        queue.enqueue(old)
        queue.enqueue(waiting)
        val quiescence = async(start = CoroutineStart.UNDISPATCHED) { queue.awaitQuiescence() }
        assertFalse(quiescence.isCompleted)
        queue.finish(old, cleanupConfirmed = false)
        queue.enqueue(future)
        assertEquals(listOf(old), started)
        assertEquals(listOf(waiting, future), rejected)
        assertFalse(queue.isCurrent(old))
        assertEquals(SpeechQueueReadiness.Blocked, queue.readiness.value)
        assertFalse(quiescence.await())
        assertFalse(queue.awaitQuiescence())
    }

    @Test fun `cancelled waiting session is never started after acknowledgement`() {
        val started = mutableListOf<Any>()
        val queue = SpeechSessionQueue<Any>({ started += it }) { error("unexpected rejection") }
        val old = Any()
        val cancelled = Any()
        val next = Any()
        queue.enqueue(old)
        queue.enqueue(cancelled)
        queue.removeWaiting(cancelled)
        queue.enqueue(next)
        queue.finish(old, cleanupConfirmed = true)
        assertEquals(listOf(old, next), started)
    }

    @Test fun `initial idle needs no SDK and busy waits for confirmed cleanup`() = runTest {
        var started = false
        val queue = SpeechSessionQueue<Any>({ started = true }) { error("unexpected rejection") }
        assertEquals(SpeechQueueReadiness.Idle, queue.readiness.value)
        assertTrue(queue.awaitQuiescence())
        assertFalse(started)
        val active = Any()
        queue.enqueue(active)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { queue.awaitQuiescence() }
        assertTrue(started)
        assertEquals(SpeechQueueReadiness.Busy, queue.readiness.value)
        assertFalse(waiting.isCompleted)
        queue.finish(active, cleanupConfirmed = true)
        assertTrue(waiting.await())
        assertEquals(SpeechQueueReadiness.Idle, queue.readiness.value)
    }

    @Test fun `consecutive queued sessions never emit intermediate idle`() = runTest {
        val queue = SpeechSessionQueue<Any>({}) { error("unexpected rejection") }
        val observed = mutableListOf<SpeechQueueReadiness>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queue.readiness.collect { observed += it }
        }
        val first = Any()
        val second = Any()
        queue.enqueue(first)
        queue.enqueue(second)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { queue.awaitQuiescence() }
        queue.finish(first, cleanupConfirmed = true)
        assertEquals(listOf(SpeechQueueReadiness.Idle, SpeechQueueReadiness.Busy), observed)
        assertFalse(waiting.isCompleted)
        queue.finish(second, cleanupConfirmed = true)
        assertTrue(waiting.await())
        assertEquals(
            listOf(SpeechQueueReadiness.Idle, SpeechQueueReadiness.Busy, SpeechQueueReadiness.Idle),
            observed,
        )
    }

    @Test fun `cancelling quiescence wait never changes active SDK state`() = runTest {
        val queue = SpeechSessionQueue<Any>({}) { error("unexpected rejection") }
        val active = Any()
        queue.enqueue(active)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { queue.awaitQuiescence() }
        waiting.cancelAndJoin()
        assertTrue(waiting.isCancelled)
        assertEquals(SpeechQueueReadiness.Busy, queue.readiness.value)
        assertTrue(queue.isCurrent(active))
        queue.finish(active, cleanupConfirmed = true)
        assertTrue(queue.awaitQuiescence())
    }
}
