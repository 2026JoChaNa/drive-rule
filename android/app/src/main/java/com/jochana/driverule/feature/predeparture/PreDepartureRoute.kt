package com.jochana.driverule.feature.predeparture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jochana.driverule.domain.repository.RuleCardRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider

@Composable
fun PreDepartureRoute(
    ruleCardRepository: RuleCardRepository,
    uxRestrictionProvider: UxRestrictionProvider,
    modifier: Modifier = Modifier,
) {
    val factory = remember(ruleCardRepository, uxRestrictionProvider) {
        viewModelFactory {
            initializer {
                PreDepartureViewModel(ruleCardRepository, uxRestrictionProvider)
            }
        }
    }
    val viewModel: PreDepartureViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PreDepartureScreen(uiState = uiState, modifier = modifier)
}
