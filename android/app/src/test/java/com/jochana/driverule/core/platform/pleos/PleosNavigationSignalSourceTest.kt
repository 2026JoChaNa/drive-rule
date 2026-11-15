package com.jochana.driverule.core.platform.pleos

import com.jochana.driverule.domain.vehicle.NavigationActivityStatus
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalUnavailable
import com.jochana.driverule.domain.vehicle.NavigationTurn
import com.jochana.driverule.domain.vehicle.NavigationDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosNavigationSignalSourceTest {
    @Test
    fun `destination expires independently and requests alone do not renew it`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        client.listener.onDestination(NavigationDestination(37.0, 127.0, 800.0))
        fixture.clock.advanceBy(5_000)
        assertEquals(1, client.destinationRefreshRequests)
        assertEquals(800.0, freshValue(fixture.source.state.value.destination).remainingDistanceMeters, 0.0)
        fixture.clock.advanceBy(5_000)
        assertTrue(fixture.source.state.value.destination is NavigationObservation.Stale)
        client.listener.onDestination(NavigationDestination(37.0, 127.0, 400.0))
        assertEquals(400.0, freshValue(fixture.source.state.value.destination).remainingDistanceMeters, 0.0)
        client.listener.onRouteInvalidated()
        assertEquals(NavigationObservation.Unknown, fixture.source.state.value.destination)
    }

    @Test
    fun `destination replacement invalidates old turn and bad coordinates never become fresh`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        client.listener.onDestination(NavigationDestination(37.0, 127.0, 800.0))
        client.listener.onTurns(listOf(NavigationTurn("TURN_RIGHT", 150.0)))
        val revision = fixture.source.state.value.routeRevision
        client.listener.onDestination(NavigationDestination(38.0, 127.0, 400.0))
        assertTrue(fixture.source.state.value.routeRevision > revision)
        assertEquals(NavigationObservation.Unknown, fixture.source.state.value.turns)
        listOf(NavigationDestination(Double.NaN, 127.0, 400.0),
            NavigationDestination(91.0, 127.0, 400.0), NavigationDestination(37.0, 181.0, 400.0),
            NavigationDestination(37.0, 127.0, -1.0)).forEach {
            client.listener.onDestination(it)
            assertTrue(fixture.source.state.value.destination is NavigationObservation.Unavailable)
        }
        client.listener.onDestination(NavigationDestination(37.0, 127.0, 400.0))
        client.listener.onStatus(NavigationActivityStatus.FreeDrive)
        assertEquals(NavigationObservation.Unknown, fixture.source.state.value.destination)
    }

    @Test
    fun `main queue delay cannot renew old navigation callback`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        fixture.clock.queueCommands = true
        client.listener.onDestination(NavigationDestination(37.0, 127.0, 400.0))
        fixture.clock.advanceBy(10_000)
        fixture.clock.flushCommands()
        assertFalse(fixture.source.state.value.destination is NavigationObservation.Fresh)
    }

    @Test
    fun `start is idempotent and stop rejects callbacks from a replaced session`() {
        val fixture = Fixture()
        fixture.source.start()
        fixture.source.start()
        assertEquals(1, fixture.clients.size)
        val old = fixture.clients.single()
        old.connected()
        old.listener.onTurns(listOf(NavigationTurn("TURN_RIGHT", 150.0)))
        assertEquals(1, old.snapshotRequests)
        assertTrue(fixture.source.state.value.turns is NavigationObservation.Fresh)

        fixture.source.stop()
        fixture.source.stop()
        assertEquals(1, old.releases)
        assertEquals(NavigationConnection.Stopped, fixture.source.state.value.connection)
        fixture.source.start()
        val current = fixture.clients.last()
        old.connected()
        old.listener.onTurns(listOf(NavigationTurn("TURN_LEFT", 1.0)))
        old.failed(SecurityException())
        assertEquals(NavigationConnection.Connecting, fixture.source.state.value.connection)
        assertEquals(NavigationObservation.Unknown, fixture.source.state.value.turns)
        current.connected()
        current.listener.onTurns(emptyList())
        assertEquals(emptyList<NavigationTurn>(), freshValue(fixture.source.state.value.turns))
    }

    @Test
    fun `initialization timeout releases SDK and cannot be revived by late success`() {
        val fixture = Fixture()
        fixture.source.start()
        val old = fixture.clients.single()
        fixture.clock.advanceBy(15_000)
        assertEquals(NavigationConnection.Unavailable, fixture.source.state.value.connection)
        assertEquals(1, old.releases)
        old.connected()
        old.listener.onRouteActive(true)
        fixture.source.start()
        assertEquals(1, fixture.clients.size)
        assertEquals(0, old.snapshotRequests)
        assertTrue(fixture.source.state.value.routeActive is NavigationObservation.Unavailable)
        fixture.source.stop()
        fixture.source.start()
        assertEquals(2, fixture.clients.size)
    }

    @Test
    fun `permission denial avoids SDK creation and revocation discards observations`() {
        val denied = Fixture().apply { permission = false }
        denied.source.start()
        assertTrue(denied.clients.isEmpty())
        assertEquals(NavigationConnection.PermissionDenied, denied.source.state.value.connection)

        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        client.listener.onRouteActive(true)
        fixture.permission = false
        fixture.clock.advanceBy(1_000)
        assertEquals(NavigationConnection.PermissionDenied, fixture.source.state.value.connection)
        assertEquals(1, client.releases)
        assertEquals(
            NavigationObservation.Unavailable(NavigationSignalUnavailable.PermissionDenied),
            fixture.source.state.value.routeActive,
        )
    }

    @Test
    fun `each observation expires without turning route status into false`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        client.listener.onStatus(NavigationActivityStatus.RouteDrive)
        client.listener.onTurns(listOf(NavigationTurn("TURN_RIGHT", 250.0)))
        fixture.clock.advanceBy(9_000)
        client.listener.onSpeedLimit(30)
        fixture.clock.advanceBy(1_000)
        assertEquals(NavigationObservation.Stale(0), fixture.source.state.value.routeActive)
        assertEquals(NavigationObservation.Stale(0), fixture.source.state.value.navigationStatus)
        assertEquals(NavigationObservation.Stale(0), fixture.source.state.value.turns)
        assertEquals(30, freshValue(fixture.source.state.value.rawReportedRoadSpeedLimit))
        assertEquals(NavigationObservation.Unknown, fixture.source.state.value.cautions)
        assertEquals(NavigationConnection.Connected, fixture.source.state.value.connection)
        assertEquals(2, client.routeRefreshRequests)
    }

    @Test
    fun `unchanged active route is kept fresh only by read responses and refresh stops on disposal`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        client.listener.onRouteActive(true)
        client.routeRefreshResponse = { client.listener.onRouteActive(true) }
        fixture.clock.advanceBy(35_000)
        assertEquals(7, client.routeRefreshRequests)
        assertTrue(freshValue(fixture.source.state.value.routeActive))
        assertEquals(NavigationObservation.Fresh(true, 35_000), fixture.source.state.value.routeActive)

        // A living SDK object is not proof that a missing response contains current route data.
        client.routeRefreshResponse = null
        fixture.clock.advanceBy(10_000)
        assertEquals(NavigationObservation.Stale(35_000), fixture.source.state.value.routeActive)
        fixture.source.stop()
        val requestsBeforeStop = client.routeRefreshRequests
        fixture.clock.advanceBy(30_000)
        assertEquals(requestsBeforeStop, client.routeRefreshRequests)
    }

    @Test
    fun `permission failure during periodic route read invalidates values and releases the SDK`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        client.listener.onRouteActive(true)
        client.routeRefreshResponse = { throw SecurityException() }
        fixture.clock.advanceBy(5_000)
        assertEquals(NavigationConnection.PermissionDenied, fixture.source.state.value.connection)
        assertTrue(fixture.source.state.value.routeActive is NavigationObservation.Unavailable)
        assertEquals(1, client.releases)
        fixture.clock.advanceBy(30_000)
        assertEquals(1, client.routeRefreshRequests)
    }

    @Test
    fun `route replacement immediately discards previous maneuver and invalid data is not empty evidence`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single()
        client.connected()
        client.listener.onRouteActive(true)
        client.listener.onTurns(listOf(NavigationTurn("TURN_RIGHT", 140.0)))
        client.listener.onRouteInvalidated()
        assertEquals(NavigationObservation.Unknown, fixture.source.state.value.routeActive)
        assertEquals(NavigationObservation.Unknown, fixture.source.state.value.turns)
        client.listener.onTurns(null)
        client.listener.onSpeedLimit(0)
        assertEquals(
            NavigationObservation.Unavailable(NavigationSignalUnavailable.InvalidData),
            fixture.source.state.value.turns,
        )
        assertTrue(fixture.source.state.value.rawReportedRoadSpeedLimit is NavigationObservation.Unavailable)
        client.listener.onRouteActive(false)
        assertFalse(freshValue(fixture.source.state.value.routeActive))
    }

    @Test
    fun `synchronous SDK access failure cleans up and releases even while initializing`() {
        val fixture = Fixture()
        fixture.source.start()
        val client = fixture.clients.single().apply { listenFailure = SecurityException() }
        client.connected()
        assertEquals(NavigationConnection.PermissionDenied, fixture.source.state.value.connection)
        assertEquals(1, client.releases)
        fixture.clock.advanceBy(30_000)
        assertEquals(1, client.releases)

        val initializing = Fixture()
        initializing.source.start()
        initializing.source.stop()
        initializing.clients.single().connected()
        assertEquals(1, initializing.clients.single().releases)
        assertEquals(0, initializing.clients.single().snapshotRequests)
    }

    private fun <T> freshValue(observation: NavigationObservation<T>): T =
        (observation as NavigationObservation.Fresh<T>).value

    private class Fixture {
        val clock = FakeDispatcher()
        var permission = true
        val clients = mutableListOf<FakeClient>()
        val source = PleosNavigationSignalSource(
            clientFactory = { listener -> FakeClient(listener).also(clients::add) },
            permissionGranted = { permission },
            dispatcher = clock,
        )
    }

    private class FakeClient(val listener: NavigationClient.Listener) : NavigationClient {
        lateinit var connected: () -> Unit
        lateinit var failed: (Throwable) -> Unit
        var snapshotRequests = 0
        var routeRefreshRequests = 0
        var destinationRefreshRequests = 0
        var routeRefreshResponse: (() -> Unit)? = null
        var releases = 0
        var listenFailure: RuntimeException? = null
        override fun initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit) {
            connected = onConnected
            failed = onFailed
        }
        override fun listenAndRequestSnapshot() {
            listenFailure?.let { throw it }
            snapshotRequests++
        }
        override fun requestDestination() { destinationRefreshRequests++ }
        override fun release() { releases++ }
        override fun requestRouteState() {
            routeRefreshRequests++
            routeRefreshResponse?.invoke()
        }
    }

    private class FakeDispatcher : NavigationDispatcher {
        private data class Task(val dueAt: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        private var now = 0L
        var queueCommands = false
        private val commands = mutableListOf<() -> Unit>()
        override fun execute(action: () -> Unit) { if (queueCommands) commands += action else action() }
        fun flushCommands() {
            queueCommands = false
            commands.toList().also { commands.clear() }.forEach { it() }
        }
        override fun elapsedRealtimeMs() = now
        override fun schedule(delayMs: Long, action: () -> Unit): NavigationCancellation {
            val task = Task(now + delayMs, action)
            tasks += task
            return NavigationCancellation { task.cancelled = true }
        }
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
}
