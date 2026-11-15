package com.jochana.driverule.domain.vehicle

import kotlinx.coroutines.flow.StateFlow

/** Vehicle-local observations only. No positions are persisted or uploaded by this source. */
interface LocationSignalSource {
    val state: StateFlow<LocationSignalSnapshot>
    fun start()
    fun stop()
}

enum class LocationConnection { Stopped, Connecting, Connected, Unavailable, PermissionDenied, Failed }

enum class LocationUnavailable { SourceStopped, SourceUnavailable, PermissionDenied, InvalidData, Failed }

/** Confidence is an uninterpreted SDK value, not an accuracy radius in metres. */
data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val rawConfidence: Float,
    /** Ordering token only: the SDK does not document an epoch/elapsed clock for this field. */
    val rawSourceTimestampMillis: Long,
)

sealed interface LocationObservation {
    data object Unknown : LocationObservation
    /** Receipt freshness + increasing source token, not a verified absolute measurement age. */
    data class Fresh(val value: VehicleLocation, val observedElapsedMs: Long) : LocationObservation
    data class Stale(val lastObservedElapsedMs: Long) : LocationObservation
    data class Unavailable(val reason: LocationUnavailable) : LocationObservation
}

data class LocationSignalSnapshot(
    /** Connected means a valid continuous callback arrived, not merely that the SDK is installed. */
    val connection: LocationConnection = LocationConnection.Stopped,
    val position: LocationObservation = LocationObservation.Unknown,
    /** Changes after a source restart or freshness discontinuity; recovery must not invent an entry. */
    val sourceRevision: Long = 0,
)
