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
import com.jochana.driverule.domain.vehicle.NavigationTurn
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.SignalStatus
import com.jochana.driverule.domain.vehicle.VehicleSignal
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import java.time.LocalDate

internal const val EVENT_NOW = 100_000L
internal const val EVENT_EPOCH = 1_800_000_000_000L
internal val EVENT_DAY: LocalDate = LocalDate.of(2026, 11, 15)
internal val EVENT_SCOPE = EventScope("session-one", "route-one")

internal fun eventVehicle(now: Long = EVENT_NOW, speed: Float = 0f, gear: SelectedGear = SelectedGear.Drive) =
    VehicleSignalSnapshot(connection = SignalConnection.Connected,
        gear = VehicleSignal(SignalStatus.Available, gear, 0),
        speedMetersPerSecond = VehicleSignal(SignalStatus.Available, speed, now, now))

internal fun eventNavigation(
    now: Long = EVENT_NOW,
    distance: Double = 100.0,
    type: String = "TURN_RIGHT",
) = NavigationSignalSnapshot(connection = NavigationConnection.Connected,
    routeActive = NavigationObservation.Fresh(true, now),
    turns = NavigationObservation.Fresh(listOf(NavigationTurn(type, distance)), now))

internal fun eventCard(id: Long = 1, tts: String = "검증용 문장") = CachedLawCard(
    id = id, version = 1, cardType = CachedCardType.SHORT, changeKind = CachedChangeKind.LAW,
    audience = CachedAudience.GENERAL, eventTag = CachedEventTag.RIGHT_TURN_CROSSWALK,
    sourceOrg = null, topic = "fixture", title = "법령이 아닌 테스트 자료", dontText = null,
    doText = null, sceneKey = null, ttsText = tts, legalBasis = "test", lawExcerpt = "test",
    penaltyText = null, lawName = null, lawNo = null, promulgatedOn = null,
    effectiveOn = EVENT_DAY, exceptions = null, decisionJson = null, verifiedAtMillis = EVENT_EPOCH - 1_000,
)

internal fun eventCache(vararg cards: CachedLawCard) = LawCacheSnapshot(
    lastSuccessfulSyncAtMillis = EVENT_EPOCH, cards = cards.toList(),
)

internal fun eventDetection(
    now: Long = EVENT_NOW,
    distance: Double = 100.0,
    speed: Float = 0f,
) = DrivingEventDetector().evaluate(EVENT_SCOPE, RightTurnEncounterState(), eventVehicle(now, speed),
    eventNavigation(now, distance), now)
