package com.jochana.driverule.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.feature.home.HomePreviewEntry
import com.jochana.driverule.feature.home.HomePreviewIcon
import com.jochana.driverule.feature.home.HomePreviewStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI fixtures only: never packaged in release and never recorded as actual heard guidance. */
object AppEnvironment {

    fun createBackgroundHost(activity: ComponentActivity): BackgroundGuidanceHost =
        com.jochana.driverule.core.platform.background.SampleBackgroundGuidanceHost(activity)

    fun createUxProvider(): UxRestrictionProvider = object : UxRestrictionProvider {
        override val state = MutableStateFlow(VehicleUxState.Unrestricted).asStateFlow()
    }

    fun previewEntries(): List<HomePreviewEntry> = listOf(
        HomePreviewEntry("sample-parking", "어린이보호구역 주정차 금지", "주정차 · 오늘 18:21", HomePreviewStatus.Active, HomePreviewIcon.Parking),
        HomePreviewEntry("sample-crosswalk", "보호구역 횡단보도 일시정지", "어린이보호구역 진입 · 오늘 18:16", HomePreviewStatus.Active, HomePreviewIcon.Crosswalk),
        HomePreviewEntry("sample-turn", "우회전 후 횡단보도 일시정지", "우회전 후 횡단보도 · 오늘 08:05", HomePreviewStatus.Active, HomePreviewIcon.Turn),
        HomePreviewEntry("sample-camera", "고속도로 정차·주차 무인단속", "주정차 · 어제 19:40", HomePreviewStatus.Scheduled, HomePreviewIcon.Camera),
    )
}
