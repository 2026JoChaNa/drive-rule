package com.jochana.driverule.core.platform.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

internal enum class SpeechQueueReadiness { Idle, Busy, Blocked }

/** Main-thread queue. Only confirmed SDK cleanup releases the slot; SDK stop is asynchronous. */
internal class SpeechSessionQueue<T : Any>(
    private val start: (T) -> Unit,
    private val reject: (T) -> Unit,
) {
    private val waiting = ArrayDeque<T>()
    private var current: T? = null
    private var blocked = false
    private val mutableReadiness = MutableStateFlow(SpeechQueueReadiness.Idle)
    val readiness: StateFlow<SpeechQueueReadiness> = mutableReadiness.asStateFlow()

    /** Observes cleanup completion without creating a platform session or reserving the queue. */
    suspend fun awaitQuiescence(): Boolean =
        readiness.first { it != SpeechQueueReadiness.Busy } == SpeechQueueReadiness.Idle

    fun enqueue(session: T) {
        if (blocked) {
            reject(session)
            return
        }
        waiting.addLast(session)
        drain()
    }

    fun isCurrent(session: T): Boolean = current === session

    fun removeWaiting(session: T) {
        waiting.removeAll { it === session }
    }

    fun finish(session: T, cleanupConfirmed: Boolean) {
        if (current !== session) return
        current = null
        if (!cleanupConfirmed) {
            blocked = true
            mutableReadiness.value = SpeechQueueReadiness.Blocked
            val rejected = waiting.toList()
            waiting.clear()
            rejected.forEach(reject)
        } else {
            drain()
        }
    }

    private fun drain() {
        if (current != null || blocked) return
        if (waiting.isEmpty()) {
            mutableReadiness.value = SpeechQueueReadiness.Idle
            return
        }
        val next = waiting.removeFirst()
        current = next
        // Keep Busy across queued sessions; a transient Idle could start STT between utterances.
        mutableReadiness.value = SpeechQueueReadiness.Busy
        start(next)
    }
}
