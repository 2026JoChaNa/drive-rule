package com.jochana.driverule.app.guidance

import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.guidance.GuidanceEvent
import com.jochana.driverule.domain.vehicle.NavigationSignalSource
import com.jochana.driverule.domain.vehicle.LocationSignalSource
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.domain.vehicle.VehicleSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class VehicleMonitoringStatus { Idle, Starting, Running, Stopping, Stopped, Unavailable, Failed }

/** Process-local signals and display state. Collecting them never starts speech or monitoring. */
class VehicleMonitoringRuntime(
    vehicleSource: VehicleSignalSource,
    navigationSource: NavigationSignalSource,
    private val locationSource: LocationSignalSource,
) {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val vehicle = vehicleSource.state
    val navigation = navigationSource.state
    val location = locationSource.state
    private var locationMonitoring = false
    private val mutableStatus = MutableStateFlow(VehicleMonitoringStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private val mutableVisible = MutableStateFlow(false)
    val appVisible = mutableVisible.asStateFlow()
    private val lifetime = MonitoringLifetime(vehicleSource::start, vehicleSource::stop,
        navigationSource::start, navigationSource::stop)
    var autoStartAttempted = false
    // Process-owned so permission changes in Settings survive Activity recreation.
    var permissionFingerprint: List<Boolean>? = null
    private val mutableSetupReady = MutableStateFlow(false)
    val setupReady = mutableSetupReady.asStateFlow()
    private val mutableBanner = MutableStateFlow<GuidanceEvent?>(null)
    val banner = mutableBanner.asStateFlow()
    private var guidanceOwner: Any? = null
    private var blockGuidance: (() -> Unit)? = null

    val uxProvider: UxRestrictionProvider = object : UxRestrictionProvider {
        override val state = vehicle.map { it.uxState }.stateIn(
            processScope, SharingStarted.Eagerly, VehicleUxState.Unknown,
        )
    }

    fun visible(owner: Any, value: Boolean) {
        lifetime.visible(owner, value)
        mutableVisible.value = lifetime.isVisible
    }
    fun refreshPermissions() {
        blockGuidance?.invoke()
        lifetime.refreshPermissions()
        // Only the service may restart location after rechecking its foreground type and grants.
        setLocationMonitoring(false)
    }
    fun setLocationMonitoring(enabled: Boolean) {
        val allowed = enabled && mutableStatus.value == VehicleMonitoringStatus.Running
        if (locationMonitoring == allowed) return
        locationMonitoring = allowed
        if (allowed) locationSource.start() else locationSource.stop()
    }
    fun setupReady(value: Boolean) {
        mutableSetupReady.value = value
        // STT may start in the next Compose effect, before a StateFlow collector runs.
        if (!value) blockGuidance?.invoke()
    }
    internal fun banner(value: GuidanceEvent?) { mutableBanner.value = value }
    internal fun attachGuidance(owner: Any, block: () -> Unit) {
        check(guidanceOwner == null || guidanceOwner === owner)
        guidanceOwner = owner
        blockGuidance = block
    }
    internal fun detachGuidance(owner: Any) {
        if (guidanceOwner !== owner) return
        blockGuidance = null
        guidanceOwner = null
        mutableBanner.value = null
    }
    fun status(value: VehicleMonitoringStatus) {
        mutableStatus.value = value
        if (value != VehicleMonitoringStatus.Running) {
            blockGuidance?.invoke()
            setLocationMonitoring(false)
        }
        lifetime.background(value == VehicleMonitoringStatus.Running)
    }
}
