package com.jochana.driverule.domain.vehicle

import com.jochana.driverule.core.model.VehicleUxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleSignalsTest {
    @Test fun `distraction optimization baseline still restricts current setup UI`() {
        assertEquals(VehicleUxState.Restricted, normalizeUxRestriction(true, 0))
        assertEquals(VehicleUxState.Restricted, normalizeUxRestriction(true, 511))
        assertEquals(VehicleUxState.Restricted, normalizeUxRestriction(false, 1))
        assertEquals(VehicleUxState.Unrestricted, normalizeUxRestriction(false, 0))
    }

    @Test fun `park zero speed and brake cannot infer UX permission`() {
        val snapshot = VehicleSignalSnapshot(connection = SignalConnection.Connected,
            gear = VehicleSignal(SignalStatus.Available, SelectedGear.Park, 0),
            speedMetersPerSecond = VehicleSignal(SignalStatus.Available, 0f, 0),
            parkingBrake = VehicleSignal(SignalStatus.Available, true, 0))
        assertEquals(VehicleUxState.Unknown, snapshot.uxState)
    }

    @Test fun `only live connected available UX can unlock input`() {
        SignalConnection.entries.filter { it != SignalConnection.Connected }.forEach { connection ->
            assertEquals(VehicleUxState.Unknown, VehicleSignalSnapshot(connection = connection,
                ux = VehicleSignal(SignalStatus.Available, VehicleUxState.Unrestricted, 0)).uxState)
        }
        SignalStatus.entries.filter { it != SignalStatus.Available }.forEach { status ->
            assertEquals(VehicleUxState.Unknown, VehicleSignalSnapshot(connection = SignalConnection.Connected,
                ux = VehicleSignal(status, VehicleUxState.Unrestricted, 0)).uxState)
        }
    }

    @Test fun `stale retains diagnostic value without exposing it as current`() {
        val signal = VehicleSignal(SignalStatus.Available, false, 1_000).stale()
        assertEquals(false, signal.value)
        assertNull(signal.availableValue)
        assertEquals(1_000L, signal.observedAtElapsedMs)
    }

    @Test fun `delayed continuous sample is expired using its measurement time`() {
        val snapshot = VehicleSignalSnapshot(speedMetersPerSecond =
            VehicleSignal(SignalStatus.Available, 0f, observedAtElapsedMs = 10_000, sourceTimestampElapsedMs = 1_000))
        assertEquals(SignalStatus.Stale, snapshot.expireSpeed(10_000, 5_000).speedMetersPerSecond.status)
    }

    @Test fun `older available callback cannot roll back current gear`() {
        val previous = VehicleSignal(SignalStatus.Available, SelectedGear.Drive, 20_000, 20_000)
        val late = VehicleSignal(SignalStatus.Available, SelectedGear.Park, 21_000, 19_000)
        assertEquals(previous, newerSignal(previous, late))
        val denied = VehicleSignal<SelectedGear>(SignalStatus.PermissionDenied, observedAtElapsedMs = 21_000)
        assertEquals(denied.copy(sourceTimestampWatermarkElapsedMs = 20_000), newerSignal(previous, denied))
    }

    @Test fun `error preserves ordering watermark and delayed park cannot restore availability`() {
        val drive = VehicleSignal(SignalStatus.Available, SelectedGear.Drive, 20_000, 20_000)
        val failed = newerSignal(drive, VehicleSignal<SelectedGear>(SignalStatus.Failed, observedAtElapsedMs = 21_000))
        assertNull(failed.value)
        assertNull(failed.sourceTimestampElapsedMs)
        assertEquals(20_000L, failed.sourceTimestampWatermarkElapsedMs)
        val latePark = VehicleSignal(SignalStatus.Available, SelectedGear.Park, 22_000, 19_000)
        assertEquals(failed, newerSignal(failed, latePark))
        val currentPark = VehicleSignal(SignalStatus.Available, SelectedGear.Park, 23_000, 23_000)
        assertEquals(currentPark, newerSignal(failed, currentPark))
    }

    @Test fun `invalidated value advances valid timestamp watermark without retaining payload`() {
        val drive = VehicleSignal(SignalStatus.Available, SelectedGear.Drive, 20_000, 20_000)
        val unknown = newerSignal(drive, VehicleSignal<SelectedGear>(SignalStatus.Unknown,
            observedAtElapsedMs = 21_000, sourceTimestampElapsedMs = 21_000))
        val noTimestampError = newerSignal(unknown, VehicleSignal<SelectedGear>(SignalStatus.Unavailable))
        assertEquals(21_000L, noTimestampError.sourceTimestampWatermarkElapsedMs)
        assertNull(noTimestampError.value)
        val late = VehicleSignal(SignalStatus.Available, SelectedGear.Park, 22_000, 20_500)
        assertEquals(noTimestampError, newerSignal(noTimestampError, late))
    }

    @Test fun `permission revocation removes value but preserves monotonic comparison`() {
        val drive = VehicleSignal(SignalStatus.Available, SelectedGear.Drive, 20_000, 20_000)
        val denied = VehicleSignalSnapshot(gear = drive)
            .withPermissions(speedGranted = true, powertrainGranted = false, nowElapsedMs = 21_000).gear
        assertNull(denied.value)
        assertNull(denied.availableValue)
        assertNull(denied.sourceTimestampElapsedMs)
        assertEquals(20_000L, denied.sourceTimestampWatermarkElapsedMs)
        val late = VehicleSignal(SignalStatus.Available, SelectedGear.Park, 22_000, 19_000)
        assertEquals(denied, newerSignal(denied, late))
        val fresh = VehicleSignal(SignalStatus.Available, SelectedGear.Neutral, 23_000, 23_000)
        assertEquals(fresh, newerSignal(denied, fresh))
    }

    @Test fun `permission revocation invalidates retained on-change values without changing UX`() {
        val snapshot = VehicleSignalSnapshot(connection = SignalConnection.Connected,
            ux = VehicleSignal(SignalStatus.Available, VehicleUxState.Unrestricted, 1_000),
            gear = VehicleSignal(SignalStatus.Available, SelectedGear.Park, 1_000),
            ignition = VehicleSignal(SignalStatus.Available, IgnitionState.On, 1_000),
            parkingBrake = VehicleSignal(SignalStatus.Available, true, 1_000),
            speedMetersPerSecond = VehicleSignal(SignalStatus.Available, 0f, 1_000))
        val powertrainDenied = snapshot.withPermissions(speedGranted = true, powertrainGranted = false, 2_000)
        assertEquals(SignalStatus.Available, powertrainDenied.speedMetersPerSecond.status)
        assertEquals(SignalStatus.PermissionDenied, powertrainDenied.gear.status)
        assertEquals(SignalStatus.PermissionDenied, powertrainDenied.ignition.status)
        assertEquals(SignalStatus.PermissionDenied, powertrainDenied.parkingBrake.status)
        assertNull(powertrainDenied.gear.value)
        assertEquals(VehicleUxState.Unrestricted, powertrainDenied.uxState)
        val allDenied = powertrainDenied.withPermissions(speedGranted = false, powertrainGranted = false, 3_000)
        assertEquals(SignalStatus.PermissionDenied, allDenied.speedMetersPerSecond.status)
        assertNull(allDenied.speedMetersPerSecond.value)
        assertEquals(allDenied, allDenied.withPermissions(speedGranted = false, powertrainGranted = false, 4_000))
    }

    @Test fun `permission grant alone cannot fabricate a previously denied value`() {
        val denied = VehicleSignalSnapshot(gear = VehicleSignal(status = SignalStatus.PermissionDenied))
        assertEquals(denied, denied.withPermissions(speedGranted = true, powertrainGranted = true, 5_000))
    }
}
