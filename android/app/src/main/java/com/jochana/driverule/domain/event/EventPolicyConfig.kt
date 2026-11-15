package com.jochana.driverule.domain.event

/** Provisional integration values, not released driving thresholds or measured TTS duration. */
data class EventPolicyConfig(
    val rightTurnEntryDistanceMeters: Double = 150.0,
    val rightTurnRearmDistanceMeters: Double = 200.0,
    val navigationMaxAgeMs: Long = 10_000L,
    val speedMaxAgeMs: Long = 5_000L,
    val candidateLifetimeMs: Long = 30_000L,
    val minimumLeadTimeMs: Long = 3_000L,
    val speechCharactersPerSecond: Double = 6.0,
    val speechEndMarginMs: Long = 1_000L,
    val cacheMaxAgeMs: Long = 24 * 60 * 60 * 1_000L,
    val locationMaxAgeMs: Long = 5_000L,
    val schoolEntryDistanceMeters: Double = 370.0,
    val schoolRearmDistanceMeters: Double = 470.0,
    val destinationSchoolDistanceMeters: Double = 300.0,
    val destinationEntryDistanceMeters: Double = 500.0,
    val destinationRearmDistanceMeters: Double = 600.0,
) {
    init {
        require(rightTurnEntryDistanceMeters.isFinite() && rightTurnEntryDistanceMeters > 0)
        require(rightTurnRearmDistanceMeters.isFinite() && rightTurnRearmDistanceMeters > rightTurnEntryDistanceMeters)
        require(navigationMaxAgeMs > 0 && speedMaxAgeMs > 0 && candidateLifetimeMs > 0)
        require(minimumLeadTimeMs > 0 && speechEndMarginMs >= 0 && cacheMaxAgeMs > 0)
        require(speechCharactersPerSecond.isFinite() && speechCharactersPerSecond > 0)
        require(locationMaxAgeMs > 0)
        require(schoolEntryDistanceMeters.isFinite() && schoolEntryDistanceMeters > 0)
        require(schoolRearmDistanceMeters.isFinite() && schoolRearmDistanceMeters > schoolEntryDistanceMeters)
        require(destinationSchoolDistanceMeters.isFinite() && destinationSchoolDistanceMeters > 0)
        require(destinationEntryDistanceMeters.isFinite() && destinationEntryDistanceMeters > 0)
        require(destinationRearmDistanceMeters.isFinite() && destinationRearmDistanceMeters > destinationEntryDistanceMeters)
    }
}

internal fun isFresh(observedAt: Long?, now: Long, maxAgeMs: Long): Boolean =
    observedAt != null && observedAt >= 0 && now >= observedAt && now - observedAt < maxAgeMs

internal fun addBounded(time: Long, duration: Long): Long =
    if (duration > Long.MAX_VALUE - time) Long.MAX_VALUE else time + duration
