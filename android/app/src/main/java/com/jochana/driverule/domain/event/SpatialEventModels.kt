package com.jochana.driverule.domain.event

import com.jochana.driverule.core.model.SchoolZonePoint
import com.jochana.driverule.core.model.SchoolZoneSnapshot
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Ephemeral fix only. The source token orders fixes; it is not assumed to be elapsed time. */
data class EventLocationObservation(
    val connected: Boolean,
    val latitude: Double,
    val longitude: Double,
    val observedElapsedMs: Long,
    val sourceTimeToken: Long? = null,
    val sourceRevision: Long = 0,
)

data class EventSpatialInputs(
    val location: EventLocationObservation? = null,
    val schoolZones: SchoolZoneSnapshot? = null,
)

data class EventGeoPoint(val latitude: Double, val longitude: Double)

/** Captured evidence remains local and is never a declaration of a legal zone boundary. */
sealed interface EventCandidateProof {
    data object RightTurn : EventCandidateProof
    data class SchoolProximity(
        val cacheSha256: String,
        val school: SchoolZonePoint,
        val locationSourceRevision: Long,
        val locationObservedElapsedMs: Long,
        val locationSourceTimeToken: Long?,
        val schoolIndex: Int = -1,
    ) : EventCandidateProof
    data class DestinationProximity(
        val cacheSha256: String,
        val school: SchoolZonePoint,
        val destination: EventGeoPoint,
        val schoolIndex: Int = -1,
    ) : EventCandidateProof
}

/** No known outside observation means unarmed, including the first fix after source restart. */
data class SpatialEncounterState(
    val cacheSha256: String? = null,
    val destination: EventGeoPoint? = null,
    val locationSourceRevision: Long? = null,
    val lastLocationObservedElapsedMs: Long? = null,
    val lastLocationSourceTimeToken: Long? = null,
    val armed: Boolean = false,
    val encounterOrdinal: Long = 0,
    val activeOccurrenceKey: EventOccurrenceKey? = null,
    val destinationSchoolMatch: SchoolPointDistance? = null,
)

/** Snapshot-local index allows constant-time membership validation of immutable cache copies. */
data class SchoolPointDistance(val school: SchoolZonePoint, val index: Int, val distanceMeters: Double)

data class DrivingEncounterState(
    val scope: EventScope? = null,
    val rightTurn: RightTurnEncounterState = RightTurnEncounterState(),
    val school: SpatialEncounterState = SpatialEncounterState(),
    val destination: SpatialEncounterState = SpatialEncounterState(),
)

data class DrivingEventDetection(val state: DrivingEncounterState, val candidates: List<DrivingEventCandidate>)

internal fun validCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

/** Great-circle distance on WGS84's mean-radius sphere; not road distance or a zone polygon. */
fun geographicDistanceMeters(first: EventGeoPoint, second: EventGeoPoint): Double? {
    if (!validCoordinates(first.latitude, first.longitude) || !validCoordinates(second.latitude, second.longitude)) return null
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(Math.toRadians(first.latitude)) * cos(Math.toRadians(second.latitude)) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return 2 * 6_371_008.8 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

internal fun freshLocation(spatial: EventSpatialInputs, now: Long, config: EventPolicyConfig): EventLocationObservation? =
    spatial.location?.takeIf { it.connected && validCoordinates(it.latitude, it.longitude) &&
        isFresh(it.observedElapsedMs, now, config.locationMaxAgeMs) && it.sourceRevision >= 0 &&
        (it.sourceTimeToken == null || it.sourceTimeToken >= 0) }

internal fun spatialCache(spatial: EventSpatialInputs): SchoolZoneSnapshot? =
    spatial.schoolZones?.takeIf { it.sha256.isNotBlank() && it.datasetVersion.isNotBlank() }

internal fun nearestSchool(point: EventGeoPoint, zones: SchoolZoneSnapshot): SchoolPointDistance? =
    zones.zones.asSequence().mapIndexedNotNull { index, zone ->
        geographicDistanceMeters(point, EventGeoPoint(zone.latitude, zone.longitude))?.let { SchoolPointDistance(zone, index, it) }
    }.minWithOrNull(compareBy<SchoolPointDistance> { it.distanceMeters }.thenBy { it.school.id })
