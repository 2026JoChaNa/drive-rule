package com.jochana.driverule.app.guidance

import com.jochana.driverule.core.model.LawCacheSnapshot
import com.jochana.driverule.core.model.ProfileSnapshot
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.event.DrivingEventDetector
import com.jochana.driverule.domain.event.EventScope
import com.jochana.driverule.domain.event.GuidancePlan
import com.jochana.driverule.domain.event.GuidancePlanPolicy
import com.jochana.driverule.domain.event.PlanValidationPhase
import com.jochana.driverule.domain.event.DrivingEncounterState
import com.jochana.driverule.domain.event.EventSpatialInputs
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class EventGuidanceInputs(
    val vehicle: VehicleSignalSnapshot,
    val navigation: NavigationSignalSnapshot,
    val cache: LawCacheSnapshot?,
    val profiles: ProfileSnapshot?,
    val enabled: Boolean,
    val spatial: EventSpatialInputs = EventSpatialInputs(),
)

/**
 * Main-dispatcher commands owned by the monitoring service, independent of UI collection.
 * No backlog: a blocked/failed/busy approach is consumed, never replayed after a gate opens.
 * Durable reservation precedes output. Cancellation cannot roll that reservation back.
 */
class EventGuidanceController(
    private val ownerScope: CoroutineScope,
    private val sessionId: String,
    private val elapsedRealtimeMs: () -> Long,
    private val epochMs: () -> Long,
    private val todayKorea: () -> LocalDate,
    private val reserve: suspend (GuidancePlan) -> Boolean,
    private val show: (GuidancePlan) -> Unit,
    private val dismiss: () -> Unit,
    private val detector: DrivingEventDetector = DrivingEventDetector(),
    private val policy: GuidancePlanPolicy = GuidancePlanPolicy(),
) {
    private var latest: EventGuidanceInputs? = null
    private var encounter = DrivingEncounterState()
    private var pending: Job? = null
    private var selected: GuidancePlan? = null
    private var outputStarted = false
    private var generation = 0L
    private var closed = false

    fun update(inputs: EventGuidanceInputs) {
        if (closed) return
        latest = inputs
        val scope = eventScope(inputs)
        val detection = detector.evaluate(scope, encounter, inputs.vehicle, inputs.navigation, elapsedRealtimeMs(), inputs.spatial)
        encounter = detection.state
        selected?.let {
            if (!valid(it, inputs, if (outputStarted) PlanValidationPhase.InProgress else PlanValidationPhase.BeforeStart))
                cancelActive()
        }
        // Always advance detection first, even when setup, cache or a previous output blocks it.
        if (selected != null || !ready(inputs)) return
        val cache = inputs.cache ?: return
        val plan = policy.select(detection.candidates, cache, inputs.profiles?.activeProfileId,
            todayKorea(), elapsedRealtimeMs(), epochMs()) ?: return
        selected = plan
        val token = ++generation
        val job = ownerScope.launch(start = CoroutineStart.LAZY) {
            val accepted = try {
                reserve(plan)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            // IO may finish after stop, route/profile changes or retirement. Never release an
            // uncertain reservation or emit from an obsolete coroutine, even if it ignores cancel.
            if (closed || generation != token || selected != plan) return@launch
            pending = null
            val current = latest
            if (!accepted || current == null || !valid(plan, current, PlanValidationPhase.BeforeStart)) {
                cancelActive()
                return@launch
            }
            outputStarted = true
            show(plan)
        }
        pending = job
        job.start()
    }

    /** Setup/STT and explicit service stop call this synchronously, before async collectors. */
    fun block() {
        latest = latest?.copy(enabled = false)
        cancelActive()
    }

    fun close() {
        if (closed) return
        closed = true
        cancelActive()
        latest = null
    }

    private fun ready(inputs: EventGuidanceInputs): Boolean = inputs.enabled &&
        inputs.vehicle.uxState != VehicleUxState.Unknown &&
        inputs.profiles?.activeProfile != null && inputs.profiles.permissionGuidePending == false

    private fun eventScope(inputs: EventGuidanceInputs) = EventScope(sessionId, inputs.navigation.routeRevision.toString())

    private fun valid(plan: GuidancePlan, inputs: EventGuidanceInputs, phase: PlanValidationPhase): Boolean =
        ready(inputs) && inputs.cache?.let { cache ->
            policy.isValid(plan, eventScope(inputs), encounter, inputs.vehicle, inputs.navigation, cache,
                inputs.profiles?.activeProfileId, todayKorea(), elapsedRealtimeMs(), epochMs(), phase, inputs.spatial)
        } == true

    private fun cancelActive() {
        generation++
        pending?.cancel()
        pending = null
        selected = null
        outputStarted = false
        dismiss()
    }
}
