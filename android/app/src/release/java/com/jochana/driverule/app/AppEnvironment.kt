package com.jochana.driverule.app

import com.jochana.driverule.core.platform.vehicle.UnavailableUxRestrictionProvider
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.feature.home.HomePreviewEntry

/** A verified vehicle adapter is required before release can expose parked interaction. */
object AppEnvironment {
    fun createUxProvider(): UxRestrictionProvider = UnavailableUxRestrictionProvider()
    fun previewEntries(): List<HomePreviewEntry> = emptyList()
}
