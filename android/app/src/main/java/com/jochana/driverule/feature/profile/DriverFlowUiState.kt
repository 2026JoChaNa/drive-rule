package com.jochana.driverule.feature.profile

import com.jochana.driverule.core.model.DriverProfile
import com.jochana.driverule.core.model.ThemeMode
import com.jochana.driverule.core.model.VehicleUxState

data class DriverFlowUiState(
    val loading: Boolean = true,
    val storageError: Boolean = false,
    val vehicleUxState: VehicleUxState = VehicleUxState.Unknown,
    val profiles: List<DriverProfile> = emptyList(),
    val activeProfile: DriverProfile? = null,
    val permissionGuidePending: Boolean = false,
    val addingProfile: Boolean = false,
    val name: String = "",
    val saving: Boolean = false,
    val error: ProfileInputError? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val themeError: Boolean = false,
) {
    val interactionAllowed: Boolean
        get() = !loading && !storageError && vehicleUxState == VehicleUxState.Unrestricted
}

enum class ProfileInputError { Empty, TooLong, LimitReached, NotFound, Storage }
