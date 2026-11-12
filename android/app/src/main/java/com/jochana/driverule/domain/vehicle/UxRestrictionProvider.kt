package com.jochana.driverule.domain.vehicle

import com.jochana.driverule.core.model.VehicleUxState
import kotlinx.coroutines.flow.StateFlow

interface UxRestrictionProvider {
    val state: StateFlow<VehicleUxState>
}
