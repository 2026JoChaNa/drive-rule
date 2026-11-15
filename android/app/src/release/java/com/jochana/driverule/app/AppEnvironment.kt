package com.jochana.driverule.app

import android.content.Intent
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.jochana.driverule.core.platform.background.VehicleBackgroundGuidanceHost
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.feature.card.CardDetailPreview
import com.jochana.driverule.feature.changes.RuleChangePreview
import com.jochana.driverule.feature.home.HomePreviewEntry

/** Actual platform state only. Unknown/restricted states keep detailed interaction locked. */
object AppEnvironment {

    val cardOpenRequests: Flow<String> = emptyFlow()
    val sampleLabelsVisible: Boolean = true
    @Suppress("UNUSED_PARAMETER")
    fun handleLaunchIntent(activity: ComponentActivity, intent: Intent?) = Unit

    fun createBackgroundHost(activity: ComponentActivity): BackgroundGuidanceHost = VehicleBackgroundGuidanceHost(activity)

    fun createUxProvider(actual: UxRestrictionProvider): UxRestrictionProvider = actual
    fun previewEntries(): List<HomePreviewEntry> = emptyList()
    fun cardDetails(): Map<String, CardDetailPreview> = emptyMap()
    fun ruleChanges(): List<RuleChangePreview> = emptyList()
}
