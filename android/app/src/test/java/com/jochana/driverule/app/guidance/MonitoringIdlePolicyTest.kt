package com.jochana.driverule.app.guidance

import com.jochana.driverule.domain.vehicle.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringIdlePolicyTest {
    private val noNavigation = NavigationSignalSnapshot()
    private fun <T> available(value: T) = VehicleSignal(SignalStatus.Available, value, 0L, 0L)
    private val parked = VehicleSignalSnapshot(connection = SignalConnection.Connected,
        gear = available(SelectedGear.Park), ignition = available(IgnitionState.On))

    @Test fun `hidden idle service gets bounded grace then releases`() {
        val policy = MonitoringIdlePolicy(1_000)
        assertFalse(policy.shouldStop(0, false, parked, noNavigation))
        assertFalse(policy.shouldStop(999, false, parked, noNavigation))
        assertTrue(policy.shouldStop(1_000, false, parked, noNavigation))
    }

    @Test fun `foreground and real driving evidence reset idle deadline`() {
        val policy = MonitoringIdlePolicy(1_000)
        assertFalse(policy.shouldStop(0, false, parked, noNavigation))
        assertFalse(policy.shouldStop(900, true, parked, noNavigation))
        assertFalse(policy.shouldStop(1_000, false, parked, noNavigation))
        assertFalse(policy.shouldStop(1_900, false, parked.copy(gear = available(SelectedGear.Drive)), noNavigation))
        assertFalse(policy.shouldStop(3_000, false, parked, noNavigation))
        assertTrue(policy.shouldStop(4_000, false, parked, noNavigation))
    }

    @Test fun `drive gear keeps traffic stop connected but stale gear does not`() {
        val policy = MonitoringIdlePolicy(1_000)
        val stoppedInDrive = parked.copy(gear = available(SelectedGear.Drive), speedMetersPerSecond = available(0f))
        assertFalse(policy.shouldStop(50_000, false, stoppedInDrive, noNavigation))
        val disconnected = stoppedInDrive.disconnected(SignalConnection.Unavailable)
        assertFalse(policy.shouldStop(51_000, false, disconnected, noNavigation))
        assertTrue(policy.shouldStop(52_000, false, disconnected, noNavigation))
    }

    @Test fun `reverse motion counts without gear permission and tiny noise does not`() {
        val policy = MonitoringIdlePolicy(1_000)
        val denied = parked.copy(gear = VehicleSignal(SignalStatus.PermissionDenied),
            speedMetersPerSecond = available(-1f))
        assertFalse(policy.shouldStop(0, false, denied, noNavigation))
        val noise = denied.copy(speedMetersPerSecond = available(-0.01f))
        assertFalse(policy.shouldStop(1_000, false, noise, noNavigation))
        assertTrue(policy.shouldStop(2_000, false, noise, noNavigation))
    }

    @Test fun `fresh route retains subscription while missing or stale route does not`() {
        val policy = MonitoringIdlePolicy(1_000)
        val route = NavigationSignalSnapshot(connection = NavigationConnection.Connected,
            routeActive = NavigationObservation.Fresh(true, 0))
        assertFalse(policy.shouldStop(0, false, VehicleSignalSnapshot(), route))
        val stale = route.copy(routeActive = NavigationObservation.Stale(0))
        assertFalse(policy.shouldStop(1_000, false, VehicleSignalSnapshot(), stale))
        assertTrue(policy.shouldStop(2_000, false, VehicleSignalSnapshot(), stale))
    }

    @Test fun `confirmed ignition off overrides remaining route and drive gear`() {
        val policy = MonitoringIdlePolicy(1_000)
        val vehicle = parked.copy(ignition = available(IgnitionState.Off), gear = available(SelectedGear.Drive))
        val route = NavigationSignalSnapshot(connection = NavigationConnection.Connected,
            routeActive = NavigationObservation.Fresh(true, 0))
        assertFalse(policy.shouldStop(0, false, vehicle, route))
        assertTrue(policy.shouldStop(1_000, false, vehicle, route))
    }
}
