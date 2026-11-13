package com.jochana.driverule.feature.home

/** Presentation-only sample item. It is deliberately not the backend card DTO. */
data class HomePreviewEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: HomePreviewStatus,
    val icon: HomePreviewIcon,
)

enum class HomePreviewStatus { Active, Scheduled }

enum class HomePreviewIcon { Parking, Crosswalk, Turn, Camera }
