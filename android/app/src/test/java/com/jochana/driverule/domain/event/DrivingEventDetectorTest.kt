package com.jochana.driverule.domain.event

import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalUnavailable
import com.jochana.driverule.domain.vehicle.NavigationTurn
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.SignalStatus
import com.jochana.driverule.domain.vehicle.VehicleSignal
import org.junit.Assert.*
import org.junit.Test

class DrivingEventDetectorTest {
    private val detector = DrivingEventDetector()

    @Test fun `entry is inclusive at 150 meters and candidate does not claim a crosswalk observation`() {
        val result = eventDetection(distance = 150.0)
        assertEquals(CachedEventTag.RIGHT_TURN_CROSSWALK, result.candidates.single().eventTag)
        assertEquals(1L, result.state.encounterOrdinal)
        assertFalse(result.state.armed)
        assertEquals(result.candidates.single().key, result.activeOccurrenceKey)
        assertEquals(EVENT_NOW + 30_000, result.candidates.single().expiresAtElapsedMs)
        assertEquals(VehicleUxState.Unknown, eventVehicle().uxState)
    }

    @Test fun `invalid and outside distances do not create an approach`() {
        listOf(150.001, 200.0, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { distance ->
            assertTrue("distance=$distance", eventDetection(distance = distance).candidates.isEmpty())
        }
    }

    @Test fun `only first exact right turn is a candidate`() {
        listOf("TURN_LEFT", "TURN_KEEP_R", "TURN_ENTER_IC_R", "TURN_SHARP_R", "TURN_UNKNOWN", "").forEach { type ->
            assertTrue(detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(), eventVehicle(),
                eventNavigation(type = type), EVENT_NOW).candidates.isEmpty())
        }
        val laterRight = eventNavigation().copy(turns = NavigationObservation.Fresh(listOf(
            NavigationTurn("TURN_LEFT", 40.0), NavigationTurn("TURN_RIGHT", 80.0)), EVENT_NOW))
        assertTrue(detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(), eventVehicle(),
            laterRight, EVENT_NOW).candidates.isEmpty())
    }

    @Test fun `fresh active route is required and a failure cannot invent route activity`() {
        val variants = listOf(
            eventNavigation().copy(connection = NavigationConnection.Failed),
            eventNavigation().copy(routeActive = NavigationObservation.Fresh(false, EVENT_NOW)),
            eventNavigation().copy(routeActive = NavigationObservation.Unknown),
            eventNavigation().copy(routeActive = NavigationObservation.Fresh(true, EVENT_NOW - 10_000)),
            eventNavigation().copy(routeActive = NavigationObservation.Fresh(true, EVENT_NOW + 1)),
            eventNavigation().copy(turns = NavigationObservation.Fresh(listOf(NavigationTurn("TURN_RIGHT", 50.0)), EVENT_NOW - 10_000)),
            eventNavigation().copy(turns = NavigationObservation.Stale(EVENT_NOW - 10_000)),
            eventNavigation().copy(turns = NavigationObservation.Fresh(emptyList(), EVENT_NOW)),
        )
        variants.forEach { nav ->
            val result = detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(), eventVehicle(), nav, EVENT_NOW)
            assertTrue(result.candidates.isEmpty())
            assertTrue(result.state.armed)
        }
    }

    @Test fun `all forward gears accept zero without inventing arrival or UX permission`() {
        SelectedGear.entries.forEach { gear ->
            val result = detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(), eventVehicle(gear = gear),
                eventNavigation(), EVENT_NOW)
            if (gear in setOf(SelectedGear.Park, SelectedGear.Neutral, SelectedGear.Reverse)) {
                assertTrue(result.candidates.isEmpty())
            } else {
                assertEquals(0f, result.candidates.single().speedMetersPerSecond, 0f)
            }
        }
    }

    @Test fun `entry remains consumed when vehicle gates block output`() {
        val blocked = detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(),
            eventVehicle().copy(connection = SignalConnection.PermissionDenied), eventNavigation(), EVENT_NOW)
        assertTrue(blocked.candidates.isEmpty())
        assertFalse(blocked.state.armed)
        assertEquals(1L, blocked.state.encounterOrdinal)
        val recovered = detector.evaluate(EVENT_SCOPE, blocked.state, eventVehicle(), eventNavigation(), EVENT_NOW)
        assertTrue(recovered.candidates.isEmpty())
        assertEquals(blocked.state, recovered.state)
    }

    @Test fun `stale future denied and invalid speed cannot be converted to zero`() {
        val speed = eventVehicle().speedMetersPerSecond
        val invalid = listOf(
            speed.copy(status = SignalStatus.PermissionDenied),
            speed.copy(status = SignalStatus.Stale),
            speed.copy(value = null), speed.copy(value = -1f), speed.copy(value = Float.NaN),
            speed.copy(value = Float.POSITIVE_INFINITY),
            speed.copy(observedAtElapsedMs = EVENT_NOW - 5_000),
            speed.copy(observedAtElapsedMs = EVENT_NOW + 1),
            speed.copy(sourceTimestampElapsedMs = EVENT_NOW - 5_000),
            speed.copy(sourceTimestampElapsedMs = EVENT_NOW + 1),
        )
        invalid.forEach { signal ->
            val result = detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(),
                eventVehicle().copy(speedMetersPerSecond = signal), eventNavigation(), EVENT_NOW)
            assertTrue("speed=$signal", result.candidates.isEmpty())
            assertFalse(result.state.armed)
        }
        val deniedGear = eventVehicle().copy(gear = VehicleSignal(status = SignalStatus.PermissionDenied))
        assertTrue(detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(), deniedGear,
            eventNavigation(), EVENT_NOW).candidates.isEmpty())
    }

    @Test fun `callbacks within the hysteresis band and failures never rearm an encounter`() {
        val entered = eventDetection().state
        val variants = listOf(eventNavigation(distance = 150.0), eventNavigation(distance = 180.0),
            eventNavigation(distance = 199.999), eventNavigation().copy(connection = NavigationConnection.Failed),
            eventNavigation().copy(turns = NavigationObservation.Fresh(emptyList(), EVENT_NOW)),
            eventNavigation().copy(turns = NavigationObservation.Unavailable(NavigationSignalUnavailable.Failed)),
            eventNavigation().copy(turns = NavigationObservation.Stale(EVENT_NOW - 10_000)))
        variants.forEach { nav ->
            val result = detector.evaluate(EVENT_SCOPE, entered, eventVehicle(), nav, EVENT_NOW)
            assertTrue(result.candidates.isEmpty())
            assertFalse(result.state.armed)
            assertEquals(entered.activeOccurrenceKey, result.activeOccurrenceKey)
        }
    }

    @Test fun `200 meter observation rearms exactly one subsequent encounter`() {
        val first = eventDetection().state
        val outside = detector.evaluate(EVENT_SCOPE, first, eventVehicle(), eventNavigation(distance = 200.0), EVENT_NOW)
        assertTrue(outside.state.armed)
        assertNull(outside.activeOccurrenceKey)
        val next = detector.evaluate(EVENT_SCOPE, outside.state, eventVehicle(), eventNavigation(), EVENT_NOW)
        assertEquals(2L, next.candidates.single().key.ordinal)
        assertNotEquals(first.activeOccurrenceKey, next.activeOccurrenceKey)
        assertTrue(detector.evaluate(EVENT_SCOPE, next.state, eventVehicle(), eventNavigation(), EVENT_NOW).candidates.isEmpty())
    }

    @Test fun `unknown and new provider types cannot rearm even with an outside distance`() {
        val first = eventDetection().state
        listOf("TURN_UNKNOWN", "UNKNOWN", "", "FUTURE_TURN_TYPE").forEach { type ->
            val result = detector.evaluate(EVENT_SCOPE, first, eventVehicle(),
                eventNavigation(type = type, distance = 300.0), EVENT_NOW)
            assertEquals(first, result.state)
            assertTrue(result.candidates.isEmpty())
        }
    }

    @Test fun `explicit nonright first maneuver rearms but reaching zero alone does not`() {
        val first = eventDetection().state
        val reached = detector.evaluate(EVENT_SCOPE, first, eventVehicle(), eventNavigation(distance = 0.0), EVENT_NOW)
        assertFalse(reached.state.armed)
        assertNull(reached.activeOccurrenceKey)
        assertTrue(detector.evaluate(EVENT_SCOPE, reached.state, eventVehicle(), eventNavigation(), EVENT_NOW).candidates.isEmpty())
        val left = detector.evaluate(EVENT_SCOPE, reached.state, eventVehicle(), eventNavigation(type = "TURN_LEFT"), EVENT_NOW)
        assertTrue(left.state.armed)
        assertEquals(2L, detector.evaluate(EVENT_SCOPE, left.state, eventVehicle(), eventNavigation(), EVENT_NOW)
            .candidates.single().key.ordinal)
    }

    @Test fun `explicit route or session change begins a distinct scope`() {
        val first = eventDetection().state
        listOf(EVENT_SCOPE.copy(routeId = "route-two"), EVENT_SCOPE.copy(sessionId = "session-two")).forEach { scope ->
            val result = detector.evaluate(scope, first, eventVehicle(), eventNavigation(), EVENT_NOW)
            assertEquals(1L, result.candidates.single().key.ordinal)
            assertNotEquals(first.activeOccurrenceKey?.stableId(), result.activeOccurrenceKey?.stableId())
        }
    }

    @Test fun `candidate deadline is capped at arrival margin or thirty seconds`() {
        assertEquals(EVENT_NOW + 9_000, eventDetection(distance = 100.0, speed = 10f).candidates.single().expiresAtElapsedMs)
        assertEquals(EVENT_NOW + 30_000, eventDetection(distance = 150.0, speed = 1f).candidates.single().expiresAtElapsedMs)
        assertTrue(eventDetection(distance = 5.0, speed = 10f).candidates.isEmpty())
        val nearMax = eventDetection(now = Long.MAX_VALUE - 10, distance = 100.0, speed = 0f)
        assertEquals(Long.MAX_VALUE, nearMax.candidates.single().expiresAtElapsedMs)
    }

    @Test fun `ordinal overflow cannot reuse a durable event key`() {
        val result = detector.evaluate(EVENT_SCOPE, RightTurnEncounterState(EVENT_SCOPE, Long.MAX_VALUE),
            eventVehicle(), eventNavigation(), EVENT_NOW)
        assertTrue(result.candidates.isEmpty())
        assertFalse(result.state.armed)
        assertEquals(Long.MAX_VALUE, result.state.encounterOrdinal)
    }
}
