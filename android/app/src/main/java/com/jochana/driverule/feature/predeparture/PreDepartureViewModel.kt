package com.jochana.driverule.feature.predeparture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jochana.driverule.core.model.CardCatalog
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.repository.RuleCardRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class PreDepartureViewModel(
    ruleCardRepository: RuleCardRepository,
    uxRestrictionProvider: UxRestrictionProvider,
) : ViewModel() {
    val uiState: StateFlow<PreDepartureUiState> = combine(
        ruleCardRepository.observeCatalog(),
        uxRestrictionProvider.state,
    ) { catalog, uxState ->
        when (uxState) {
            VehicleUxState.Unknown -> PreDepartureUiState.VehicleStatusUnavailable
            VehicleUxState.Restricted -> PreDepartureUiState.DrivingRestricted
            VehicleUxState.Unrestricted -> when (catalog) {
                CardCatalog.Unavailable -> PreDepartureUiState.CatalogUnavailable
                is CardCatalog.Available -> if (catalog.cards.isEmpty()) {
                    PreDepartureUiState.Empty
                } else {
                    PreDepartureUiState.Ready(catalog.cards.toList())
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000,
            replayExpirationMillis = 0,
        ),
        initialValue = PreDepartureUiState.Loading,
    )
}
