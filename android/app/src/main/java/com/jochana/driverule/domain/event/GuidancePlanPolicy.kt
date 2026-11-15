package com.jochana.driverule.domain.event

import com.jochana.driverule.core.model.CachedAudience
import com.jochana.driverule.core.model.CachedChangeKind
import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.core.model.CachedLawCard
import com.jochana.driverule.core.model.LawCacheSnapshot
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import java.time.LocalDate
import kotlin.math.ceil

enum class GuidanceEffectiveStage { Current, Upcoming, Unspecified }

/** An immutable reviewed card capture. No decision-table answer or new legal sentence is made. */
data class GuidancePlan(
    val candidate: DrivingEventCandidate,
    val profileId: Long,
    val card: CachedLawCard,
    val effectiveStage: GuidanceEffectiveStage,
    val estimatedSpeechDurationMs: Long,
    val estimatedArrivalElapsedMs: Long?,
) {
    val expiresAtElapsedMs: Long get() = candidate.expiresAtElapsedMs
}

/** Ongoing speech need not still have enough time to replay its entire original sentence. */
enum class PlanValidationPhase { BeforeStart, InProgress }

class GuidancePlanPolicy(private val config: EventPolicyConfig = EventPolicyConfig()) {
    fun select(
        candidates: List<DrivingEventCandidate>,
        cache: LawCacheSnapshot,
        profileId: Long?,
        todayKorea: LocalDate,
        nowElapsedMs: Long,
        nowEpochMs: Long,
    ): GuidancePlan? {
        if (profileId == null || profileId <= 0 || !cacheFresh(cache, nowEpochMs)) return null
        val eligible = candidates.asSequence()
            .filter { candidateValid(it, nowElapsedMs) }
            .flatMap { candidate ->
                cache.cards.asSequence().filter { cardMatches(it, candidate.eventTag) }.map { card ->
                    val eta = if (candidate.proof is EventCandidateProof.SchoolProximity) null
                        else arrivalDurationMs(candidate.distanceMeters, candidate.speedMetersPerSecond)
                    GuidancePlan(candidate, profileId, card, stage(card, todayKorea), speechDuration(card.ttsText),
                        eta?.let { addBounded(candidate.observedAtElapsedMs, it) })
                }
            }
            .filter { enoughStartTime(it, nowElapsedMs, it.estimatedArrivalElapsedMs) }
        // Provisional event order: imminent maneuver, facility proximity, destination proximity.
        // Every simultaneous entry is consumed by detection; this is not an output backlog.
        return eligible.sortedWith(compareBy<GuidancePlan> { eventPriority(it.candidate.eventTag) }
            .thenBy { it.candidate.expiresAtElapsedMs }
            .thenBy { if (it.card.changeKind == CachedChangeKind.LAW) 0 else 1 }
            .thenBy { it.effectiveStage.ordinal }
            .thenByDescending { it.card.verifiedAtMillis }
            .thenBy { it.card.id }
            .thenBy { it.candidate.key.stableId() }).firstOrNull()
    }

    /**
     * Recheck after an asynchronous ledger reservation and on every relevant input change.
     * Missing/retired/unsupported cards disappear from cache.cards. Full equality also catches
     * same-version corrections. The owner separately enforces genuine UX and audio permissions.
     */
    fun isValid(
        plan: GuidancePlan,
        scope: EventScope,
        encounter: RightTurnEncounterState,
        vehicle: VehicleSignalSnapshot,
        navigation: NavigationSignalSnapshot,
        cache: LawCacheSnapshot,
        profileId: Long?,
        todayKorea: LocalDate,
        nowElapsedMs: Long,
        nowEpochMs: Long,
        phase: PlanValidationPhase = PlanValidationPhase.BeforeStart,
    ): Boolean {
        if (plan.candidate.proof != EventCandidateProof.RightTurn || profileId != plan.profileId || scope != encounter.scope ||
            encounter.activeOccurrenceKey != plan.candidate.key ||
            plan.candidate.key.sessionId != scope.sessionId || plan.candidate.key.routeId != scope.routeId ||
            !candidateValid(plan.candidate, nowElapsedMs) || !cacheFresh(cache, nowEpochMs) ||
            !cardMatches(plan.card, plan.candidate.eventTag) ||
            stage(plan.card, todayKorea) != plan.effectiveStage || cache.cards.none { it == plan.card }
        ) return false
        val first = freshTurns(navigation, nowElapsedMs, config)?.firstOrNull() ?: return false
        val distanceLimit = if (phase == PlanValidationPhase.InProgress) config.rightTurnRearmDistanceMeters
            else config.rightTurnEntryDistanceMeters
        if (first.typeCode != EXACT_RIGHT_TURN || !first.distanceMeters.isFinite() || first.distanceMeters <= 0 ||
            first.distanceMeters > distanceLimit || first.distanceMeters >= config.rightTurnRearmDistanceMeters) return false
        val speed = freshForwardSpeed(vehicle, nowElapsedMs, config) ?: return false
        val eta = arrivalDurationMs(first.distanceMeters, speed)
        return if (phase == PlanValidationPhase.BeforeStart) {
            enoughStartTime(plan, nowElapsedMs, eta?.let { addBounded(nowElapsedMs, it) })
        } else {
            eta == null || eta > config.speechEndMarginMs
        }
    }

    /** Spatial evidence is validated independently; school proximity never fabricates an ETA. */
    fun isValid(
        plan: GuidancePlan,
        scope: EventScope,
        encounter: DrivingEncounterState,
        vehicle: VehicleSignalSnapshot,
        navigation: NavigationSignalSnapshot,
        cache: LawCacheSnapshot,
        profileId: Long?,
        todayKorea: LocalDate,
        nowElapsedMs: Long,
        nowEpochMs: Long,
        phase: PlanValidationPhase = PlanValidationPhase.BeforeStart,
        spatial: EventSpatialInputs,
    ): Boolean {
        if (plan.candidate.proof == EventCandidateProof.RightTurn) {
            return isValid(plan, scope, encounter.rightTurn, vehicle, navigation, cache, profileId,
                todayKorea, nowElapsedMs, nowEpochMs, phase)
        }
        val key = when (plan.candidate.eventTag) {
            CachedEventTag.SCHOOL_ZONE_ENTRY -> encounter.school.activeOccurrenceKey
            CachedEventTag.PARKING_DESTINATION -> encounter.destination.activeOccurrenceKey
            else -> null
        }
        if (profileId != plan.profileId || scope != encounter.scope || key != plan.candidate.key ||
            plan.candidate.key.sessionId != scope.sessionId || plan.candidate.key.routeId != scope.routeId ||
            !candidateValid(plan.candidate, nowElapsedMs) || !cacheFresh(cache, nowEpochMs) ||
            !cardMatches(plan.card, plan.candidate.eventTag) || stage(plan.card, todayKorea) != plan.effectiveStage ||
            cache.cards.none { it == plan.card }) return false
        val speed = freshForwardSpeed(vehicle, nowElapsedMs, config) ?: return false
        val zones = spatialCache(spatial) ?: return false
        return when (val proof = plan.candidate.proof) {
            EventCandidateProof.RightTurn -> false
            is EventCandidateProof.SchoolProximity -> {
                if (zones.sha256 != proof.cacheSha256 || zones.zones.getOrNull(proof.schoolIndex) != proof.school) return false
                val location = freshLocation(spatial, nowElapsedMs, config) ?: return false
                if (location.sourceRevision != proof.locationSourceRevision ||
                    location.observedElapsedMs < proof.locationObservedElapsedMs ||
                    proof.locationSourceTimeToken?.let { location.sourceTimeToken == null || location.sourceTimeToken < it } == true)
                    return false
                val distance = geographicDistanceMeters(EventGeoPoint(location.latitude, location.longitude),
                    EventGeoPoint(proof.school.latitude, proof.school.longitude)) ?: return false
                if (distance >= config.schoolRearmDistanceMeters ||
                    (phase == PlanValidationPhase.BeforeStart && distance > config.schoolEntryDistanceMeters)) return false
                phase == PlanValidationPhase.InProgress || enoughStartTime(plan, nowElapsedMs, null)
            }
            is EventCandidateProof.DestinationProximity -> {
                if (zones.sha256 != proof.cacheSha256 || zones.zones.getOrNull(proof.schoolIndex) != proof.school) return false
                val destination = freshDestination(navigation, nowElapsedMs, config) ?: return false
                if (EventGeoPoint(destination.latitude, destination.longitude) != proof.destination) return false
                val schoolDistance = geographicDistanceMeters(proof.destination,
                    EventGeoPoint(proof.school.latitude, proof.school.longitude)) ?: return false
                if (schoolDistance > config.destinationSchoolDistanceMeters || destination.remainingDistanceMeters <= 0 ||
                    destination.remainingDistanceMeters >= config.destinationRearmDistanceMeters ||
                    (phase == PlanValidationPhase.BeforeStart && destination.remainingDistanceMeters > config.destinationEntryDistanceMeters))
                    return false
                val eta = arrivalDurationMs(destination.remainingDistanceMeters, speed)
                if (phase == PlanValidationPhase.BeforeStart) enoughStartTime(plan, nowElapsedMs,
                    eta?.let { addBounded(nowElapsedMs, it) }) else eta == null || eta > config.speechEndMarginMs
            }
        }
    }

    private fun candidateValid(candidate: DrivingEventCandidate, now: Long): Boolean =
        candidate.key.ordinal > 0 && candidate.key.sessionId.isNotBlank() && candidate.key.routeId.isNotBlank() &&
            candidate.observedAtElapsedMs >= 0 && now >= candidate.observedAtElapsedMs &&
            now < candidate.expiresAtElapsedMs && now - candidate.observedAtElapsedMs < config.candidateLifetimeMs &&
            candidate.distanceMeters.isFinite() && candidate.speedMetersPerSecond.isFinite() && candidate.speedMetersPerSecond >= 0 &&
            when (val proof = candidate.proof) {
                EventCandidateProof.RightTurn -> candidate.eventTag == CachedEventTag.RIGHT_TURN_CROSSWALK &&
                    candidate.distanceMeters > 0 && candidate.distanceMeters <= config.rightTurnEntryDistanceMeters
                is EventCandidateProof.SchoolProximity -> candidate.eventTag == CachedEventTag.SCHOOL_ZONE_ENTRY &&
                    candidate.distanceMeters >= 0 && candidate.distanceMeters <= config.schoolEntryDistanceMeters &&
                    proof.cacheSha256.isNotBlank() && validCoordinates(proof.school.latitude, proof.school.longitude) &&
                    proof.locationSourceRevision >= 0 && proof.locationObservedElapsedMs >= 0 && proof.schoolIndex >= 0
                is EventCandidateProof.DestinationProximity -> candidate.eventTag == CachedEventTag.PARKING_DESTINATION &&
                    candidate.distanceMeters > 0 && candidate.distanceMeters <= config.destinationEntryDistanceMeters &&
                    proof.cacheSha256.isNotBlank() && validCoordinates(proof.school.latitude, proof.school.longitude) &&
                    validCoordinates(proof.destination.latitude, proof.destination.longitude) && proof.schoolIndex >= 0
            }

    private fun eventPriority(tag: CachedEventTag): Int = when (tag) {
        CachedEventTag.RIGHT_TURN_CROSSWALK -> 0
        CachedEventTag.SCHOOL_ZONE_ENTRY -> 1
        CachedEventTag.PARKING_DESTINATION -> 2
        CachedEventTag.PARKING_HIGHWAY -> 3 // Unsupported proof never passes candidateValid.
    }

    private fun cardMatches(card: CachedLawCard, tag: CachedEventTag): Boolean =
        card.audience == CachedAudience.GENERAL && card.eventTag == tag && card.ttsText.isNotBlank()

    private fun cacheFresh(cache: LawCacheSnapshot, now: Long): Boolean =
        isFresh(cache.lastSuccessfulSyncAtMillis, now, config.cacheMaxAgeMs)

    private fun stage(card: CachedLawCard, today: LocalDate): GuidanceEffectiveStage = when {
        card.effectiveOn == null -> GuidanceEffectiveStage.Unspecified
        card.effectiveOn > today -> GuidanceEffectiveStage.Upcoming
        else -> GuidanceEffectiveStage.Current
    }

    private fun speechDuration(text: String): Long =
        ceil(text.codePointCount(0, text.length) / config.speechCharactersPerSecond * 1_000).toLong()

    private fun enoughStartTime(plan: GuidancePlan, now: Long, arrivalAt: Long?): Boolean {
        val remainingCandidateMs = plan.expiresAtElapsedMs - now
        val requiredSpeechMs = addBounded(plan.estimatedSpeechDurationMs, config.speechEndMarginMs)
        val requiredLeadMs = maxOf(config.minimumLeadTimeMs, requiredSpeechMs)
        if (remainingCandidateMs < plan.estimatedSpeechDurationMs) return false
        return arrivalAt == null || arrivalAt - now >= requiredLeadMs
    }
}
