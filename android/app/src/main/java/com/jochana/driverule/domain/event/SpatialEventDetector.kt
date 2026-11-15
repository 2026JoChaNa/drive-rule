package com.jochana.driverule.domain.event

import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationDestination
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot

internal fun evaluateSpatialEvents(
    config: EventPolicyConfig,
    scope: EventScope,
    state: DrivingEncounterState,
    rightTurn: EventDetection,
    vehicle: VehicleSignalSnapshot,
    navigation: NavigationSignalSnapshot,
    now: Long,
    spatial: EventSpatialInputs,
): DrivingEventDetection {
    val school = evaluateSchool(config, scope, state.school, vehicle, now, spatial)
    val destination = evaluateDestination(config, scope, state.destination, vehicle, navigation, now, spatial)
    return DrivingEventDetection(state.copy(rightTurn = rightTurn.state, school = school.first,
        destination = destination.first), rightTurn.candidates + listOfNotNull(school.second, destination.second))
}

private fun evaluateSchool(
    config: EventPolicyConfig,
    scope: EventScope,
    previous: SpatialEncounterState,
    vehicle: VehicleSignalSnapshot,
    now: Long,
    spatial: EventSpatialInputs,
): Pair<SpatialEncounterState, DrivingEventCandidate?> {
    val cache = spatialCache(spatial) ?: return previous to null
    var state = if (cache.sha256 == previous.cacheSha256) previous else
        SpatialEncounterState(cacheSha256 = cache.sha256, encounterOrdinal = previous.encounterOrdinal)
    val location = freshLocation(spatial, now, config) ?: return state to null
    if (state.locationSourceRevision != location.sourceRevision) {
        state = SpatialEncounterState(cacheSha256 = cache.sha256, locationSourceRevision = location.sourceRevision,
            encounterOrdinal = state.encounterOrdinal)
    }
    // A stale callback must not rearm a consumed encounter after the adapter has invalidated it.
    if (state.lastLocationObservedElapsedMs?.let { location.observedElapsedMs <= it } == true ||
        state.lastLocationSourceTimeToken?.let { location.sourceTimeToken == null || location.sourceTimeToken <= it } == true)
        return state to null
    state = state.copy(lastLocationObservedElapsedMs = location.observedElapsedMs,
        lastLocationSourceTimeToken = location.sourceTimeToken ?: state.lastLocationSourceTimeToken)
    val nearest = nearestSchool(EventGeoPoint(location.latitude, location.longitude), cache) ?: return state to null
    val distance = nearest.distanceMeters
    if (distance >= config.schoolRearmDistanceMeters) return state.copy(armed = true, activeOccurrenceKey = null) to null
    if (distance > config.schoolEntryDistanceMeters || !state.armed) return state to null
    if (state.encounterOrdinal == Long.MAX_VALUE) return state.copy(armed = false) to null
    val key = EventOccurrenceKey(scope.sessionId, scope.routeId, CachedEventTag.SCHOOL_ZONE_ENTRY, state.encounterOrdinal + 1)
    state = state.copy(armed = false, encounterOrdinal = key.ordinal, activeOccurrenceKey = key)
    val speed = freshForwardSpeed(vehicle, now, config) ?: return state to null
    // This is only distance to a facility point. It does not provide a legal boundary or ETA.
    val proof = EventCandidateProof.SchoolProximity(cache.sha256, nearest.school, location.sourceRevision,
        location.observedElapsedMs, location.sourceTimeToken, nearest.index)
    return state to DrivingEventCandidate(key, now, addBounded(now, config.candidateLifetimeMs), distance, speed, proof)
}

private fun evaluateDestination(
    config: EventPolicyConfig,
    scope: EventScope,
    previous: SpatialEncounterState,
    vehicle: VehicleSignalSnapshot,
    navigation: NavigationSignalSnapshot,
    now: Long,
    spatial: EventSpatialInputs,
): Pair<SpatialEncounterState, DrivingEventCandidate?> {
    val cache = spatialCache(spatial) ?: return previous to null
    var state = if (cache.sha256 == previous.cacheSha256) previous else
        SpatialEncounterState(cacheSha256 = cache.sha256, encounterOrdinal = previous.encounterOrdinal)
    val destination = freshDestination(navigation, now, config) ?: return state to null
    val point = EventGeoPoint(destination.latitude, destination.longitude)
    if (point != state.destination) state = SpatialEncounterState(cacheSha256 = cache.sha256,
        destination = point, encounterOrdinal = state.encounterOrdinal)
    // Destination coordinates are on-change. Reuse their nearest school through owner ticks;
    // changing the hash/point above resets this lookup, and the index also detects removal.
    val nearest = state.destinationSchoolMatch?.takeIf { cache.zones.getOrNull(it.index) == it.school }
        ?: nearestSchool(point, cache) ?: return state to null
    state = state.copy(destinationSchoolMatch = nearest)
    if (nearest.distanceMeters > config.destinationSchoolDistanceMeters) return state.copy(activeOccurrenceKey = null) to null
    val distance = destination.remainingDistanceMeters
    if (distance >= config.destinationRearmDistanceMeters) return state.copy(armed = true, activeOccurrenceKey = null) to null
    if (distance > config.destinationEntryDistanceMeters || !state.armed) return state to null
    if (state.encounterOrdinal == Long.MAX_VALUE) return state.copy(armed = false) to null
    val key = EventOccurrenceKey(scope.sessionId, scope.routeId, CachedEventTag.PARKING_DESTINATION, state.encounterOrdinal + 1)
    state = state.copy(armed = false, encounterOrdinal = key.ordinal, activeOccurrenceKey = key)
    if (distance <= 0) return state.copy(activeOccurrenceKey = null) to null
    val speed = freshForwardSpeed(vehicle, now, config) ?: return state to null
    val eta = arrivalDurationMs(distance, speed)
    val deadline = minOf(addBounded(now, config.candidateLifetimeMs),
        eta?.let { addBounded(now, (it - config.speechEndMarginMs).coerceAtLeast(0)) } ?: Long.MAX_VALUE)
    if (deadline <= now) return state to null
    return state to DrivingEventCandidate(key, now, deadline, distance, speed,
        EventCandidateProof.DestinationProximity(cache.sha256, nearest.school, point, nearest.index))
}

internal fun freshDestination(navigation: NavigationSignalSnapshot, now: Long, config: EventPolicyConfig): NavigationDestination? {
    if (navigation.connection != NavigationConnection.Connected) return null
    val route = navigation.routeActive as? NavigationObservation.Fresh ?: return null
    if (!route.value || !isFresh(route.observedElapsedMs, now, config.navigationMaxAgeMs)) return null
    val destination = navigation.destination as? NavigationObservation.Fresh ?: return null
    val value = destination.value
    return value.takeIf { isFresh(destination.observedElapsedMs, now, config.navigationMaxAgeMs) &&
        validCoordinates(value.latitude, value.longitude) && value.remainingDistanceMeters.isFinite() && value.remainingDistanceMeters >= 0 }
}
