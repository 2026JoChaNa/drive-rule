package com.jochana.driverule.domain.event

import com.jochana.driverule.core.model.CachedAudience
import com.jochana.driverule.core.model.CachedCardType
import com.jochana.driverule.core.model.CachedChangeKind
import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.core.model.CachedLawCard
import com.jochana.driverule.core.model.LawCacheSnapshot
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalStatus
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class GuidancePlanPolicyTest {
    private val policy = GuidancePlanPolicy()
    private val detection = eventDetection()

    private fun select(
        cards: List<CachedLawCard> = listOf(eventCard()),
        candidates: List<DrivingEventCandidate> = detection.candidates,
        cache: LawCacheSnapshot = eventCache(*cards.toTypedArray()),
        profile: Long? = 1,
        now: Long = EVENT_NOW,
        epoch: Long = EVENT_EPOCH,
    ) = policy.select(candidates, cache, profile, EVENT_DAY, now, epoch)

    private fun valid(
        plan: GuidancePlan,
        scope: EventScope = EVENT_SCOPE,
        state: RightTurnEncounterState = detection.state,
        vehicle: VehicleSignalSnapshot = eventVehicle(),
        navigation: NavigationSignalSnapshot = eventNavigation(),
        cache: LawCacheSnapshot = eventCache(plan.card),
        profile: Long? = 1,
        today: LocalDate = EVENT_DAY,
        now: Long = EVENT_NOW,
        epoch: Long = EVENT_EPOCH,
        phase: PlanValidationPhase = PlanValidationPhase.BeforeStart,
    ) = policy.isValid(plan, scope, state, vehicle, navigation, cache, profile, today, now, epoch, phase)

    @Test fun `general matching event uses reviewed top level text for every supported card type`() {
        CachedCardType.entries.forEach { type ->
            val card = eventCard(tts = "  원문 그대로\n읽기  ").copy(cardType = type,
                decisionJson = if (type == CachedCardType.CONDITIONAL) "{\"notEvaluatedHere\":true}" else null)
            val plan = select(cards = listOf(card))!!
            assertEquals(card, plan.card)
            assertEquals(card.ttsText, plan.card.ttsText)
            assertEquals(GuidanceEffectiveStage.Current, plan.effectiveStage)
            assertNull(plan.estimatedArrivalElapsedMs)
        }
    }

    @Test fun `limited unmatched untagged and empty speech cards never enter event output`() {
        val excluded = listOf(eventCard().copy(audience = CachedAudience.LIMITED),
            eventCard().copy(eventTag = CachedEventTag.SCHOOL_ZONE_ENTRY), eventCard().copy(eventTag = null),
            eventCard(tts = " \n "))
        assertNull(select(cards = excluded))
        assertNull(select(cards = emptyList()))
    }

    @Test fun `multiple cards are deterministically ordered law before operation`() {
        val operation = eventCard(1).copy(changeKind = CachedChangeKind.OPERATION, verifiedAtMillis = EVENT_EPOCH)
        val law = eventCard(2).copy(effectiveOn = EVENT_DAY.plusDays(1), verifiedAtMillis = 1)
        assertEquals(2L, select(cards = listOf(operation, law))!!.card.id)
        assertEquals(2L, select(cards = listOf(law, operation))!!.card.id)
    }

    @Test fun `current upcoming then unspecified stages have stable precedence`() {
        val current = eventCard(3).copy(verifiedAtMillis = 1)
        val upcoming = eventCard(2).copy(effectiveOn = EVENT_DAY.plusDays(1), verifiedAtMillis = 2)
        val unknown = eventCard(1).copy(effectiveOn = null, verifiedAtMillis = 3)
        assertEquals(3L, select(cards = listOf(unknown, upcoming, current))!!.card.id)
        assertEquals(2L, select(cards = listOf(unknown, upcoming))!!.card.id)
        assertEquals(GuidanceEffectiveStage.Unspecified, select(cards = listOf(unknown))!!.effectiveStage)
    }

    @Test fun `verification timestamp then ascending id break same stage ties`() {
        val older = eventCard(1).copy(verifiedAtMillis = 10)
        val newerLargeId = eventCard(3).copy(verifiedAtMillis = 20)
        val newerSmallId = eventCard(2).copy(verifiedAtMillis = 20)
        listOf(listOf(older, newerLargeId, newerSmallId), listOf(newerSmallId, older, newerLargeId)).forEach { cards ->
            assertEquals(2L, select(cards = cards)!!.card.id)
        }
    }

    @Test fun `a too long higher ranked card is skipped without editing it`() {
        val longLaw = eventCard(1, "가".repeat(181))
        val shortOperation = eventCard(2).copy(changeKind = CachedChangeKind.OPERATION)
        val plan = select(cards = listOf(longLaw, shortOperation))!!
        assertEquals(shortOperation, plan.card)
        assertEquals("가".repeat(181), longLaw.ttsText)
        assertNull(select(cards = listOf(longLaw)))
    }

    @Test fun `missing old or future cache time fails closed at exact twenty four hours`() {
        val cache = eventCache(eventCard())
        assertNotNull(select(cache = cache, epoch = EVENT_EPOCH + 86_400_000 - 1))
        assertNull(select(cache = cache, epoch = EVENT_EPOCH + 86_400_000))
        assertNull(select(cache = cache.copy(lastSuccessfulSyncAtMillis = null)))
        assertNull(select(cache = cache.copy(lastSuccessfulSyncAtMillis = EVENT_EPOCH + 1)))
        assertNull(select(cache = cache.copy(lastSuccessfulSyncAtMillis = -1)))
    }

    @Test fun `no active profile no event output`() {
        assertNull(select(profile = null))
        assertNull(select(profile = 0))
        assertNull(select(profile = -1))
    }

    @Test fun `expired future and unsupported candidate evidence is rejected`() {
        val candidate = detection.candidates.single()
        assertNull(select(now = candidate.expiresAtElapsedMs))
        assertNull(select(now = EVENT_NOW - 1))
        val excluded = listOf(candidate.copy(distanceMeters = Double.NaN), candidate.copy(distanceMeters = 0.0),
            candidate.copy(distanceMeters = 151.0), candidate.copy(speedMetersPerSecond = -1f),
            candidate.copy(key = candidate.key.copy(eventTag = CachedEventTag.PARKING_HIGHWAY)),
            candidate.copy(key = candidate.key.copy(ordinal = 0)),
            candidate.copy(key = candidate.key.copy(sessionId = "")))
        excluded.forEach { assertNull(select(candidates = listOf(it))) }
    }

    @Test fun `full speech must fit the remaining candidate duration and arrival margin`() {
        val moving = eventDetection(distance = 100.0, speed = 10f).candidates
        assertNotNull(select(cards = listOf(eventCard(tts = "가".repeat(54))), candidates = moving))
        assertNull(select(cards = listOf(eventCard(tts = "가".repeat(55))), candidates = moving))
        assertNull(select(cards = listOf(eventCard(tts = "가".repeat(54))), candidates = moving, now = EVENT_NOW + 1))
        val close = eventDetection(distance = 20.0, speed = 10f).candidates
        assertNull(select(cards = listOf(eventCard(tts = "가")), candidates = close))
        val leadBoundary = eventDetection(distance = 30.0, speed = 10f).candidates
        assertNotNull(select(cards = listOf(eventCard(tts = "가")), candidates = leadBoundary))
    }

    @Test fun `unicode estimate counts code points while preserving the exact text`() {
        val emoji = "\uD83D\uDE99".repeat(6)
        val plan = select(cards = listOf(eventCard(tts = emoji)))!!
        assertEquals(1_000L, plan.estimatedSpeechDurationMs)
        assertEquals(emoji, plan.card.ttsText)
    }

    @Test fun `card retirement and same version corrections invalidate captured output`() {
        val plan = select()!!
        assertTrue(valid(plan))
        assertFalse(valid(plan, cache = eventCache()))
        assertFalse(valid(plan, cache = eventCache(plan.card.copy(title = "same version corrected title"))))
        assertFalse(valid(plan, cache = eventCache(plan.card.copy(ttsText = "changed reviewed text"))))
        assertFalse(valid(plan, cache = eventCache(plan.card.copy(version = 2))))
        assertFalse(valid(plan, cache = eventCache(plan.card.copy(eventTag = null))))
        assertFalse(valid(plan, cache = eventCache(plan.card.copy(audience = CachedAudience.LIMITED))))
    }

    @Test fun `profile route session and consumed encounter identity bind the plan`() {
        val plan = select()!!
        assertFalse(valid(plan, profile = 2))
        assertFalse(valid(plan, profile = null))
        assertFalse(valid(plan, scope = EVENT_SCOPE.copy(routeId = "another")))
        assertFalse(valid(plan, scope = EVENT_SCOPE.copy(sessionId = "another")))
        assertFalse(valid(plan, state = detection.state.copy(activeOccurrenceKey = null)))
        assertFalse(valid(plan, state = detection.state.copy(activeOccurrenceKey = plan.candidate.key.copy(ordinal = 2))))
    }

    @Test fun `midnight effective stage change and stale cache cancel rather than replay`() {
        val plan = select(cards = listOf(eventCard().copy(effectiveOn = EVENT_DAY.plusDays(1))))!!
        assertTrue(valid(plan))
        assertFalse(valid(plan, today = EVENT_DAY.plusDays(1)))
        assertFalse(valid(plan, epoch = EVENT_EPOCH + 86_400_000))
    }

    @Test fun `latest signal loss cancels even while original evidence remains within TTL`() {
        val plan = select()!!
        assertFalse(valid(plan, navigation = eventNavigation().copy(connection = NavigationConnection.Failed)))
        assertFalse(valid(plan, navigation = eventNavigation().copy(routeActive = NavigationObservation.Fresh(false, EVENT_NOW))))
        assertFalse(valid(plan, navigation = eventNavigation(type = "TURN_KEEP_R")))
        assertFalse(valid(plan, vehicle = eventVehicle(gear = SelectedGear.Park)))
        val denied = eventVehicle().copy(speedMetersPerSecond = eventVehicle().speedMetersPerSecond.copy(status = SignalStatus.PermissionDenied))
        assertFalse(valid(plan, vehicle = denied))
        assertFalse(valid(plan, now = EVENT_NOW + 5_000))
        assertTrue(valid(plan, now = EVENT_NOW + 5_000, vehicle = eventVehicle(EVENT_NOW + 5_000),
            navigation = eventNavigation(EVENT_NOW + 5_000)))
    }

    @Test fun `before start uses latest ETA instead of trusting old zero speed`() {
        val plan = select(cards = listOf(eventCard(tts = "가".repeat(60))))!!
        assertFalse(valid(plan, vehicle = eventVehicle(speed = 20f)))
        assertTrue(valid(plan, vehicle = eventVehicle(speed = 5f)))
    }

    @Test fun `in progress allows hysteresis but stops at zero rearm edge or arrival margin`() {
        val plan = select()!!
        assertFalse(valid(plan, navigation = eventNavigation(distance = 175.0)))
        assertTrue(valid(plan, navigation = eventNavigation(distance = 175.0), phase = PlanValidationPhase.InProgress))
        assertFalse(valid(plan, navigation = eventNavigation(distance = 200.0), phase = PlanValidationPhase.InProgress))
        assertFalse(valid(plan, navigation = eventNavigation(distance = 0.0), phase = PlanValidationPhase.InProgress))
        assertFalse(valid(plan, navigation = eventNavigation(distance = 10.0), vehicle = eventVehicle(speed = 10f),
            phase = PlanValidationPhase.InProgress))
    }

    @Test fun `in progress does not demand enough time to repeat the original sentence`() {
        val moving = eventDetection(distance = 100.0, speed = 10f)
        val plan = select(cards = listOf(eventCard(tts = "가".repeat(42))), candidates = moving.candidates)!!
        val later = EVENT_NOW + 6_000
        val nav = eventNavigation(later, distance = 40.0)
        val vehicle = eventVehicle(later, speed = 10f)
        assertFalse(valid(plan, state = moving.state, now = later, navigation = nav, vehicle = vehicle))
        assertTrue(valid(plan, state = moving.state, now = later, navigation = nav, vehicle = vehicle,
            phase = PlanValidationPhase.InProgress))
        assertFalse(valid(plan, state = moving.state, now = plan.expiresAtElapsedMs,
            navigation = eventNavigation(plan.expiresAtElapsedMs), vehicle = eventVehicle(plan.expiresAtElapsedMs),
            phase = PlanValidationPhase.InProgress))
    }
}
