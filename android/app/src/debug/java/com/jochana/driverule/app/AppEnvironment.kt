package com.jochana.driverule.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.feature.card.CardDetailPreview
import com.jochana.driverule.feature.card.CardEventTag
import com.jochana.driverule.feature.card.CardEvidencePreview
import com.jochana.driverule.feature.card.CardScene
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

    /**
     * Figma A-1 · B-1 · H-1 · I-1 sample texts, keyed by [previewEntries] id. Shown with a sample
     * label; they are design copy, not reviewed published cards, and carry no server card id.
     */
    fun cardDetails(): Map<String, CardDetailPreview> = listOf(
        CardDetailPreview(
            id = "sample-parking",
            status = HomePreviewStatus.Active,
            tag = CardEventTag.Parking,
            message = "어린이보호구역은\n주정차가 금지됩니다",
            scene = CardScene.SchoolZoneDestination,
            legend = "반경 300m",
            evidence = CardEvidencePreview(
                law = "도로교통법",
                dates = "2021.10.21 시행",
                penalty = "승용차 과태료 12만 원 (08~20시, 일반도로의 3배)",
            ),
        ),
        CardDetailPreview(
            id = "sample-crosswalk",
            status = HomePreviewStatus.Active,
            tag = CardEventTag.SchoolZoneEntry,
            message = "신호등 없는 횡단보도는\n사람이 없어도 멈춥니다",
            scene = CardScene.UnsignalizedCrosswalk,
            evidence = CardEvidencePreview(
                law = "도로교통법 제27조",
                dates = "2022.7.12 시행",
                penalty = "승용차 범칙금 6만 원 · 벌점 10점",
            ),
        ),
        CardDetailPreview(
            id = "sample-turn",
            status = HomePreviewStatus.Active,
            tag = CardEventTag.RightTurnCrosswalk,
            message = "우회전 뒤 횡단보도에\n사람이 있으면 멈춥니다",
            scene = CardScene.RightTurnCrosswalk,
            evidence = CardEvidencePreview(
                law = "도로교통법 제27조 제1항",
                dates = "2022.7.12 시행",
                penalty = "승용차 범칙금 6만 원 · 벌점 10점",
            ),
        ),
        CardDetailPreview(
            id = "sample-camera",
            status = HomePreviewStatus.Scheduled,
            tag = CardEventTag.Parking,
            message = "2027년 4월 14일부터\n고속도로 정차·주차 무인단속",
            scene = CardScene.HighwayShoulder,
            evidence = CardEvidencePreview(
                law = "도로교통법 일부개정 (법률 제22067호)",
                dates = "2026.10.13 공포 · 2027.4.14 시행",
                penalty = "무인 장비 단속 · 과태료 부과 (금액 미정)",
            ),
        ),
    ).associateBy { it.id }

    fun previewEntries(): List<HomePreviewEntry> = listOf(
        HomePreviewEntry("sample-parking", "어린이보호구역 주정차 금지", "주정차 · 오늘 18:21", HomePreviewStatus.Active, HomePreviewIcon.Parking),
        HomePreviewEntry("sample-crosswalk", "보호구역 횡단보도 일시정지", "어린이보호구역 진입 · 오늘 18:16", HomePreviewStatus.Active, HomePreviewIcon.Crosswalk),
        HomePreviewEntry("sample-turn", "우회전 후 횡단보도 일시정지", "우회전 후 횡단보도 · 오늘 08:05", HomePreviewStatus.Active, HomePreviewIcon.Turn),
        HomePreviewEntry("sample-camera", "고속도로 정차·주차 무인단속", "주정차 · 어제 19:40", HomePreviewStatus.Scheduled, HomePreviewIcon.Camera),
    )
}
