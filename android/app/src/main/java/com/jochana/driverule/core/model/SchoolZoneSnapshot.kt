package com.jochana.driverule.core.model

/** Public dataset points, not vehicle locations. There is no stable source row ID. */
data class SchoolZonePoint(val latitude: Double, val longitude: Double) {
    /** Coordinate identity is stable across file ordering; normalize signed zero. */
    val id: String get() = "${bits(latitude)}:${bits(longitude)}"

    private fun bits(value: Double): Long = (if (value == 0.0) 0.0 else value).toBits()
}

/** Only a fully validated, atomically committed file may become the repository snapshot. */
data class SchoolZoneSnapshot(
    val datasetVersion: String,
    val sha256: String,
    val zones: List<SchoolZonePoint>,
)

sealed interface SchoolZoneLoadState {
    data object Loading : SchoolZoneLoadState
    data class Ready(val snapshot: SchoolZoneSnapshot) : SchoolZoneLoadState
    data object Unavailable : SchoolZoneLoadState
}

sealed interface SchoolZoneSyncResult {
    data class Updated(val snapshot: SchoolZoneSnapshot) : SchoolZoneSyncResult
    data object Unchanged : SchoolZoneSyncResult
    data class Failure(val reason: SchoolZoneSyncFailure, val retryable: Boolean) : SchoolZoneSyncResult
}

enum class SchoolZoneSyncFailure { Network, Http, InvalidData, Storage }
