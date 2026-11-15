package com.jochana.driverule.app.guidance

import com.jochana.driverule.core.model.CachedAudience
import com.jochana.driverule.core.model.CachedCardType
import com.jochana.driverule.core.model.CachedChangeKind
import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.core.model.CachedLawCard
import com.jochana.driverule.core.model.DriverProfile
import com.jochana.driverule.core.model.LawCacheSnapshot
import com.jochana.driverule.core.model.ProfileSnapshot
import com.jochana.driverule.core.model.SchoolZonePoint
import com.jochana.driverule.core.model.SchoolZoneSnapshot
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.event.EventLocationObservation
import com.jochana.driverule.domain.event.EventSpatialInputs
import com.jochana.driverule.domain.event.GuidancePlan
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationDestination
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.NavigationTurn
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.SignalStatus
import com.jochana.driverule.domain.vehicle.VehicleSignal
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises actual detector/policy/controller together; no Android, SDK, network or database. */
@OptIn(ExperimentalCoroutinesApi::class)
class EventGuidanceControllerTest {
    @Test
    fun `right turn outputs the exact matching reviewed server copy once`() = runTest {
        val fixture = fixture()
        val reviewed = fixture.inputs.cache!!.cards.single()
        fixture.inputs = fixture.inputs.copy(cache = fixture.inputs.cache!!.copy(cards = listOf(
            reviewed.copy(id = 1, eventTag = CachedEventTag.SCHOOL_ZONE_ENTRY),
            reviewed.copy(id = 2, audience = CachedAudience.LIMITED),
            reviewed,
        )))

        fixture.update()
        runCurrent()
        repeat(4) {
            fixture.refreshSignals(distanceMeters = 135.0 - it)
            fixture.update()
            runCurrent()
        }

        assertEquals(1, fixture.reserved.size)
        assertEquals(1, fixture.shown.size)
        val output = fixture.shown.single()
        assertSame(reviewed, output.card)
        assertEquals("검수된 서버 음성 원문입니다.", output.card.ttsText)
        assertEquals(CachedEventTag.RIGHT_TURN_CROSSWALK, output.candidate.eventTag)
        assertEquals(1L, output.profileId)
        fixture.controller.close()
    }

    @Test
    fun `name input blocked approach is consumed and does not replay when enabled`() = runTest {
        val fixture = fixture()
        fixture.inputs = fixture.inputs.copy(enabled = false)
        fixture.update()
        fixture.inputs = fixture.inputs.copy(enabled = true)
        fixture.update()
        runCurrent()
        assertTrue(fixture.reserved.isEmpty())
        assertTrue(fixture.shown.isEmpty())

        // A genuinely rearmed approach can still be handled; the gate did not disable the host.
        fixture.refreshSignals(distanceMeters = 250.0)
        fixture.update()
        fixture.refreshSignals()
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.shown.size)
        fixture.controller.close()
    }

    @Test
    fun `permission guide entry is not replayed when onboarding completes`() = runTest {
        val fixture = fixture()
        fixture.inputs = fixture.inputs.copy(profiles = fixture.inputs.profiles!!.copy(permissionGuidePending = true))
        fixture.update()
        fixture.inputs = fixture.inputs.copy(profiles = fixture.inputs.profiles!!.copy(permissionGuidePending = false))
        fixture.update()
        runCurrent()
        assertTrue(fixture.reserved.isEmpty())
        assertTrue(fixture.shown.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun `retirement prevents output even if pending reservation ignores cancellation`() = runTest {
        val late = LateReservation()
        val fixture = fixture(late::reserve)
        fixture.update()
        runCurrent()
        assertEquals(1, late.pendingCount)

        fixture.inputs = fixture.inputs.copy(cache = fixture.inputs.cache!!.copy(cards = emptyList()))
        fixture.update()
        late.complete()
        runCurrent()

        assertTrue(fixture.shown.isEmpty())
        assertTrue(fixture.dismissals > 0)
        fixture.controller.close()
    }

    @Test
    fun `profile switch prevents old drivers delayed reservation from producing output`() = runTest {
        val late = LateReservation()
        val fixture = fixture(late::reserve)
        fixture.update()
        runCurrent()

        fixture.inputs = fixture.inputs.copy(profiles = ProfileSnapshot(
            profiles = listOf(DriverProfile(1, "첫 운전자"), DriverProfile(2, "다음 운전자")),
            activeProfileId = 2,
        ))
        fixture.update()
        late.complete()
        runCurrent()

        assertEquals(1, fixture.reserved.size)
        assertTrue(fixture.shown.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun `route invalidation prevents obsolete delayed reservation from producing output`() = runTest {
        val late = LateReservation()
        val fixture = fixture(late::reserve)
        fixture.update()
        runCurrent()

        fixture.inputs = fixture.inputs.copy(navigation = fixture.inputs.navigation.copy(
            routeRevision = fixture.inputs.navigation.routeRevision + 1,
            routeActive = NavigationObservation.Unknown,
            turns = NavigationObservation.Unknown,
        ))
        fixture.update()
        late.complete()
        runCurrent()

        assertTrue(fixture.shown.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun `close rejects late reservation and later updates`() = runTest {
        val late = LateReservation()
        val fixture = fixture(late::reserve)
        fixture.update()
        runCurrent()
        fixture.controller.close()
        val dismissalsAtClose = fixture.dismissals
        fixture.controller.close()
        late.complete()
        fixture.update()
        runCurrent()

        assertTrue(fixture.shown.isEmpty())
        assertEquals(1, fixture.reserved.size)
        assertEquals(dismissalsAtClose, fixture.dismissals)
    }

    @Test
    fun `synchronous block rejects late reservation and never replays the consumed encounter`() = runTest {
        val late = LateReservation()
        val fixture = fixture(late::reserve)
        fixture.update()
        runCurrent()
        fixture.controller.block()
        late.complete()
        runCurrent()
        fixture.update() // setup/STT has ended, but this is still the same approach.
        runCurrent()

        assertTrue(fixture.shown.isEmpty())
        assertEquals(1, fixture.reserved.size)
        fixture.controller.close()
    }

    @Test
    fun `same version corrected card dismisses active output instead of speaking a replacement`() = runTest {
        val fixture = fixture()
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.shown.size)
        val original = fixture.inputs.cache!!.cards.single()

        fixture.inputs = fixture.inputs.copy(cache = fixture.inputs.cache!!.copy(
            cards = listOf(original.copy(ttsText = "같은 버전의 정정된 서버 원문입니다.")),
        ))
        fixture.update()
        runCurrent()

        assertEquals(1, fixture.dismissals)
        assertEquals(1, fixture.shown.size)
        assertEquals(1, fixture.reserved.size)
        assertSame(original, fixture.shown.single().card)
        fixture.controller.close()
    }

    @Test
    fun `unknown actual UX prevents output and recovery does not replay that entry`() = runTest {
        val fixture = fixture()
        fixture.inputs = fixture.inputs.copy(vehicle = fixture.inputs.vehicle.copy(ux = VehicleSignal()))
        fixture.update()
        fixture.refreshSignals()
        fixture.update()
        runCurrent()
        assertTrue(fixture.reserved.isEmpty())
        assertTrue(fixture.shown.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun `loss of actual UX dismisses active output`() = runTest {
        val fixture = fixture()
        fixture.update()
        runCurrent()
        fixture.inputs = fixture.inputs.copy(vehicle = fixture.inputs.vehicle.copy(ux = VehicleSignal()))
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.shown.size)
        assertEquals(1, fixture.dismissals)
        fixture.controller.close()
    }

    @Test
    fun `expired active plan is dismissed on owner tick even with refreshed signals`() = runTest {
        val fixture = fixture()
        fixture.update()
        runCurrent()
        val plan = fixture.shown.single()
        advanceTimeBy(plan.expiresAtElapsedMs - fixture.nowElapsedMs())
        // The controller is command-driven; the service owns periodic update/tick delivery.
        fixture.refreshSignals()
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.shown.size)
        assertEquals(1, fixture.dismissals)
        assertEquals(1, fixture.reserved.size)
        fixture.controller.close()
    }

    @Test
    fun `time expires during reservation without another input and late success stays silent`() = runTest {
        val late = LateReservation()
        val fixture = fixture(late::reserve)
        fixture.update()
        runCurrent()
        val plan = fixture.reserved.single()
        advanceTimeBy(plan.expiresAtElapsedMs - fixture.nowElapsedMs())
        late.complete()
        runCurrent()
        assertTrue(fixture.shown.isEmpty())
        assertEquals(1, fixture.reserved.size)
        fixture.controller.close()
    }

    @Test
    fun `failed durable reservation does not fall back to unreserved output or retry same approach`() = runTest {
        val fixture = fixture { false }
        fixture.update()
        runCurrent()
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.reserved.size)
        assertTrue(fixture.shown.isEmpty())
        fixture.controller.close()
    }

    @Test
    fun `school reservation cannot outlive hash location or source invalidation`() = runTest {
        for (change in listOf("hash", "location", "source")) {
            val late = LateReservation()
            val fixture = fixture(late::reserve)
            fixture.useSpatialCards()
            fixture.refreshSpatial(schoolMeters = 500.0)
            fixture.update()
            advanceTimeBy(1)
            fixture.refreshSpatial(schoolMeters = 300.0)
            fixture.update()
            runCurrent()
            assertEquals(CachedEventTag.SCHOOL_ZONE_ENTRY, fixture.reserved.single().candidate.eventTag)
            assertEquals(1, late.pendingCount)

            advanceTimeBy(1)
            when (change) {
                "hash" -> fixture.inputs = fixture.inputs.copy(spatial = fixture.inputs.spatial.copy(
                    schoolZones = fixture.inputs.spatial.schoolZones!!.copy(sha256 = "changed-hash")))
                "location" -> fixture.refreshSpatial(schoolMeters = 500.0)
                "source" -> fixture.refreshSpatial(schoolMeters = 300.0, sourceRevision = 2)
            }
            fixture.update()
            late.complete()
            runCurrent()
            assertTrue(change, fixture.shown.isEmpty())
            assertEquals(1, fixture.reserved.size)
            fixture.controller.close()
        }
    }

    @Test
    fun `destination reservation cannot outlive geo hash or destination change`() = runTest {
        for (change in listOf("hash", "destination")) {
            val late = LateReservation()
            val fixture = fixture(late::reserve)
            fixture.useSpatialCards()
            fixture.refreshSpatial(remainingMeters = 600.0)
            fixture.update()
            advanceTimeBy(1)
            fixture.refreshSpatial(remainingMeters = 500.0)
            fixture.update()
            runCurrent()
            assertEquals(CachedEventTag.PARKING_DESTINATION, fixture.reserved.single().candidate.eventTag)
            assertEquals(1, late.pendingCount)
            if (change == "hash") {
                fixture.inputs = fixture.inputs.copy(spatial = fixture.inputs.spatial.copy(
                    schoolZones = fixture.inputs.spatial.schoolZones!!.copy(sha256 = "changed-hash")))
            } else {
                val changed = NavigationDestination(0.00001, 0.0, 500.0)
                fixture.inputs = fixture.inputs.copy(navigation = fixture.inputs.navigation.copy(
                    destination = NavigationObservation.Fresh(changed, fixture.nowElapsedMs())))
            }
            fixture.update()
            late.complete()
            runCurrent()
            assertTrue(change, fixture.shown.isEmpty())
            assertEquals(1, fixture.reserved.size)
            fixture.controller.close()
        }
    }

    @Test
    fun `simultaneous spatial entries reserve only the higher priority right turn`() = runTest {
        val fixture = fixture()
        fixture.useSpatialCards()
        fixture.refreshSpatial(schoolMeters = 500.0, remainingMeters = 600.0)
        fixture.update()
        advanceTimeBy(1)
        fixture.refreshSpatial(schoolMeters = 300.0, remainingMeters = 500.0, rightTurn = true)
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.reserved.size)
        assertEquals(CachedEventTag.RIGHT_TURN_CROSSWALK, fixture.shown.single().candidate.eventTag)

        advanceTimeBy(30_000)
        fixture.refreshSpatial(schoolMeters = 300.0, remainingMeters = 400.0, rightTurn = true)
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.reserved.size)
        assertEquals(1, fixture.shown.size)
        fixture.controller.close()
    }

    @Test
    fun `spatial entries while voice is busy are consumed without delayed replay`() = runTest {
        val fixture = fixture()
        fixture.useSpatialCards()
        fixture.refreshSpatial(schoolMeters = 500.0, remainingMeters = 600.0, rightTurn = true)
        fixture.update()
        runCurrent()
        assertEquals(CachedEventTag.RIGHT_TURN_CROSSWALK, fixture.shown.single().candidate.eventTag)
        advanceTimeBy(1)
        fixture.refreshSpatial(schoolMeters = 300.0, remainingMeters = 500.0, rightTurn = true)
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.reserved.size)

        advanceTimeBy(30_000)
        fixture.refreshSpatial(schoolMeters = 300.0, remainingMeters = 400.0, rightTurn = true)
        fixture.update()
        runCurrent()
        assertEquals(1, fixture.shown.size)
        assertEquals(1, fixture.reserved.size)
        fixture.controller.close()
    }

    private fun TestScope.fixture(reserve: suspend (GuidancePlan) -> Boolean = { true }): Fixture =
        Fixture(this, { START_ELAPSED_MS + testScheduler.currentTime }, reserve)

    private class Fixture(
        scope: TestScope,
        val nowElapsedMs: () -> Long,
        reservation: suspend (GuidancePlan) -> Boolean,
    ) {
        val reserved = mutableListOf<GuidancePlan>()
        val shown = mutableListOf<GuidancePlan>()
        var dismissals = 0
        var inputs = EventGuidanceInputs(
            vehicle = vehicle(nowElapsedMs()),
            navigation = navigation(nowElapsedMs()),
            cache = LawCacheSnapshot(
                syncToken = "verified-sync",
                lastSuccessfulSyncAtMillis = START_EPOCH_MS,
                cards = listOf(card()),
            ),
            profiles = ProfileSnapshot(listOf(DriverProfile(1, "로컬 운전자")), 1),
            enabled = true,
        )
        val controller = EventGuidanceController(
            ownerScope = scope,
            sessionId = "service-session",
            elapsedRealtimeMs = nowElapsedMs,
            epochMs = { START_EPOCH_MS + nowElapsedMs() - START_ELAPSED_MS },
            todayKorea = { LocalDate.of(2026, 11, 15) },
            reserve = { plan -> reserved += plan; reservation(plan) },
            show = { shown += it },
            dismiss = { dismissals++ },
        )

        fun update() = controller.update(inputs)

        fun refreshSignals(distanceMeters: Double = 140.0) {
            inputs = inputs.copy(
                vehicle = vehicle(nowElapsedMs()),
                navigation = navigation(nowElapsedMs(), distanceMeters).copy(routeRevision = inputs.navigation.routeRevision),
            )
        }

        fun useSpatialCards() {
            inputs = inputs.copy(cache = inputs.cache!!.copy(cards = listOf(
                card(), card().copy(id = 102, eventTag = CachedEventTag.SCHOOL_ZONE_ENTRY),
                card().copy(id = 103, eventTag = CachedEventTag.PARKING_DESTINATION),
            )))
        }

        fun refreshSpatial(
            schoolMeters: Double? = null,
            remainingMeters: Double? = null,
            rightTurn: Boolean = false,
            sourceRevision: Long = 1,
        ) {
            val now = nowElapsedMs()
            val fix = schoolMeters?.let {
                EventLocationObservation(true, Math.toDegrees(it / 6_371_008.8), 0.0, now, now, sourceRevision)
            }
            inputs = inputs.copy(vehicle = vehicle(now),
                navigation = navigation(now).copy(routeRevision = inputs.navigation.routeRevision,
                    turns = NavigationObservation.Fresh(listOf(NavigationTurn(if (rightTurn) "TURN_RIGHT" else "TURN_LEFT", 140.0)), now),
                    destination = remainingMeters?.let { NavigationObservation.Fresh(NavigationDestination(0.0, 0.0, it), now) }
                        ?: NavigationObservation.Unknown),
                spatial = EventSpatialInputs(fix, SchoolZoneSnapshot("fixture", "hash-a", listOf(SchoolZonePoint(0.0, 0.0)))))
        }
    }

    /** Deliberately ignores coroutine cancellation, like an already committed external IO. */
    private class LateReservation {
        private val continuations = mutableListOf<Continuation<Boolean>>()
        val pendingCount: Int get() = continuations.size
        suspend fun reserve(@Suppress("UNUSED_PARAMETER") plan: GuidancePlan): Boolean =
            suspendCoroutine { continuations += it }
        fun complete() = continuations.removeAt(0).resume(true)
    }

    private companion object {
        const val START_ELAPSED_MS = 10_000L
        const val START_EPOCH_MS = 1_800_000_000_000L

        fun vehicle(now: Long) = VehicleSignalSnapshot(
            connection = SignalConnection.Connected,
            ux = VehicleSignal(SignalStatus.Available, VehicleUxState.Restricted, now),
            gear = VehicleSignal(SignalStatus.Available, SelectedGear.Drive, now),
            speedMetersPerSecond = VehicleSignal(SignalStatus.Available, 1f, now, now),
        )

        fun navigation(now: Long, distanceMeters: Double = 140.0) = NavigationSignalSnapshot(
            connection = NavigationConnection.Connected,
            routeActive = NavigationObservation.Fresh(true, now),
            turns = NavigationObservation.Fresh(listOf(NavigationTurn("TURN_RIGHT", distanceMeters)), now),
            routeRevision = 1,
        )

        fun card() = CachedLawCard(
            id = 101,
            version = 7,
            cardType = CachedCardType.SHORT,
            changeKind = CachedChangeKind.LAW,
            audience = CachedAudience.GENERAL,
            eventTag = CachedEventTag.RIGHT_TURN_CROSSWALK,
            sourceOrg = null,
            topic = "test-reviewed-right-turn",
            title = "테스트 검수 카드",
            dontText = null,
            doText = null,
            sceneKey = null,
            ttsText = "검수된 서버 음성 원문입니다.",
            legalBasis = "테스트 근거",
            lawExcerpt = "실제 법령이 아닌 테스트 사본",
            penaltyText = null,
            lawName = "테스트 법령",
            lawNo = null,
            promulgatedOn = null,
            effectiveOn = LocalDate.of(2026, 1, 1),
            exceptions = null,
            decisionJson = null,
            verifiedAtMillis = START_EPOCH_MS,
        )
    }
}
