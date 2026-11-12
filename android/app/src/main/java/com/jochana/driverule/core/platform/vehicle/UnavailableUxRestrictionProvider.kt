package com.jochana.driverule.core.platform.vehicle

import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No vehicle integration is connected, so interaction restrictions stay unknown. */
class UnavailableUxRestrictionProvider : UxRestrictionProvider {
    override val state: StateFlow<VehicleUxState> =
        MutableStateFlow(VehicleUxState.Unknown).asStateFlow()
}
