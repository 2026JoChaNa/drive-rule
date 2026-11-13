package com.jochana.driverule.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

import com.jochana.driverule.core.platform.vehicle.UnavailableUxRestrictionProvider
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.feature.home.HomePreviewEntry

/** A verified vehicle adapter is required before release can expose parked interaction. */
object AppEnvironment {

    fun createBackgroundHost(activity: ComponentActivity): BackgroundGuidanceHost = object : BackgroundGuidanceHost {
        override fun onVisible() = Unit
        override fun onHidden() = Unit
        @Composable override fun Controls() = Unit
    }

    fun createUxProvider(): UxRestrictionProvider = UnavailableUxRestrictionProvider()
    fun previewEntries(): List<HomePreviewEntry> = emptyList()
}
