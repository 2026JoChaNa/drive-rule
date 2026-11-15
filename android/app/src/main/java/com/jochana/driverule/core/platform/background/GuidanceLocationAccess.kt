package com.jochana.driverule.core.platform.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/** Pleos access and Android foreground-location access are separate requirements. */
internal object GuidanceLocationAccess {
    const val PLEOS_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

    fun available(context: Context): Boolean = try {
        fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        granted(PLEOS_PERMISSION) &&
            (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) &&
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
    } catch (_: RuntimeException) { false }
}
