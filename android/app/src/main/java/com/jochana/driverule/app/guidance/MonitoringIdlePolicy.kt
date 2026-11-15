package com.jochana.driverule.app.guidance

import com.jochana.driverule.domain.vehicle.IgnitionState
import com.jochana.driverule.domain.vehicle.NavigationConnection
import com.jochana.driverule.domain.vehicle.NavigationObservation
import com.jochana.driverule.domain.vehicle.NavigationSignalSnapshot
import com.jochana.driverule.domain.vehicle.SelectedGear
import com.jochana.driverule.domain.vehicle.SignalConnection
import com.jochana.driverule.domain.vehicle.VehicleSignalSnapshot
import kotlin.math.abs

/** Resource-lifetime policy only: never a trip/event detector or permission to interact. */
internal class MonitoringIdlePolicy(private val graceMs: Long = 30_000L) {
    private var idleSince: Long? = null
    init { require(graceMs > 0) }

    fun shouldStop(nowElapsedMs: Long, visible: Boolean, vehicle: VehicleSignalSnapshot,
        navigation: NavigationSignalSnapshot): Boolean {
        val carConnected = vehicle.connection == SignalConnection.Connected
        val poweredOff = carConnected && vehicle.ignition.availableValue in setOf(IgnitionState.Off, IgnitionState.Lock)
        val gear = if (carConnected) vehicle.gear.availableValue else null
        val engagedGear = gear != null && gear != SelectedGear.Park && gear != SelectedGear.Neutral
        val moving = carConnected && vehicle.speedMetersPerSecond.availableValue?.let { abs(it) > 0.1f } == true
        val navigating = navigation.connection == NavigationConnection.Connected &&
            (navigation.routeActive as? NavigationObservation.Fresh)?.value == true
        if (visible || (!poweredOff && (engagedGear || moving || navigating))) {
            idleSince = null
            return false
        }
        val since = idleSince
        if (since == null || nowElapsedMs < since) {
            idleSince = nowElapsedMs
            return false
        }
        return nowElapsedMs - since >= graceMs
    }
}
