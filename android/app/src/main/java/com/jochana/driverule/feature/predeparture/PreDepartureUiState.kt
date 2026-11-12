package com.jochana.driverule.feature.predeparture

import com.jochana.driverule.core.model.RuleCardSummary

sealed interface PreDepartureUiState {
    data object Loading : PreDepartureUiState
    data object VehicleStatusUnavailable : PreDepartureUiState
    data object DrivingRestricted : PreDepartureUiState
    data object CatalogUnavailable : PreDepartureUiState
    data object Empty : PreDepartureUiState
    data class Ready(val cards: List<RuleCardSummary>) : PreDepartureUiState
}
