package com.jochana.driverule.core.platform.vehicle

import android.content.Context
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import com.jochana.driverule.domain.vehicle.VehicleSignalSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Some non-AAOS VMs verify optional classes before start; keep that failure out of app startup. */
fun createVehicleSignalSource(context: Context): VehicleSignalSource = try {
    AaosVehicleSignalSource(context.applicationContext)
} catch (_: LinkageError) {
    MissingVehicleSource()
} catch (_: RuntimeException) {
    MissingVehicleSource()
}

private class MissingVehicleSource : VehicleSignalSource {
    private val mutableState = MutableStateFlow(VehicleSignalSnapshot())
    override val state = mutableState.asStateFlow()
    override fun start() { mutableState.value = VehicleSignalSnapshot(connection = SignalConnection.Unavailable) }
    override fun stop() { mutableState.value = VehicleSignalSnapshot() }
}
