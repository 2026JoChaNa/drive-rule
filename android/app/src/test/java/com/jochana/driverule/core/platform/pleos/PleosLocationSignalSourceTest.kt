package com.jochana.driverule.core.platform.pleos

import com.jochana.driverule.domain.vehicle.LocationConnection
import com.jochana.driverule.domain.vehicle.LocationObservation
import com.jochana.driverule.domain.vehicle.LocationUnavailable
import com.jochana.driverule.domain.vehicle.VehicleLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosLocationSignalSourceTest {
    @Test
    fun `installed SDK is not connected and first continuous fix is only a baseline`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        assertEquals(LocationConnection.Connecting, fixture.source.state.value.connection)
        assertEquals(LocationObservation.Unknown, fixture.source.state.value.position)
        client.location(1_000)
        assertEquals(LocationConnection.Connected, fixture.source.state.value.connection)
        assertEquals(LocationObservation.Unknown, fixture.source.state.value.position)
        fixture.clock.advanceBy(1_000)
        client.location(2_000)
        assertEquals(LocationObservation.Fresh(position(2_000), 1_000), fixture.source.state.value.position)
        fixture.source.stop()
    }

    @Test
    fun `same and backward timestamps do not refresh a fix or revive it after five seconds`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.location(1_000)
        client.location(2_000)
        val revision = fixture.source.state.value.sourceRevision
        fixture.clock.advanceBy(4_000)
        client.location(2_000)
        client.location(1_500)
        assertEquals(LocationObservation.Fresh(position(2_000), 0), fixture.source.state.value.position)
        fixture.clock.advanceBy(1_000)
        assertEquals(LocationObservation.Stale(0), fixture.source.state.value.position)
        assertEquals(revision + 1, fixture.source.state.value.sourceRevision)
        client.location(2_000)
        assertEquals(LocationObservation.Stale(0), fixture.source.state.value.position)
        client.location(3_000)
        assertEquals(LocationObservation.Unknown, fixture.source.state.value.position)
        client.location(4_000)
        assertEquals(LocationObservation.Fresh(position(4_000), 5_000), fixture.source.state.value.position)
        fixture.source.stop()
    }

    @Test
    fun `a repeated baseline becomes stale without ever exposing coordinates`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.location(0) // Treat as an ordering token, not a presumed Unix epoch.
        fixture.clock.advanceBy(4_000)
        client.location(0)
        fixture.clock.advanceBy(1_000)
        assertEquals(LocationObservation.Stale(0), fixture.source.state.value.position)
        fixture.source.stop()
    }

    @Test
    fun `start and stop are idempotent and old callbacks cannot modify a new source session`() {
        val fixture = Fixture()
        fixture.source.start()
        fixture.source.start()
        val old = fixture.clients.single()
        old.location(1_000)
        old.location(2_000)
        val firstRevision = fixture.source.state.value.sourceRevision
        fixture.source.stop()
        fixture.source.stop()
        assertEquals(1, old.releases)
        assertEquals(0, fixture.clock.activeTasks)

        fixture.source.start()
        assertEquals(firstRevision + 1, fixture.source.state.value.sourceRevision)
        old.location(3_000)
        old.listener.onError(SecurityException())
        assertEquals(LocationConnection.Connecting, fixture.source.state.value.connection)
        assertEquals(LocationObservation.Unknown, fixture.source.state.value.position)
        val current = fixture.clients.last()
        current.location(100)
        current.location(200)
        assertEquals(position(200), (fixture.source.state.value.position as LocationObservation.Fresh).value)
        fixture.source.stop()
    }

    @Test
    fun `permission denial does not create SDK and revocation releases existing subscription`() {
        val denied = Fixture().apply { permission = false }
        denied.source.start()
        assertTrue(denied.clients.isEmpty())
        assertEquals(LocationConnection.PermissionDenied, denied.source.state.value.connection)

        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.location(1_000)
        client.location(2_000)
        fixture.permission = false
        fixture.clock.advanceBy(1_000)
        assertEquals(LocationConnection.PermissionDenied, fixture.source.state.value.connection)
        assertEquals(LocationObservation.Unavailable(LocationUnavailable.PermissionDenied), fixture.source.state.value.position)
        assertEquals(1, client.releases)
        assertEquals(0, fixture.clock.activeTasks)
    }

    @Test
    fun `permission revoked between timer checks is caught before accepting a callback`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.location(1_000)
        fixture.permission = false
        client.location(2_000)
        assertEquals(LocationConnection.PermissionDenied, fixture.source.state.value.connection)
        assertEquals(1, client.releases)
    }

    @Test
    fun `missing service avoids initialization and no callback times out with cleanup`() {
        val unsupported = Fixture().apply { supported = false }
        unsupported.source.start()
        assertEquals(0, unsupported.clients.single().starts)
        assertEquals(1, unsupported.clients.single().releases)
        assertEquals(LocationConnection.Unavailable, unsupported.source.state.value.connection)

        val fixture = Fixture()
        fixture.source.start()
        val old = fixture.clients.single()
        fixture.clock.advanceBy(15_000)
        assertEquals(LocationConnection.Unavailable, fixture.source.state.value.connection)
        assertEquals(1, old.releases)
        assertEquals(0, fixture.clock.activeTasks)
        old.location(1_000)
        old.location(2_000)
        fixture.source.start()
        assertEquals(1, fixture.clients.size) // Failures are latched, not a retry loop.
        fixture.source.stop()
        fixture.source.start()
        assertEquals(2, fixture.clients.size)
        fixture.source.stop()
    }

    @Test
    fun `invalid coordinates confidence and null discard values without resetting the watermark`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.location(1_000)
        client.location(2_000)
        listOf(
            null,
            position(3_000).copy(latitude = Double.NaN),
            position(3_000).copy(longitude = 181.0),
            position(3_000).copy(rawConfidence = Float.POSITIVE_INFINITY),
            position(3_000).copy(rawSourceTimestampMillis = -1),
        ).forEach { invalid ->
            client.listener.onLocation(invalid)
            assertEquals(LocationObservation.Unavailable(LocationUnavailable.InvalidData), fixture.source.state.value.position)
            client.location(2_000)
            assertEquals(LocationObservation.Unavailable(LocationUnavailable.InvalidData), fixture.source.state.value.position)
        }
        // No invented confidence range: the official contract only supplies a raw Float.
        client.listener.onLocation(position(3_000).copy(rawConfidence = 83f))
        assertEquals(LocationObservation.Unknown, fixture.source.state.value.position)
        client.listener.onLocation(position(4_000).copy(rawConfidence = 83f))
        assertEquals(83f, (fixture.source.state.value.position as LocationObservation.Fresh).value.rawConfidence, 0f)
        fixture.source.stop()
    }

    @Test
    fun `SDK callback errors remove all data and reject later success`() {
        listOf(Exception("privacy unavailable") to LocationConnection.Unavailable,
            SecurityException() to LocationConnection.PermissionDenied).forEach { (error, expected) ->
            val fixture = Fixture()
            fixture.source.start()
            val client = fixture.clients.single()
            client.location(1_000)
            client.location(2_000)
            client.listener.onError(error)
            client.location(3_000)
            assertEquals(expected, fixture.source.state.value.connection)
            assertTrue(fixture.source.state.value.position is LocationObservation.Unavailable)
            assertEquals(1, client.releases)
            assertEquals(0, fixture.clock.activeTasks)
        }
    }

    @Test
    fun `synchronous startup failure cleans up and missing SDK class stays unavailable`() {
        val fixture = Fixture().apply { startupFailure = SecurityException() }
        fixture.source.start()
        assertEquals(LocationConnection.PermissionDenied, fixture.source.state.value.connection)
        assertEquals(1, fixture.clients.single().releases)
        assertEquals(0, fixture.clock.activeTasks)

        val absent = PleosLocationSignalSource(
            clientFactory = { throw NoClassDefFoundError() },
            permissionGranted = { true },
            dispatcher = FakeDispatcher(),
        )
        absent.start()
        assertEquals(LocationConnection.Unavailable, absent.state.value.connection)
    }

    @Test
    fun `callbacks wait for the owner dispatcher and a queued old callback is ignored after stop`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        fixture.clock.queueExecutions = true
        client.location(1_000)
        client.location(2_000)
        assertEquals(LocationConnection.Connecting, fixture.source.state.value.connection)
        fixture.clock.flushExecutions()
        assertTrue(fixture.source.state.value.position is LocationObservation.Fresh)

        fixture.source.stop()
        client.location(3_000)
        fixture.clock.flushExecutions()
        assertEquals(LocationConnection.Stopped, fixture.source.state.value.connection)
        assertEquals(LocationObservation.Unavailable(LocationUnavailable.SourceStopped), fixture.source.state.value.position)
        assertEquals(1, client.releases)
    }

    @Test
    fun `main queue delay cannot turn an expired callback into a fresh fix`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.location(1_000)
        client.location(2_000)
        val revision = fixture.source.state.value.sourceRevision
        fixture.clock.queueExecutions = true
        client.location(3_000) // Received at elapsed 0, but processed at elapsed 5,000.
        fixture.clock.advanceBy(5_000)
        fixture.clock.flushExecutions()
        assertEquals(LocationObservation.Stale(0), fixture.source.state.value.position)
        assertEquals(revision + 1, fixture.source.state.value.sourceRevision)

        fixture.clock.queueExecutions = false
        client.location(3_000) // A timely replay of the delayed token is still unusable.
        assertEquals(LocationObservation.Stale(0), fixture.source.state.value.position)
        client.location(4_000)
        assertEquals(LocationObservation.Unknown, fixture.source.state.value.position)
        client.location(5_000)
        assertEquals(LocationObservation.Fresh(position(5_000), 5_000), fixture.source.state.value.position)
        fixture.clock.queueExecutions = true
        client.location(6_000)
        fixture.clock.advanceBy(500)
        fixture.clock.flushExecutions()
        assertEquals(LocationObservation.Fresh(position(6_000), 5_000), fixture.source.state.value.position)
        fixture.clock.queueExecutions = false
        fixture.source.stop()
    }

    @Test
    fun `receipt order regression cannot promote a newer source token to fresh`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.location(1_000)
        client.location(2_000)
        val revision = fixture.source.state.value.sourceRevision
        fixture.clock.queueExecutions = true
        client.location(4_000) // Earlier receipt waits while another Binder thread gets ahead.
        fixture.clock.advanceBy(100)
        client.location(3_000)
        fixture.clock.flushExecution(1)
        assertEquals(LocationObservation.Fresh(position(3_000), 100), fixture.source.state.value.position)
        fixture.clock.flushExecutions()
        assertEquals(LocationObservation.Stale(100), fixture.source.state.value.position)
        assertEquals(revision + 1, fixture.source.state.value.sourceRevision)

        fixture.clock.queueExecutions = false
        client.location(4_000) // The regressed callback's newer token was consumed.
        assertEquals(LocationObservation.Stale(100), fixture.source.state.value.position)
        client.location(5_000)
        assertEquals(LocationObservation.Unknown, fixture.source.state.value.position)
        fixture.clock.advanceBy(100)
        client.location(6_000)
        assertEquals(LocationObservation.Fresh(position(6_000), 200), fixture.source.state.value.position)
        fixture.source.stop()
    }

    @Test
    fun `binding lifetime closes once and rejects retries and late connection callbacks`() {
        val lifetime = LocationBindingLifetime()
        var binds = 0
        var callbacks = 0
        var cleanups = 0
        assertEquals(true, lifetime.whileOpen { binds++; true })
        lifetime.whileOpen { callbacks++ }
        lifetime.close { cleanups++ }
        repeat(31) { assertNull(lifetime.whileOpen { binds++; true }) }
        assertNull(lifetime.whileOpen { callbacks++ })
        lifetime.close { cleanups++ }
        assertEquals(1, binds)
        assertEquals(1, callbacks)
        assertEquals(1, cleanups)
    }

    private class Fixture {
        val clock = FakeDispatcher()
        var permission = true
        var supported = true
        var startupFailure: RuntimeException? = null
        val clients = mutableListOf<FakeClient>()
        val source = PleosLocationSignalSource(
            clientFactory = { listener -> FakeClient(listener, supported, startupFailure).also(clients::add) },
            permissionGranted = { permission },
            dispatcher = clock,
        )
    }

    private class FakeClient(
        val listener: LocationClient.Listener,
        private val supported: Boolean,
        private val startupFailure: RuntimeException?,
    ) : LocationClient {
        var starts = 0
        var releases = 0
        override fun isSupported(): Boolean = supported
        override fun startContinuousUpdates() {
            starts++
            startupFailure?.let { throw it }
        }
        override fun release() { releases++ }
        fun location(timestamp: Long) = listener.onLocation(position(timestamp))
    }

    private class FakeDispatcher : LocationDispatcher {
        private data class Task(val dueAt: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        private val queued = mutableListOf<() -> Unit>()
        private var now = 0L
        var queueExecutions = false
        val activeTasks: Int get() = tasks.count { !it.cancelled }
        override fun execute(action: () -> Unit) {
            if (queueExecutions) queued += action else action()
        }
        override fun elapsedRealtimeMs(): Long = now
        override fun schedule(delayMs: Long, action: () -> Unit): LocationCancellation {
            val task = Task(now + delayMs, action)
            tasks += task
            return LocationCancellation { task.cancelled = true }
        }
        fun flushExecutions() {
            while (queued.isNotEmpty()) queued.removeAt(0).invoke()
        }
        fun flushExecution(index: Int) = queued.removeAt(index).invoke()
        fun advanceBy(durationMs: Long) {
            val target = now + durationMs
            while (true) {
                val task = tasks.filter { !it.cancelled && it.dueAt <= target }.minByOrNull { it.dueAt } ?: break
                tasks.remove(task)
                now = task.dueAt
                task.action()
            }
            now = target
        }
    }

    private companion object {
        fun position(timestamp: Long) = VehicleLocation(37.5, 127.0, 0.8f, timestamp)
    }
}
