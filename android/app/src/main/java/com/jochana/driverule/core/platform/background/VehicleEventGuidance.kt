package com.jochana.driverule.core.platform.background

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.jochana.driverule.R
import com.jochana.driverule.app.AppContainer
import com.jochana.driverule.app.guidance.EventGuidanceController
import com.jochana.driverule.app.guidance.EventGuidanceInputs
import com.jochana.driverule.app.guidance.GuidanceCoordinator
import com.jochana.driverule.app.guidance.VehicleMonitoringStatus
import com.jochana.driverule.core.model.CachedChangeKind
import com.jochana.driverule.core.model.LawCacheSnapshot
import com.jochana.driverule.core.model.ProfileSnapshot
import com.jochana.driverule.core.model.SchoolZoneSnapshot
import com.jochana.driverule.domain.event.EventSpatialInputs
import com.jochana.driverule.domain.event.EventLocationObservation
import com.jochana.driverule.domain.event.EventCandidateProof
import com.jochana.driverule.domain.vehicle.LocationConnection
import com.jochana.driverule.domain.vehicle.LocationObservation
import com.jochana.driverule.core.platform.audio.PleosGuidanceSpeechOutput
import com.jochana.driverule.domain.event.GuidanceEffectiveStage
import com.jochana.driverule.domain.event.GuidancePlan
import com.jochana.driverule.domain.guidance.GuidanceAttemptRequest
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.guidance.GuidanceSpeechOutput
import com.jochana.driverule.domain.guidance.GuidanceSpeechResult
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One service instance owns detection, attempts, timers and audio. No UI or network side effects. */
internal class VehicleEventGuidance(
    private val context: Context,
    private val container: AppContainer,
    serviceScope: CoroutineScope,
) {
    private val scope = CoroutineScope(serviceScope.coroutineContext + SupervisorJob(serviceScope.coroutineContext[Job]))
    private val runtime = container.vehicleMonitoring
    private val notifications = GuidanceEventNotifications(context)
    private val router = GuidanceOutputRouter(SystemClock::elapsedRealtime, runtime::banner,
        notifications::post, notifications::dismiss, initiallyVisible = runtime.appVisible.value)
    private val adapter = PleosGuidanceSpeechOutput(context.applicationContext)
    private val speech = object : GuidanceSpeechOutput {
        override suspend fun speak(event: GuidanceEvent): GuidanceSpeechResult =
            if (runtime.status.value != VehicleMonitoringStatus.Running || !runtime.setupReady.value)
                GuidanceSpeechResult.Unavailable else adapter.speak(event)
        override fun stop() = adapter.stop()
        override fun close() = adapter.close()
    }
    private val coordinator = GuidanceCoordinator(scope, router, SystemClock::elapsedRealtime,
        Dispatchers.Main.immediate, speechOutput = speech)
    private val sessionId = UUID.randomUUID().toString()
    // BOOT_COUNT is an ordering marker kept inside the vehicle, never sent or logged.
    private val bootId = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toString()
    } catch (_: RuntimeException) { sessionId }
      catch (_: Settings.SettingNotFoundException) { sessionId }
    private var cache: LawCacheSnapshot? = null
    private var profiles: ProfileSnapshot? = null
    private var schoolZones: SchoolZoneSnapshot? = null
    private var closed = false
    private val controller = EventGuidanceController(scope, sessionId, SystemClock::elapsedRealtime,
        System::currentTimeMillis,
        { Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.of("Asia/Seoul")).toLocalDate() },
        reserve = { plan ->
            val accepted = container.guidanceAttemptLedger.reserve(GuidanceAttemptRequest(plan.profileId, plan.card.id,
                plan.effectiveStage.name, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootId))
            if (accepted) {
                // Database writes can precede their Flow emission. Read again after reservation,
                // then let the controller check these snapshots plus current platform signals.
                try {
                    cache = container.ruleCardRepository.readCache()
                    profiles = container.driverProfileRepository.observeProfiles().first()
                    if (plan.candidate.proof != EventCandidateProof.RightTurn)
                        schoolZones = container.schoolZoneRepository.read()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    cache = null
                    profiles = null
                    schoolZones = null
                }
                update()
            }
            accepted
        },
        show = { plan ->
            coordinator.start()
            coordinator.submit(GuidanceEvent(plan.candidate.key.stableId(), plan.card.title,
                plan.card.ttsText, plan.expiresAtElapsedMs, speechText = plan.card.ttsText,
                contextLabel = contextLabel(plan)))
        },
        dismiss = coordinator::stop,
    )

    init {
        // A system channel failure cannot disable an otherwise usable voice path.
        try { notifications.createChannel() } catch (_: RuntimeException) { /* voice remains independent */ }
        runtime.attachGuidance(this, controller::block)
        scope.launch {
            container.ruleCardRepository.observeCache().map<LawCacheSnapshot, LawCacheSnapshot?> { it }
                .catch { if (it is CancellationException) throw it else emit(null) }
                .collect { cache = it; update() }
        }
        scope.launch {
            container.driverProfileRepository.observeProfiles().map<ProfileSnapshot, ProfileSnapshot?> { it }
                .catch { if (it is CancellationException) throw it else emit(null) }
                .collect { profiles = it; update() }
        }
        scope.launch {
            combine(runtime.vehicle, runtime.navigation, runtime.setupReady, runtime.status, runtime.location) { _, _, _, _, _ -> Unit }
                .collect { update() }
        }
        scope.launch {
            container.schoolZoneRepository.observe()
                .catch { if (it is CancellationException) throw it else emit(null) }
                .collect { schoolZones = it; update() }
        }
        scope.launch { runtime.appVisible.collect(router::setVisible) }
        // Re-evaluate deadlines even when SDK stops sending. Never refresh observation ages here.
        scope.launch { while (!closed) { delay(250); update() } }
    }

    private fun update() {
        if (closed) return
        val location = runtime.location.value
        val point = location.position as? LocationObservation.Fresh
        val spatial = EventSpatialInputs(location = point?.let {
            EventLocationObservation(location.connection == LocationConnection.Connected,
                it.value.latitude, it.value.longitude, it.observedElapsedMs,
                it.value.rawSourceTimestampMillis, location.sourceRevision)
        }, schoolZones = schoolZones)
        controller.update(EventGuidanceInputs(runtime.vehicle.value, runtime.navigation.value, cache, profiles,
            runtime.status.value == VehicleMonitoringStatus.Running && runtime.setupReady.value, spatial))
    }

    fun close() {
        if (closed) return
        closed = true
        controller.close()
        runtime.detachGuidance(this)
        coordinator.close()
        router.dismiss()
        scope.cancel()
    }

    private fun contextLabel(plan: GuidancePlan): String = if (plan.card.changeKind == CachedChangeKind.OPERATION) {
        context.getString(R.string.guidance_context_operation, plan.card.sourceOrg.orEmpty())
    } else when (plan.effectiveStage) {
        GuidanceEffectiveStage.Current -> context.getString(R.string.guidance_context_current, plan.card.effectiveOn.toString())
        GuidanceEffectiveStage.Upcoming -> context.getString(R.string.guidance_context_upcoming, plan.card.effectiveOn.toString())
        GuidanceEffectiveStage.Unspecified -> context.getString(R.string.guidance_context_unknown)
    }
}
