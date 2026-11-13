package com.jochana.driverule.core.model

data class ProfileSnapshot(
    val profiles: List<DriverProfile>,
    val activeProfileId: Long?,
    val permissionGuidePending: Boolean = false,
) {
    val activeProfile: DriverProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }
}
