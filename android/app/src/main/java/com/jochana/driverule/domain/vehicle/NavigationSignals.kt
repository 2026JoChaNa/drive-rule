package com.jochana.driverule.domain.vehicle

import kotlinx.coroutines.flow.StateFlow

/** Memory-only observations; these are navigation inputs, not detected traffic events. */
interface NavigationSignalSource {
    val state: StateFlow<NavigationSignalSnapshot>
    fun start()
    fun stop()
}

enum class NavigationConnection { Stopped, Connecting, Connected, Unavailable, PermissionDenied, Failed }

enum class NavigationSignalUnavailable { SourceStopped, SourceUnavailable, PermissionDenied, InvalidData, Failed }

/** No value is exposed once it expires, so downstream code cannot accidentally use stale input. */
sealed interface NavigationObservation<out T> {
    data object Unknown : NavigationObservation<Nothing>
    data class Fresh<T>(val value: T, val observedElapsedMs: Long) : NavigationObservation<T>
    data class Stale(val lastObservedElapsedMs: Long) : NavigationObservation<Nothing>
    data class Unavailable(val reason: NavigationSignalUnavailable) : NavigationObservation<Nothing>
}

enum class NavigationActivityStatus { Inactive, Initializing, FreeDrive, RouteDrive }

/** typeCode is the SDK enum name, not a backend eventTag or proof that a turn happened. */
data class NavigationTurn(val typeCode: String, val distanceMeters: Double)

/** Only geometry and remaining distance enter memory; names, addresses and POI IDs are discarded. */
data class NavigationDestination(val latitude: Double, val longitude: Double, val remainingDistanceMeters: Double)

data class NavigationCaution(val typeCode: String, val remainingDistanceMeters: Int)

/** The booleans are the navigation provider's reports, not this app's violation judgement. */
data class NavigationCamera(
    val typeCode: String,
    val reportedActive: Boolean,
    val remainingDistanceMeters: Int,
    val speedLimitKph: Int?,
    val sectionAverageSpeedKph: Int?,
    val reportedOverSpeed: Boolean,
    val reportedOverSectionAverageSpeed: Boolean,
)

data class NavigationSignalSnapshot(
    val connection: NavigationConnection = NavigationConnection.Stopped,
    val navigationStatus: NavigationObservation<NavigationActivityStatus> = NavigationObservation.Unknown,
    val routeActive: NavigationObservation<Boolean> = NavigationObservation.Unknown,
    val turns: NavigationObservation<List<NavigationTurn>> = NavigationObservation.Unknown,
    val cautions: NavigationObservation<List<NavigationCaution>> = NavigationObservation.Unknown,
    val cameras: NavigationObservation<List<NavigationCamera>> = NavigationObservation.Unknown,
    // SDK KDoc writes "km" for this callback; do not compare with vehicle speed before unit verification.
    val rawReportedRoadSpeedLimit: NavigationObservation<Int> = NavigationObservation.Unknown,
    /** Local invalidation generation, not a route ID, location or backend identifier. */
    val routeRevision: Long = 0,
    val destination: NavigationObservation<NavigationDestination> = NavigationObservation.Unknown,
)

/** Local freshness policy, not an SDK promise about the frequency of callbacks. */
fun NavigationSignalSnapshot.expire(nowElapsedMs: Long, maxAgeMs: Long): NavigationSignalSnapshot {
    require(maxAgeMs > 0)
    fun <T> NavigationObservation<T>.expired(): NavigationObservation<T> = when (this) {
        is NavigationObservation.Fresh -> if (
            nowElapsedMs < observedElapsedMs || nowElapsedMs - observedElapsedMs >= maxAgeMs
        ) NavigationObservation.Stale(observedElapsedMs) else this
        else -> this
    }
    return copy(
        navigationStatus = navigationStatus.expired(),
        routeActive = routeActive.expired(),
        turns = turns.expired(),
        destination = destination.expired(),
        cautions = cautions.expired(),
        cameras = cameras.expired(),
        rawReportedRoadSpeedLimit = rawReportedRoadSpeedLimit.expired(),
    )
}
