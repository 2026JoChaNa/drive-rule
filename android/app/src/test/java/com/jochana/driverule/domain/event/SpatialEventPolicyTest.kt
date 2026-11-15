package com.jochana.driverule.domain.event

import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.core.model.SchoolZonePoint
import com.jochana.driverule.core.model.SchoolZoneSnapshot
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationDestination
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import org.junit.Assert.*
import org.junit.Test

class SpatialEventPolicyTest {
    private val detector = DrivingEventDetector()
    private val policy = GuidancePlanPolicy()
    private val school = SchoolZonePoint(0.0, 0.0)
    private val zones = SchoolZoneSnapshot("fixture", "hash-a", listOf(school))

    private fun north(meters: Double) = EventGeoPoint(Math.toDegrees(meters / 6_371_008.8), 0.0)
    private fun location(meters: Double, now: Long = EVENT_NOW, revision: Long = 1): EventLocationObservation {
        val point = north(meters)
        return EventLocationObservation(true, point.latitude, point.longitude, now, now, revision)
    }

    private fun navigation(now: Long = EVENT_NOW, remaining: Double? = null, point: EventGeoPoint = EventGeoPoint(0.0, 0.0)) =
        eventNavigation(now, type = "TURN_LEFT").copy(destination = remaining?.let {
            NavigationObservation.Fresh(NavigationDestination(point.latitude, point.longitude, it), now)
        } ?: NavigationObservation.Unknown)

    private fun evaluate(
        previous: DrivingEncounterState = DrivingEncounterState(),
        fix: EventLocationObservation? = null,
        geo: SchoolZoneSnapshot? = zones,
        nav: NavigationSignalSnapshot = navigation(),
        vehicle: VehicleSignalSnapshot = eventVehicle(),
        now: Long = EVENT_NOW,
        scope: EventScope = EVENT_SCOPE,
    ) = detector.evaluate(scope, previous, vehicle, nav, now, EventSpatialInputs(fix, geo))

    private fun schoolEntry(speed: Float = 0f): DrivingEventDetection {
        val outside = evaluate(fix = location(500.0), vehicle = eventVehicle(speed = speed))
        return evaluate(outside.state, location(300.0, EVENT_NOW + 1), vehicle = eventVehicle(EVENT_NOW + 1, speed), now = EVENT_NOW + 1)
    }

    private fun destinationEntry(speed: Float = 0f): DrivingEventDetection {
        val outside = evaluate(nav = navigation(remaining = 600.0), vehicle = eventVehicle(speed = speed))
        return evaluate(outside.state, nav = navigation(EVENT_NOW + 1, 500.0),
            vehicle = eventVehicle(EVENT_NOW + 1, speed), now = EVENT_NOW + 1)
    }

    private fun plan(detection: DrivingEventDetection): GuidancePlan {
        val candidate = detection.candidates.single()
        return policy.select(detection.candidates, eventCache(eventCard().copy(eventTag = candidate.eventTag)),
            1, EVENT_DAY, EVENT_NOW + 1, EVENT_EPOCH)!!
    }

    private fun valid(
        plan: GuidancePlan,
        detection: DrivingEventDetection,
        spatial: EventSpatialInputs = EventSpatialInputs(location(300.0, EVENT_NOW + 1), zones),
        nav: NavigationSignalSnapshot = navigation(EVENT_NOW + 1, 500.0),
        now: Long = EVENT_NOW + 1,
        scope: EventScope = EVENT_SCOPE,
        phase: PlanValidationPhase = PlanValidationPhase.BeforeStart,
    ) = policy.isValid(plan, scope, detection.state, eventVehicle(now), nav, eventCache(plan.card),
        1, EVENT_DAY, now, EVENT_EPOCH, phase, spatial)

    @Test fun `haversine is symmetric handles date line and rejects invalid coordinates`() {
        val origin = EventGeoPoint(0.0, 0.0)
        assertEquals(0.0, geographicDistanceMeters(origin, origin)!!, 0.00001)
        assertEquals(1_000.0, geographicDistanceMeters(origin, north(1_000.0))!!, 0.00001)
        assertEquals(geographicDistanceMeters(origin, north(1_000.0))!!,
            geographicDistanceMeters(north(1_000.0), origin)!!, 0.00001)
        assertTrue(geographicDistanceMeters(EventGeoPoint(0.0, 179.999), EventGeoPoint(0.0, -179.999))!! < 223)
        listOf(EventGeoPoint(Double.NaN, 0.0), EventGeoPoint(91.0, 0.0), EventGeoPoint(0.0, -181.0)).forEach {
            assertNull(geographicDistanceMeters(origin, it))
        }
    }

    @Test fun `first location inside school band is consumed until an observed outside fix`() {
        val inside = evaluate(fix = location(100.0))
        assertTrue(inside.candidates.isEmpty())
        assertFalse(inside.state.school.armed)
        val repeated = evaluate(inside.state, location(50.0, EVENT_NOW + 1), now = EVENT_NOW + 1)
        assertTrue(repeated.candidates.isEmpty())
        val outside = evaluate(repeated.state, location(500.0, EVENT_NOW + 2), now = EVENT_NOW + 2)
        val entry = evaluate(outside.state, location(300.0, EVENT_NOW + 3), now = EVENT_NOW + 3)
        assertEquals(CachedEventTag.SCHOOL_ZONE_ENTRY, entry.candidates.single().eventTag)
    }

    @Test fun `school entry can work without an active navigation route`() {
        val freeDrive = NavigationSignalSnapshot(connection = NavigationConnection.Connected)
        val outside = evaluate(fix = location(500.0), nav = freeDrive)
        val entry = evaluate(outside.state, location(300.0, EVENT_NOW + 1), nav = freeDrive, now = EVENT_NOW + 1)
        assertEquals(CachedEventTag.SCHOOL_ZONE_ENTRY, entry.candidates.single().eventTag)
    }

    @Test fun `school hysteresis only rearms after exit and never from lost location`() {
        val entry = schoolEntry()
        val band = evaluate(entry.state, location(400.0, EVENT_NOW + 2), now = EVENT_NOW + 2)
        assertFalse(band.state.school.armed)
        val lost = evaluate(band.state, null, now = EVENT_NOW + 3)
        assertFalse(lost.state.school.armed)
        assertTrue(evaluate(lost.state, location(300.0, EVENT_NOW + 4), now = EVENT_NOW + 4).candidates.isEmpty())
        val outside = evaluate(lost.state, location(500.0, EVENT_NOW + 5), now = EVENT_NOW + 5)
        assertNull(outside.state.school.activeOccurrenceKey)
        assertEquals(2L, evaluate(outside.state, location(300.0, EVENT_NOW + 6), now = EVENT_NOW + 6)
            .candidates.single().key.ordinal)
    }

    @Test fun `school boundaries are inclusive on entry and exit`() {
        val entryPoint = north(370.0)
        val exitPoint = north(470.0)
        val entryDistance = geographicDistanceMeters(EventGeoPoint(0.0, 0.0), entryPoint)!!
        val exitDistance = geographicDistanceMeters(EventGeoPoint(0.0, 0.0), exitPoint)!!
        val exact = DrivingEventDetector(EventPolicyConfig(schoolEntryDistanceMeters = entryDistance,
            schoolRearmDistanceMeters = exitDistance))
        val outside = exact.evaluate(EVENT_SCOPE, DrivingEncounterState(), eventVehicle(), navigation(), EVENT_NOW,
            EventSpatialInputs(location(470.0), zones))
        assertTrue(outside.state.school.armed)
        val entry = exact.evaluate(EVENT_SCOPE, outside.state, eventVehicle(), navigation(), EVENT_NOW + 1,
            EventSpatialInputs(location(370.0, EVENT_NOW + 1), zones))
        assertEquals(CachedEventTag.SCHOOL_ZONE_ENTRY, entry.candidates.single().eventTag)
    }

    @Test fun `stale future invalid and disconnected fixes cannot arm school detection`() {
        val invalid = listOf(location(500.0).copy(observedElapsedMs = EVENT_NOW - 5_000),
            location(500.0).copy(observedElapsedMs = EVENT_NOW + 1), location(500.0).copy(connected = false),
            location(500.0).copy(latitude = 91.0), location(500.0).copy(longitude = Double.NaN))
        invalid.forEach { fix ->
            val outside = evaluate(fix = fix)
            assertFalse(outside.state.school.armed)
            assertTrue(evaluate(outside.state, location(300.0, EVENT_NOW + 1), now = EVENT_NOW + 1).candidates.isEmpty())
        }
    }

    @Test fun `duplicate and reversed source timestamps cannot rearm a consumed school encounter`() {
        val entry = schoolEntry()
        listOf(EVENT_NOW + 1, EVENT_NOW).forEach { sourceToken ->
            val outside = evaluate(entry.state, location(500.0, EVENT_NOW + 2).copy(sourceTimeToken = sourceToken), now = EVENT_NOW + 2)
            assertFalse(outside.state.school.armed)
            assertTrue(evaluate(outside.state, location(300.0, EVENT_NOW + 3), now = EVENT_NOW + 3).candidates.isEmpty())
        }
    }

    @Test fun `location source restart and new geo hash require a new outside observation`() {
        val outside = evaluate(fix = location(500.0))
        assertTrue(evaluate(outside.state, location(300.0, EVENT_NOW + 1, revision = 2), now = EVENT_NOW + 1).candidates.isEmpty())
        val changed = evaluate(outside.state, location(300.0, EVENT_NOW + 1), geo = zones.copy(sha256 = "hash-b"), now = EVENT_NOW + 1)
        assertTrue(changed.candidates.isEmpty())
        assertFalse(changed.state.school.armed)
    }

    @Test fun `blocked vehicle consumes school entry without replay on recovery`() {
        val outside = evaluate(fix = location(500.0))
        val blocked = evaluate(outside.state, location(300.0, EVENT_NOW + 1),
            vehicle = eventVehicle().copy(connection = SignalConnection.PermissionDenied), now = EVENT_NOW + 1)
        assertTrue(blocked.candidates.isEmpty())
        assertFalse(blocked.state.school.armed)
        assertTrue(evaluate(blocked.state, location(200.0, EVENT_NOW + 2), now = EVENT_NOW + 2).candidates.isEmpty())
    }

    @Test fun `overlapping schools do not rearm simply because the nearest point changes`() {
        val second = north(100.0).let { SchoolZonePoint(it.latitude, it.longitude) }
        val multiple = zones.copy(zones = listOf(school, second))
        val outside = evaluate(fix = location(700.0), geo = multiple)
        val first = evaluate(outside.state, location(400.0, EVENT_NOW + 1), geo = multiple, now = EVENT_NOW + 1)
        assertEquals(second, (first.candidates.single().proof as EventCandidateProof.SchoolProximity).school)
        assertTrue(evaluate(first.state, location(0.0, EVENT_NOW + 2), geo = multiple, now = EVENT_NOW + 2).candidates.isEmpty())
    }

    @Test fun `school speech uses a fixed TTL and never facility point divided by speed`() {
        val entry = schoolEntry(speed = 30f)
        val selected = plan(entry)
        assertNull(selected.estimatedArrivalElapsedMs)
        assertEquals(EVENT_NOW + 1 + 30_000, selected.expiresAtElapsedMs)
        assertTrue(valid(selected, entry))
    }

    @Test fun `first destination inside is consumed and five hundred entry follows six hundred outside`() {
        val inside = evaluate(nav = navigation(remaining = 500.0))
        assertTrue(inside.candidates.isEmpty())
        assertFalse(inside.state.destination.armed)
        val middle = evaluate(inside.state, nav = navigation(EVENT_NOW + 1, 550.0), now = EVENT_NOW + 1)
        assertFalse(middle.state.destination.armed)
        val outside = evaluate(middle.state, nav = navigation(EVENT_NOW + 2, 600.0), now = EVENT_NOW + 2)
        assertTrue(outside.state.destination.armed)
        val entry = evaluate(outside.state, nav = navigation(EVENT_NOW + 3, 500.0), now = EVENT_NOW + 3)
        assertEquals(CachedEventTag.PARKING_DESTINATION, entry.candidates.single().eventTag)
    }

    @Test fun `destination must be near a school and zero arrival is not advance warning`() {
        val far = north(301.0)
        val outside = evaluate(nav = navigation(remaining = 600.0, point = far))
        assertFalse(outside.state.destination.armed)
        assertTrue(evaluate(outside.state, nav = navigation(EVENT_NOW + 1, 500.0, far), now = EVENT_NOW + 1).candidates.isEmpty())
        val eligible = evaluate(nav = navigation(remaining = 600.0))
        val arrived = evaluate(eligible.state, nav = navigation(EVENT_NOW + 1, 0.0), now = EVENT_NOW + 1)
        assertTrue(arrived.candidates.isEmpty())
        assertFalse(arrived.state.destination.armed)
        assertNull(arrived.state.destination.activeOccurrenceKey)
    }

    @Test fun `destination school radius is inclusive at its exact distance`() {
        val destinationPoint = north(300.0)
        val radius = geographicDistanceMeters(EventGeoPoint(0.0, 0.0), destinationPoint)!!
        val exact = DrivingEventDetector(EventPolicyConfig(destinationSchoolDistanceMeters = radius))
        val outside = exact.evaluate(EVENT_SCOPE, DrivingEncounterState(), eventVehicle(),
            navigation(remaining = 600.0, point = destinationPoint), EVENT_NOW, EventSpatialInputs(schoolZones = zones))
        val entry = exact.evaluate(EVENT_SCOPE, outside.state, eventVehicle(),
            navigation(EVENT_NOW + 1, 500.0, destinationPoint), EVENT_NOW + 1, EventSpatialInputs(schoolZones = zones))
        assertEquals(CachedEventTag.PARKING_DESTINATION, entry.candidates.single().eventTag)
    }

    @Test fun `late initial school cache load cannot convert an inside fix into an entry`() {
        val noCache = evaluate(fix = location(500.0), geo = null)
        assertFalse(noCache.state.school.armed)
        val loadedInside = evaluate(noCache.state, location(300.0, EVENT_NOW + 1), now = EVENT_NOW + 1)
        assertTrue(loadedInside.candidates.isEmpty())
        val badPoints = zones.copy(zones = listOf(SchoolZonePoint(Double.NaN, 0.0), SchoolZonePoint(91.0, 0.0)))
        assertTrue(evaluate(fix = location(300.0), geo = badPoints).candidates.isEmpty())
    }

    @Test fun `geo replacement cannot recycle a previous same-scope occurrence key`() {
        val original = schoolEntry()
        val changedZones = zones.copy(sha256 = "hash-b")
        val outside = evaluate(original.state, location(500.0, EVENT_NOW + 2), geo = changedZones, now = EVENT_NOW + 2)
        val next = evaluate(outside.state, location(300.0, EVENT_NOW + 3), geo = changedZones, now = EVENT_NOW + 3)
        assertEquals(2L, next.candidates.single().key.ordinal)
        assertNotEquals(original.candidates.single().key, next.candidates.single().key)
    }

    @Test fun `missing stale inactive invalid destination or invalid cache cannot arm`() {
        val invalid = listOf(navigation(), navigation(remaining = -1.0), navigation(remaining = Double.NaN),
            navigation(remaining = 600.0, point = EventGeoPoint(91.0, 0.0)),
            navigation(now = EVENT_NOW - 10_000, remaining = 600.0),
            navigation(remaining = 600.0).copy(routeActive = NavigationObservation.Fresh(false, EVENT_NOW)),
            navigation(remaining = 600.0).copy(connection = NavigationConnection.Failed))
        invalid.forEach { nav -> assertFalse(evaluate(nav = nav).state.destination.armed) }
        assertFalse(evaluate(nav = navigation(remaining = 600.0), geo = null).state.destination.armed)
        assertFalse(evaluate(nav = navigation(remaining = 600.0), geo = zones.copy(zones = emptyList())).state.destination.armed)
    }

    @Test fun `destination coordinate route and hash changes consume a new inside baseline`() {
        val outside = evaluate(nav = navigation(remaining = 600.0))
        assertTrue(evaluate(outside.state, nav = navigation(EVENT_NOW + 1, 500.0, north(1.0)), now = EVENT_NOW + 1).candidates.isEmpty())
        assertTrue(evaluate(outside.state, nav = navigation(EVENT_NOW + 1, 500.0), now = EVENT_NOW + 1,
            scope = EVENT_SCOPE.copy(routeId = "new-route")).candidates.isEmpty())
        assertTrue(evaluate(outside.state, nav = navigation(EVENT_NOW + 1, 500.0), now = EVENT_NOW + 1,
            geo = zones.copy(sha256 = "new-hash")).candidates.isEmpty())
    }

    @Test fun `destination vehicle denial consumes an entry and keeps highway unsupported`() {
        val outside = evaluate(nav = navigation(remaining = 600.0))
        val blocked = evaluate(outside.state, nav = navigation(EVENT_NOW + 1, 500.0),
            vehicle = eventVehicle(gear = SelectedGear.Park), now = EVENT_NOW + 1)
        assertTrue(blocked.candidates.isEmpty())
        assertFalse(blocked.state.destination.armed)
        assertTrue(evaluate(blocked.state, nav = navigation(EVENT_NOW + 2, 400.0), now = EVENT_NOW + 2).candidates.isEmpty())
        assertTrue(destinationEntry().candidates.none { it.eventTag == CachedEventTag.PARKING_HIGHWAY })
    }

    @Test fun `destination uses remaining route distance ETA and reviewed text must fit`() {
        val entry = destinationEntry(speed = 25f)
        val selected = plan(entry)
        assertEquals(EVENT_NOW + 1 + 20_000, selected.estimatedArrivalElapsedMs)
        assertEquals(EVENT_NOW + 1 + 19_000, selected.expiresAtElapsedMs)
        assertNull(policy.select(entry.candidates, eventCache(eventCard(tts = "가".repeat(115))
            .copy(eventTag = CachedEventTag.PARKING_DESTINATION)), 1, EVENT_DAY, EVENT_NOW + 1, EVENT_EPOCH))
    }

    @Test fun `school plan is invalidated by changed hash removed point source restart and stale fix`() {
        val entry = schoolEntry()
        val selected = plan(entry)
        assertTrue(valid(selected, entry))
        assertFalse(valid(selected, entry, EventSpatialInputs(location(300.0, EVENT_NOW + 1), zones.copy(sha256 = "new"))))
        assertFalse(valid(selected, entry, EventSpatialInputs(location(300.0, EVENT_NOW + 1), zones.copy(zones = emptyList()))))
        assertFalse(valid(selected, entry, EventSpatialInputs(location(300.0, EVENT_NOW + 1, revision = 2), zones)))
        assertFalse(valid(selected, entry, now = EVENT_NOW + 5_001))
        assertFalse(valid(selected, entry, EventSpatialInputs(location(300.0, EVENT_NOW + 1).copy(latitude = Double.NaN), zones)))
        assertFalse(valid(selected, entry, scope = EVENT_SCOPE.copy(routeId = "changed")))
    }

    @Test fun `school in progress retains only the hysteresis band and original TTL`() {
        val entry = schoolEntry()
        val selected = plan(entry)
        val band = EventSpatialInputs(location(400.0, EVENT_NOW + 2), zones)
        assertFalse(valid(selected, entry, band, now = EVENT_NOW + 2))
        assertTrue(valid(selected, entry, band, now = EVENT_NOW + 2, phase = PlanValidationPhase.InProgress))
        assertFalse(valid(selected, entry, EventSpatialInputs(location(500.0, EVENT_NOW + 2), zones),
            now = EVENT_NOW + 2, phase = PlanValidationPhase.InProgress))
        assertFalse(valid(selected, entry, EventSpatialInputs(location(300.0, selected.expiresAtElapsedMs), zones),
            now = selected.expiresAtElapsedMs, phase = PlanValidationPhase.InProgress))
    }

    @Test fun `destination plan is invalidated by changed point cache route inactive and rearm distance`() {
        val entry = destinationEntry()
        val selected = plan(entry)
        assertTrue(valid(selected, entry))
        assertFalse(valid(selected, entry, nav = navigation(EVENT_NOW + 1, 500.0, north(1.0))))
        assertFalse(valid(selected, entry, EventSpatialInputs(schoolZones = zones.copy(sha256 = "new"))))
        assertFalse(valid(selected, entry, nav = navigation(EVENT_NOW + 1, 600.0), phase = PlanValidationPhase.InProgress))
        assertFalse(valid(selected, entry, nav = navigation(EVENT_NOW + 1, 500.0)
            .copy(routeActive = NavigationObservation.Fresh(false, EVENT_NOW + 1))))
        assertFalse(valid(selected, entry, scope = EVENT_SCOPE.copy(routeId = "changed")))
    }

    @Test fun `simultaneous candidates choose right turn then school then destination`() {
        val right = eventDetection(EVENT_NOW + 1).candidates.single()
        val schoolCandidate = schoolEntry().candidates.single()
        val destination = destinationEntry().candidates.single()
        val cards = eventCache(eventCard(3).copy(eventTag = right.eventTag), eventCard(2).copy(eventTag = schoolCandidate.eventTag),
            eventCard(1).copy(eventTag = destination.eventTag))
        assertEquals(right.eventTag, policy.select(listOf(destination, schoolCandidate, right), cards, 1, EVENT_DAY,
            EVENT_NOW + 1, EVENT_EPOCH)!!.candidate.eventTag)
        assertEquals(schoolCandidate.eventTag, policy.select(listOf(destination, schoolCandidate), cards, 1, EVENT_DAY,
            EVENT_NOW + 1, EVENT_EPOCH)!!.candidate.eventTag)
    }

    @Test fun `unchanged destination ticks and captured membership do not scan the entire zone list`() {
        val backing = listOf(school) + (1..1_000).map { SchoolZonePoint(1.0 + it / 100_000.0, 0.0) }
        val counted = CountingPoints(backing)
        val cached = zones.copy(zones = counted)
        val outside = evaluate(nav = navigation(remaining = 600.0), geo = cached)
        assertTrue(counted.reads >= backing.size)
        counted.reads = 0
        val tick = evaluate(outside.state, nav = navigation(EVENT_NOW + 1, 550.0), geo = cached, now = EVENT_NOW + 1)
        assertTrue("unchanged destination uses a cached match", counted.reads <= 2)
        counted.reads = 0
        val entry = evaluate(tick.state, nav = navigation(EVENT_NOW + 2, 500.0), geo = cached, now = EVENT_NOW + 2)
        assertTrue(counted.reads <= 2)
        val selected = policy.select(entry.candidates, eventCache(eventCard().copy(eventTag = CachedEventTag.PARKING_DESTINATION)),
            1, EVENT_DAY, EVENT_NOW + 2, EVENT_EPOCH)!!
        counted.reads = 0
        assertTrue(valid(selected, entry, EventSpatialInputs(schoolZones = cached),
            nav = navigation(EVENT_NOW + 2, 500.0), now = EVENT_NOW + 2))
        assertTrue("plan membership validates its captured index", counted.reads <= 2)
    }

    private class CountingPoints(private val points: List<SchoolZonePoint>) : AbstractList<SchoolZonePoint>() {
        var reads = 0
        override val size: Int get() = points.size
        override fun get(index: Int): SchoolZonePoint { reads++; return points[index] }
    }
}
