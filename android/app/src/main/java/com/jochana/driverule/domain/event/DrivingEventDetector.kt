package com.jochana.driverule.domain.event

import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.NavigationTurn
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot

/** The owner supplies a process/session ID and the explicit navigation route revision. */
data class EventScope(val sessionId: String, val routeId: String) {
    init { require(sessionId.isNotBlank() && routeId.isNotBlank()) }
}

data class EventOccurrenceKey(
    val sessionId: String,
    val routeId: String,
    val eventTag: CachedEventTag,
    val ordinal: Long,
) {
    /** Length prefixes avoid collisions when an owner supplies IDs containing delimiters. */
    fun stableId(): String = "${sessionId.length}:$sessionId|${routeId.length}:$routeId|${eventTag.name}|$ordinal"
}

data class RightTurnEncounterState(
    val scope: EventScope? = null,
    val encounterOrdinal: Long = 0,
    val armed: Boolean = true,
    val activeOccurrenceKey: EventOccurrenceKey? = null,
)

/** Evidence for a supported approach; no proof of a traffic signal, parking act or legal boundary. */
data class DrivingEventCandidate(
    val key: EventOccurrenceKey,
    val observedAtElapsedMs: Long,
    val expiresAtElapsedMs: Long,
    val distanceMeters: Double,
    val speedMetersPerSecond: Float,
    val proof: EventCandidateProof = EventCandidateProof.RightTurn,
) {
    val eventTag: CachedEventTag get() = key.eventTag
}

data class EventDetection(val state: RightTurnEncounterState, val candidates: List<DrivingEventCandidate>) {
    val activeOccurrenceKey: EventOccurrenceKey? get() = state.activeOccurrenceKey
}

/**
 * Stateless transition function. An entry is consumed even if vehicle/output/cache gates block
 * it. Stale data, empty lists and connection errors never rearm the same encounter. The owner
 * keeps this state for its session; a durable attempt ledger separately guards process restarts.
 */
class DrivingEventDetector(private val config: EventPolicyConfig = EventPolicyConfig()) {
    fun evaluate(
        scope: EventScope,
        previous: DrivingEncounterState,
        vehicle: VehicleSignalSnapshot,
        navigation: NavigationSignalSnapshot,
        nowElapsedMs: Long,
        spatial: EventSpatialInputs,
    ): DrivingEventDetection {
        val state = if (previous.scope == scope) previous else DrivingEncounterState(scope = scope)
        val rightTurn = evaluate(scope, state.rightTurn, vehicle, navigation, nowElapsedMs)
        return evaluateSpatialEvents(config, scope, state, rightTurn, vehicle, navigation, nowElapsedMs, spatial)
    }

    fun evaluate(
        scope: EventScope,
        previous: RightTurnEncounterState,
        vehicle: VehicleSignalSnapshot,
        navigation: NavigationSignalSnapshot,
        nowElapsedMs: Long,
    ): EventDetection {
        val state = if (previous.scope == scope) previous else RightTurnEncounterState(scope = scope)
        val turns = freshTurns(navigation, nowElapsedMs, config) ?: return EventDetection(state, emptyList())
        val first = turns.firstOrNull() ?: return EventDetection(state, emptyList())
        if (!first.distanceMeters.isFinite() || first.distanceMeters < 0) return EventDetection(state, emptyList())

        if (first.typeCode in KNOWN_OTHER_TURNS) {
            return EventDetection(state.copy(armed = true, activeOccurrenceKey = null), emptyList())
        }
        if (first.typeCode != EXACT_RIGHT_TURN) return EventDetection(state, emptyList())
        if (first.distanceMeters >= config.rightTurnRearmDistanceMeters) {
            return EventDetection(state.copy(armed = true, activeOccurrenceKey = null), emptyList())
        }
        if (first.distanceMeters <= 0) {
            return EventDetection(state.copy(activeOccurrenceKey = null), emptyList())
        }
        if (first.distanceMeters > config.rightTurnEntryDistanceMeters || !state.armed) {
            return EventDetection(state, emptyList())
        }
        // No wrap-around can create a duplicate key. A new owner scope is needed after overflow.
        if (state.encounterOrdinal == Long.MAX_VALUE) return EventDetection(state.copy(armed = false), emptyList())
        val ordinal = state.encounterOrdinal + 1
        val key = EventOccurrenceKey(scope.sessionId, scope.routeId, CachedEventTag.RIGHT_TURN_CROSSWALK, ordinal)
        val entered = state.copy(encounterOrdinal = ordinal, armed = false, activeOccurrenceKey = key)
        val speed = freshForwardSpeed(vehicle, nowElapsedMs, config) ?: return EventDetection(entered, emptyList())
        val eta = arrivalDurationMs(first.distanceMeters, speed)
        val deadline = minOf(addBounded(nowElapsedMs, config.candidateLifetimeMs),
            eta?.let { addBounded(nowElapsedMs, (it - config.speechEndMarginMs).coerceAtLeast(0)) } ?: Long.MAX_VALUE)
        if (deadline <= nowElapsedMs) return EventDetection(entered, emptyList())
        return EventDetection(entered, listOf(DrivingEventCandidate(key, nowElapsedMs, deadline,
            first.distanceMeters, speed)))
    }
}

internal const val EXACT_RIGHT_TURN = "TURN_RIGHT"

// Pleos navi-data TurnType names. Unknown/new provider values cannot prove an encounter ended.
private val KNOWN_OTHER_TURNS = setOf(
    "TURN_STRAIGHT", "TURN_LEFT", "TURN_KEEP_R", "TURN_KEEP_L", "TURN_SHARP_R", "TURN_SHARP_L",
    "TURN_UTURN", "TURN_ENTER_IC_R", "TURN_ENTER_IC_L", "TURN_ENTER_IC_C", "TURN_EXIT_IC_R",
    "TURN_EXIT_IC_L", "TURN_EXIT_IC_C", "TURN_ENTER_UNDER_R", "TURN_ENTER_UNDER_L", "TURN_ENTER_UNDER_C",
    "TURN_AVOID_UNDER_R", "TURN_AVOID_UNDER_L", "TURN_AVOID_UNDER_C", "TURN_ENTER_OVER_R",
    "TURN_ENTER_OVER_L", "TURN_ENTER_OVER_C", "TURN_AVOID_OVER_R", "TURN_AVOID_OVER_L", "TURN_AVOID_OVER_C",
    "TURN_ENTER_ROUNDABOUT_02", "TURN_ENTER_ROUNDABOUT_03", "TURN_ENTER_ROUNDABOUT_04",
    "TURN_ENTER_ROUNDABOUT_06", "TURN_ENTER_ROUNDABOUT_08", "TURN_ENTER_ROUNDABOUT_09",
    "TURN_ENTER_ROUNDABOUT_10", "TURN_ENTER_ROUNDABOUT_12", "TURN_EXIT_ROUNDABOUT", "TURN_WAY",
    "TURN_ARRIVE", "TURN_TUNNEL", "TURN_DEVIDE_MERGE", "TURN_TOLL", "TURN_ENTER_SERVICEAREA", "TURN_RESTAREA",
)

/** Only the first (nearest) TBT is evidence. A later right turn is not the current approach. */
internal fun freshTurns(navigation: NavigationSignalSnapshot, now: Long, config: EventPolicyConfig): List<NavigationTurn>? {
    if (navigation.connection != NavigationConnection.Connected) return null
    val route = navigation.routeActive as? NavigationObservation.Fresh ?: return null
    if (!route.value || !isFresh(route.observedElapsedMs, now, config.navigationMaxAgeMs)) return null
    val turns = navigation.turns as? NavigationObservation.Fresh ?: return null
    if (!isFresh(turns.observedElapsedMs, now, config.navigationMaxAgeMs)) return null
    return turns.value
}

/** Gear is on-change, so connection/status matter rather than elapsed time since the last shift. */
internal fun freshForwardSpeed(vehicle: VehicleSignalSnapshot, now: Long, config: EventPolicyConfig): Float? {
    if (vehicle.connection != SignalConnection.Connected) return null
    val gear = vehicle.gear.availableValue ?: return null
    if (gear in setOf(SelectedGear.Park, SelectedGear.Neutral, SelectedGear.Reverse)) return null
    val speed = vehicle.speedMetersPerSecond
    val value = speed.availableValue ?: return null
    if (!value.isFinite() || value < 0 || !isFresh(speed.observedAtElapsedMs, now, config.speedMaxAgeMs)) return null
    if (speed.sourceTimestampElapsedMs != null && !isFresh(speed.sourceTimestampElapsedMs, now, config.speedMaxAgeMs)) return null
    // Zero in a forward gear is allowed, but produces no fabricated arrival estimate or UX state.
    return value
}

internal fun arrivalDurationMs(distanceMeters: Double, speedMetersPerSecond: Float): Long? =
    if (speedMetersPerSecond == 0f) null else (distanceMeters / speedMetersPerSecond * 1_000).toLong()
