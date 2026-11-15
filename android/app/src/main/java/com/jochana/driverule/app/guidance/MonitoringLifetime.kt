package com.jochana.driverule.app.guidance

/** Main-thread ownership. Activity recreation and UI observation do not create new sessions. */
internal class MonitoringLifetime(
    private val startVehicle: () -> Unit,
    private val stopVehicle: () -> Unit,
    private val startNavigation: () -> Unit,
    private val stopNavigation: () -> Unit,
) {
    private val visibleOwners = mutableSetOf<Any>()
    private var background = false
    private var vehicleStarted = false
    private var navigationStarted = false
    val isVisible: Boolean get() = visibleOwners.isNotEmpty()

    fun visible(owner: Any, visible: Boolean) {
        if (visible) visibleOwners.add(owner) else visibleOwners.remove(owner)
        reconcile()
    }

    fun background(active: Boolean) {
        background = active
        reconcile()
    }

    fun refreshPermissions() {
        if (vehicleStarted) { stopVehicle(); startVehicle() }
        if (navigationStarted) { stopNavigation(); startNavigation() }
    }

    private fun reconcile() {
        val needsVehicle = background || visibleOwners.isNotEmpty()
        if (needsVehicle && !vehicleStarted) {
            vehicleStarted = true
            startVehicle()
        }
        if (background && !navigationStarted) {
            navigationStarted = true
            startNavigation()
        }
        if (!background && navigationStarted) {
            navigationStarted = false
            stopNavigation()
        }
        if (!needsVehicle && vehicleStarted) {
            vehicleStarted = false
            stopVehicle()
        }
    }
}
