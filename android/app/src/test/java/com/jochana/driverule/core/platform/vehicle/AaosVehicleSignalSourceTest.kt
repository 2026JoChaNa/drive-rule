package com.jochana.driverule.core.platform.vehicle

import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.vehicle.IgnitionState
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.SignalStatus
import com.jochana.driverule.domain.vehicle.VehicleSignal
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import com.jochana.driverule.domain.vehicle.newerSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AaosVehicleSignalSourceTest {
    @Test fun `start is idempotent and accepts synchronous initial ready and values`() {
        var creations = 0
        val controller = VehicleSignalController({ true }) { observer ->
            creations++
            FakeSession(observer) {
                val epoch = requireNotNull(observer.onReady())
                observer.update(epoch) { it.copy(gear = available(SelectedGear.Park)) }
            }
        }
        controller.start()
        controller.start()
        assertEquals(1, creations)
        assertEquals(SignalConnection.Connected, controller.state.value.connection)
        assertEquals(SelectedGear.Park, controller.state.value.gear.availableValue)
        assertEquals(VehicleUxState.Unknown, controller.state.value.uxState)
    }

    @Test fun `stop releases session once and late callbacks cannot revive it`() {
        val fixture = Fixture()
        fixture.source.start()
        val session = fixture.sessions.single()
        val epoch = session.ready()
        session.observer.update(epoch) { it.copy(ux = available(VehicleUxState.Unrestricted)) }
        fixture.source.stop()
        fixture.source.stop()
        session.observer.update(epoch) { it.copy(ux = available(VehicleUxState.Unrestricted)) }
        session.observer.onLost()
        assertNull(session.observer.onReady())
        assertEquals(1, session.closeCount)
        assertEquals(SignalConnection.Stopped, fixture.source.state.value.connection)
        assertEquals(VehicleUxState.Unknown, fixture.source.state.value.uxState)
        assertEquals(SignalStatus.Stale, fixture.source.state.value.ux.status)
    }

    @Test fun `restart creates a new session and rejects previous session failure`() {
        val fixture = Fixture()
        fixture.source.start()
        val first = fixture.sessions.single()
        val oldEpoch = first.ready()
        fixture.source.stop()
        fixture.source.start()
        val second = fixture.sessions.last()
        val newEpoch = second.ready()
        second.observer.update(newEpoch) { it.copy(gear = available(SelectedGear.Drive)) }
        first.observer.update(oldEpoch) { it.copy(gear = available(SelectedGear.Park)) }
        first.observer.onFailure(SignalConnection.PermissionDenied)
        assertEquals(2, fixture.sessions.size)
        assertEquals(SignalConnection.Connected, fixture.source.state.value.connection)
        assertEquals(SelectedGear.Drive, fixture.source.state.value.gear.availableValue)
    }

    @Test fun `lost connection invalidates on-change values and old subscription callbacks`() {
        val fixture = Fixture()
        fixture.source.start()
        val session = fixture.sessions.single()
        val epoch = session.ready()
        session.observer.update(epoch) { it.copy(gear = available(SelectedGear.Park),
            parkingBrake = available(true), ignition = available(IgnitionState.On),
            ux = available(VehicleUxState.Unrestricted)) }
        session.observer.onLost()
        val lost = fixture.source.state.value
        assertEquals(SignalConnection.Connecting, lost.connection)
        assertEquals(SignalStatus.Stale, lost.gear.status)
        assertEquals(SignalStatus.Stale, lost.parkingBrake.status)
        assertEquals(SignalStatus.Stale, lost.ignition.status)
        assertEquals(VehicleUxState.Unknown, lost.uxState)
        session.observer.update(epoch) { it.copy(gear = available(SelectedGear.Drive)) }
        assertEquals(lost, fixture.source.state.value)
        val newEpoch = session.ready()
        assertEquals(SignalStatus.Unknown, fixture.source.state.value.gear.status)
        session.observer.update(newEpoch) { it.copy(gear = available(SelectedGear.Neutral)) }
        session.observer.update(epoch) { it.copy(gear = available(SelectedGear.Park)) }
        assertEquals(SelectedGear.Neutral, fixture.source.state.value.gear.availableValue)
    }

    @Test fun `fresh connection resets ordering watermark while rejecting old connection callbacks`() {
        val fixture = Fixture()
        fixture.source.start()
        val session = fixture.sessions.single()
        val first = session.ready()
        session.observer.update(first) { it.copy(gear = available(SelectedGear.Drive, 20_000)) }
        session.observer.update(first) { it.copy(gear = newerSignal(it.gear,
            VehicleSignal(status = SignalStatus.Failed))) }
        assertEquals(20_000L, fixture.source.state.value.gear.sourceTimestampWatermarkElapsedMs)
        session.observer.onLost()
        val second = session.ready()
        assertNull(fixture.source.state.value.gear.sourceTimestampWatermarkElapsedMs)
        session.observer.update(second) { it.copy(gear = newerSignal(it.gear, available(SelectedGear.Park, 19_000))) }
        session.observer.update(first) { it.copy(gear = available(SelectedGear.Drive, 21_000)) }
        assertEquals(SelectedGear.Park, fixture.source.state.value.gear.availableValue)
        assertEquals(19_000L, fixture.source.state.value.gear.sourceTimestampWatermarkElapsedMs)
    }

    @Test fun `permission denial on speed is independent of UX and powertrain availability`() {
        val fixture = Fixture()
        fixture.source.start()
        val session = fixture.sessions.single()
        val epoch = session.ready()
        session.observer.update(epoch) { it.copy(
            ux = available(VehicleUxState.Restricted),
            gear = available(SelectedGear.Park),
            speedMetersPerSecond = VehicleSignal(status = SignalStatus.PermissionDenied),
        ) }
        assertEquals(SignalConnection.Connected, fixture.source.state.value.connection)
        assertEquals(VehicleUxState.Restricted, fixture.source.state.value.uxState)
        assertEquals(SelectedGear.Park, fixture.source.state.value.gear.availableValue)
        assertNull(fixture.source.state.value.speedMetersPerSecond.availableValue)
    }

    @Test fun `non automotive device never creates a platform session`() {
        val controller = VehicleSignalController({ false }) { error("must not create Car") }
        controller.start()
        assertEquals(SignalConnection.Unavailable, controller.state.value.connection)
        assertEquals(VehicleUxState.Unknown, controller.state.value.uxState)
        assertFalse(controller.isRunning)
    }

    @Test fun `missing optional library and failed class verification are unavailable`() {
        listOf<Throwable>(ClassNotFoundException(), NoClassDefFoundError()).forEach { failure ->
            val controller = VehicleSignalController({ throw failure }) { error("unreachable") }
            controller.start()
            assertEquals(SignalConnection.Unavailable, controller.state.value.connection)
            assertFalse(controller.isRunning)
        }
    }

    @Test fun `connection security error closes created session and allows retry`() {
        lateinit var session: FakeSession
        var allowed = false
        val controller = VehicleSignalController({ true }) { observer ->
            FakeSession(observer) {
                if (!allowed) throw SecurityException("denied")
                observer.onReady()
            }.also { session = it }
        }
        controller.start()
        assertEquals(SignalConnection.PermissionDenied, controller.state.value.connection)
        assertEquals(1, session.closeCount)
        allowed = true
        controller.start()
        assertEquals(SignalConnection.Connected, controller.state.value.connection)
        assertTrue(controller.isRunning)
    }

    @Test fun `registration failure is not reported as a safe zero value`() {
        val fixture = Fixture()
        fixture.source.start()
        val session = fixture.sessions.single()
        val epoch = session.ready()
        session.observer.update(epoch) { it.copy(
            speedMetersPerSecond = VehicleSignal(status = SignalStatus.Failed),
            parkingBrake = VehicleSignal(status = SignalStatus.Unavailable),
        ) }
        assertNull(fixture.source.state.value.speedMetersPerSecond.availableValue)
        assertNull(fixture.source.state.value.parkingBrake.availableValue)
        assertEquals(VehicleUxState.Unknown, fixture.source.state.value.uxState)
    }

    @Test fun `speed watchdog expires at boundary but does not age out on-change gear`() {
        val fixture = Fixture()
        fixture.source.start()
        val session = fixture.sessions.single()
        val epoch = session.ready()
        session.observer.update(epoch) { it.copy(speedMetersPerSecond = available(0f, 1_000),
            gear = available(SelectedGear.Park, 1_000)) }
        fixture.source.expireSpeed(5_999)
        assertEquals(SignalStatus.Available, fixture.source.state.value.speedMetersPerSecond.status)
        fixture.source.expireSpeed(6_000)
        assertEquals(SignalStatus.Stale, fixture.source.state.value.speedMetersPerSecond.status)
        assertEquals(SignalStatus.Available, fixture.source.state.value.gear.status)
        session.observer.update(epoch) { it.copy(speedMetersPerSecond = available(2f, 6_100)) }
        assertEquals(2f, fixture.source.state.value.speedMetersPerSecond.availableValue)
    }

    @Test fun `watchdog checks access without a property event and stops polling after stop`() {
        val fixture = Fixture()
        fixture.source.start()
        val session = fixture.sessions.single()
        val epoch = session.ready()
        var polls = 0
        session.onRefresh = {
            polls++
            session.observer.update(epoch) { it.copy(gear = VehicleSignal(status = SignalStatus.PermissionDenied)) }
        }
        fixture.source.expireSpeed(1_000)
        assertEquals(1, polls)
        assertEquals(SignalStatus.PermissionDenied, fixture.source.state.value.gear.status)
        fixture.source.stop()
        fixture.source.expireSpeed(2_000)
        assertEquals(1, polls)
    }

    @Test fun `gear mapping distinguishes selected park reverse neutral drive and unknown`() {
        listOf(1 to SelectedGear.Neutral, 2 to SelectedGear.Reverse, 4 to SelectedGear.Park,
            8 to SelectedGear.Drive, 16 to SelectedGear.First, 4096 to SelectedGear.Ninth).forEach { (raw, expected) ->
            assertEquals(expected, AaosSignalValues.gear(raw, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        }
        listOf(null, 0, 999, "4").forEach { raw ->
            val signal = AaosSignalValues.gear(raw, SignalStatus.Available, 1_000, 1_000_000_000)
            assertEquals(SignalStatus.Unknown, signal.status)
            assertNull(signal.availableValue)
        }
    }

    @Test fun `ignition undefined is not off`() {
        assertNull(AaosSignalValues.ignition(0, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        assertEquals(IgnitionState.Off, AaosSignalValues.ignition(2, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        assertEquals(IgnitionState.Accessory, AaosSignalValues.ignition(3, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        assertEquals(IgnitionState.On, AaosSignalValues.ignition(4, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
    }

    @Test fun `speed retains signed meters per second and rejects nonfinite or wrong type`() {
        assertEquals(-3.5f, AaosSignalValues.speed(-3.5f, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        assertEquals(0f, AaosSignalValues.speed(0f, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        listOf(null, 0, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { raw ->
            assertNull(AaosSignalValues.speed(raw, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        }
    }

    @Test fun `unavailable or error payload is discarded instead of normalized`() {
        listOf(SignalStatus.Unavailable, SignalStatus.Failed, SignalStatus.PermissionDenied).forEach { status ->
            val signal = AaosSignalValues.parkingBrake(true, status, 1_000, 1_000_000_000)
            assertEquals(status, signal.status)
            assertNull(signal.value)
        }
        assertEquals(false, AaosSignalValues.parkingBrake(false, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
        assertNull(AaosSignalValues.parkingBrake(null, SignalStatus.Available, 1_000, 1_000_000_000).availableValue)
    }

    @Test fun `invalid and far future timestamps cannot make speed available`() {
        listOf(-1L, 10_000_000_000L).forEach { timestamp ->
            assertEquals(SignalStatus.Failed, AaosSignalValues.speed(0f, SignalStatus.Available, 1_000, timestamp).status)
        }
        val value = AaosSignalValues.speed(1f, SignalStatus.Available, 1_000, 999_000_000)
        assertEquals(999L, value.sourceTimestampElapsedMs)
        assertEquals(1_000L, value.observedAtElapsedMs)
    }

    @Test fun `future error timestamp cannot block subsequent valid measurements forever`() {
        val valid = AaosSignalValues.gear(8, SignalStatus.Available, 20_000, 20_000_000_000)
        val futureError = AaosSignalValues.gear(null, SignalStatus.Failed, 21_000, Long.MAX_VALUE)
        assertNull(futureError.sourceTimestampElapsedMs)
        val failed = newerSignal(valid, futureError)
        assertEquals(20_000L, failed.sourceTimestampWatermarkElapsedMs)
        val next = AaosSignalValues.gear(4, SignalStatus.Available, 22_000, 22_000_000_000)
        assertEquals(next, newerSignal(failed, next))
    }

    private class Fixture {
        val sessions = mutableListOf<FakeSession>()
        val source = VehicleSignalController({ true }) { observer ->
            FakeSession(observer).also(sessions::add)
        }
    }

    private class FakeSession(
        val observer: VehiclePlatformObserver,
        val onOpen: () -> Unit = {},
    ) : VehiclePlatformSession {
        var closeCount = 0
        var onRefresh: () -> Unit = {}
        override fun open() = onOpen()
        override fun close() { closeCount++ }
        override fun refreshPermissions() = onRefresh()
        fun ready(): Long = requireNotNull(observer.onReady())
    }

    private companion object {
        fun <T> available(value: T, now: Long = 1_000): VehicleSignal<T> =
            VehicleSignal(SignalStatus.Available, value, now, now)
    }
}
