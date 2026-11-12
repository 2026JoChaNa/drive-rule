package com.jochana.driverule.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jochana.driverule.feature.predeparture.PreDepartureRoute
import com.jochana.driverule.ui.theme.DriveRuleTheme

@Composable
fun DriveRuleApp(appContainer: AppContainer) {
    DriveRuleTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
            PreDepartureRoute(
                ruleCardRepository = appContainer.ruleCardRepository,
                uxRestrictionProvider = appContainer.uxRestrictionProvider,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}
