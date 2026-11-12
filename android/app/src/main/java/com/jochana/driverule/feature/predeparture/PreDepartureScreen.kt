package com.jochana.driverule.feature.predeparture

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jochana.driverule.R

@Composable
fun PreDepartureScreen(
    uiState: PreDepartureUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.predeparture_title),
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        item {
            Text(
                text = stringResource(uiState.messageResource()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState is PreDepartureUiState.Ready) {
            items(items = uiState.cards, key = { "${it.id}:${it.version}" }) { card ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = card.title,
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@StringRes
private fun PreDepartureUiState.messageResource(): Int = when (this) {
    PreDepartureUiState.Loading -> R.string.predeparture_loading
    PreDepartureUiState.VehicleStatusUnavailable -> R.string.predeparture_vehicle_unavailable
    PreDepartureUiState.DrivingRestricted -> R.string.predeparture_driving_restricted
    PreDepartureUiState.CatalogUnavailable -> R.string.predeparture_catalog_unavailable
    PreDepartureUiState.Empty -> R.string.predeparture_catalog_empty
    is PreDepartureUiState.Ready -> R.string.predeparture_catalog_ready
}
