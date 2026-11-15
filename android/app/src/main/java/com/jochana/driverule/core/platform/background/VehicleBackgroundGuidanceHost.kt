package com.jochana.driverule.core.platform.background

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jochana.driverule.DriveRuleApplication
import com.jochana.driverule.R
import com.jochana.driverule.app.BackgroundGuidanceHost
import com.jochana.driverule.app.guidance.VehicleMonitoringStatus
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.feature.home.GuidanceBanner
import com.jochana.driverule.ui.components.DriveRuleButton
import com.jochana.driverule.ui.theme.LocalDriveRuleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Activity commands start a service; Compose only renders its state. */
class VehicleBackgroundGuidanceHost(private val activity: ComponentActivity) : BackgroundGuidanceHost {
    private val runtime = (activity.application as DriveRuleApplication).appContainer.vehicleMonitoring
    private val owner = Any()
    private var visible = false
    private var idleRestartJob: Job? = null
    private var permissionResult: (() -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        runtime.permissionFingerprint = permissionState()
        val complete = permissionResult
        permissionResult = null
        // Finish the guide against the current genuine UX state before reconnection temporarily
        // changes it to Unknown. A real restriction change still fails the UI's completion guard.
        complete?.invoke()
        runtime.refreshPermissions()
        refreshVisibleLocation()
    }

    init {
        // onStart and permission results can precede RESUMED after returning from Settings.
        // A one-shot visible lifecycle event may promote location; the background timer may not.
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshVisibleLocation()
        })
    }

    override fun onVisible() {
        idleRestartJob?.cancel()
        idleRestartJob = null
        visible = true
        runtime.visible(owner, true)
        val current = permissionState()
        if (permissionResult == null && runtime.permissionFingerprint != null && runtime.permissionFingerprint != current)
            runtime.refreshPermissions()
        runtime.permissionFingerprint = current
        if (runtime.status.value == VehicleMonitoringStatus.Stopping) {
            // A foreground return can precede the idle service's onDestroy. Wait once for that
            // instance to finish; this Activity owns the wait and never retries a user/OS stop.
            idleRestartJob = activity.lifecycleScope.launch {
                val finished = runtime.status.first { it != VehicleMonitoringStatus.Stopping }
                if (finished == VehicleMonitoringStatus.Idle && visible &&
                    !VehicleMonitoringControl.stoppedByUser(activity)) start()
            }
            return
        }
        if (runtime.autoStartAttempted && runtime.status.value != VehicleMonitoringStatus.Idle) {
            refreshVisibleLocation()
            return
        }
        runtime.autoStartAttempted = true
        if (VehicleMonitoringControl.stoppedByUser(activity)) runtime.status(VehicleMonitoringStatus.Stopped)
        else start()
    }

    override fun onHidden() {
        visible = false
        idleRestartJob?.cancel()
        idleRestartJob = null
        runtime.visible(owner, false)
    }

    override fun onSpeechAllowed(allowed: Boolean) = runtime.setupReady(allowed)

    override fun requestPermissions(onComplete: () -> Unit) {
        if (!canInteract() || permissionResult != null) { onComplete(); return }
        val missing = permissions().filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
                isDangerous(permission)
        }
        if (missing.isEmpty()) {
            onComplete()
            runtime.refreshPermissions()
            refreshVisibleLocation()
            return
        }
        permissionResult = onComplete
        // Android requires coarse + fine in the same request when upgrading approximate access.
        val requested = if (Manifest.permission.ACCESS_FINE_LOCATION in missing)
            (missing + Manifest.permission.ACCESS_COARSE_LOCATION).distinct() else missing
        try { launcher.launch(requested.toTypedArray()) }
        catch (_: RuntimeException) { permissionResult = null; onComplete() }
    }

    private fun refreshVisibleLocation() {
        if (!visible || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            runtime.status.value != VehicleMonitoringStatus.Running) return
        try {
            activity.startService(Intent(activity, VehicleMonitoringService::class.java)
                .setAction(VehicleMonitoringService.ACTION_REFRESH_LOCATION))
        } catch (_: RuntimeException) { /* the next visible entry may retry; no background restart */ }
    }

    private fun start() {
        if (!visible || runtime.status.value in setOf(VehicleMonitoringStatus.Starting,
                VehicleMonitoringStatus.Running, VehicleMonitoringStatus.Stopping)) return
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            runtime.status(VehicleMonitoringStatus.Unavailable)
            return
        }
        VehicleMonitoringControl.enabled(activity)
        runtime.status(VehicleMonitoringStatus.Starting)
        try { ContextCompat.startForegroundService(activity, Intent(activity, VehicleMonitoringService::class.java)) }
        catch (_: RuntimeException) { runtime.status(VehicleMonitoringStatus.Failed) }
    }

    private fun canInteract() = visible && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        runtime.vehicle.value.uxState == VehicleUxState.Unrestricted

    private fun permissions() = buildList {
        add("android.car.permission.CAR_SPEED")
        add(GuidanceLocationAccess.PLEOS_PERMISSION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        // Inspect protection on the actual device; standard AAOS POWERTRAIN is normal.
        add("android.car.permission.CAR_POWERTRAIN")
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun permissionState() = permissions().map {
        ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    } + GuidanceLocationAccess.available(activity)

    private fun isDangerous(permission: String) = try {
        (activity.packageManager.getPermissionInfo(permission, 0).protectionLevel and
            PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
    } catch (_: PackageManager.NameNotFoundException) { false }

    @Composable override fun Controls() {
        val status by runtime.status.collectAsStateWithLifecycle()
        val vehicle by runtime.vehicle.collectAsStateWithLifecycle()
        val navigation by runtime.navigation.collectAsStateWithLifecycle()
        val location by runtime.location.collectAsStateWithLifecycle()
        val colors = LocalDriveRuleColors.current
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vehicle_monitor_status, status.label()), color = colors.textPrimary)
            // Connection is distinct from an available value, a detected situation or heard guidance.
            Text(stringResource(R.string.vehicle_monitor_sources, connectionLabel(vehicle.connection.name),
                connectionLabel(navigation.connection.name)),
                color = colors.textSecondary)
            Text(stringResource(R.string.vehicle_monitor_location, connectionLabel(location.connection.name)),
                color = colors.textSecondary)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DriveRuleButton(stringResource(if (status == VehicleMonitoringStatus.Running)
                    R.string.vehicle_monitor_stop else R.string.vehicle_monitor_start), onClick = {
                    if (canInteract()) {
                        if (status == VehicleMonitoringStatus.Running) VehicleMonitoringControl.stop(activity) else start()
                    }
                })
                DriveRuleButton(stringResource(R.string.vehicle_monitor_permissions),
                    onClick = { requestPermissions {} }, outlined = true)
                DriveRuleButton(stringResource(R.string.vehicle_monitor_settings), onClick = {
                    if (canInteract()) try {
                        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", activity.packageName, null)))
                    } catch (_: RuntimeException) {
                        Toast.makeText(activity, R.string.vehicle_monitor_settings_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }, outlined = true)
            }
        }
    }

    @Composable override fun Banner(modifier: Modifier) {
        val event by runtime.banner.collectAsStateWithLifecycle()
        event?.let { GuidanceBanner(it.title, it.message, modifier, it.contextLabel) }
    }

    @Composable private fun VehicleMonitoringStatus.label(): String = stringResource(when (this) {
        VehicleMonitoringStatus.Idle -> R.string.vehicle_monitor_idle
        VehicleMonitoringStatus.Starting -> R.string.vehicle_monitor_starting
        VehicleMonitoringStatus.Running -> R.string.vehicle_monitor_running
        VehicleMonitoringStatus.Stopping -> R.string.vehicle_monitor_stopping
        VehicleMonitoringStatus.Stopped -> R.string.vehicle_monitor_stopped
        VehicleMonitoringStatus.Unavailable -> R.string.vehicle_monitor_unavailable
        VehicleMonitoringStatus.Failed -> R.string.vehicle_monitor_failed
    })

    @Composable private fun connectionLabel(name: String): String = stringResource(when (name) {
        "Connected" -> R.string.vehicle_source_connected
        "Connecting" -> R.string.vehicle_source_connecting
        "PermissionDenied" -> R.string.vehicle_source_permission
        "Stopped" -> R.string.vehicle_source_stopped
        else -> R.string.vehicle_source_unavailable
    })
}
