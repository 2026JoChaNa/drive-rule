package com.jochana.driverule.feature.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.jochana.driverule.domain.repository.CardReportTarget
import com.jochana.driverule.feature.home.HomePreviewStatus
import com.jochana.driverule.ui.theme.DriveRuleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI fixtures only. No request leaves the device: report taps go to a local counter. */
class CardDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val highway = CardDetailPreview(
        id = "fixture-highway",
        status = HomePreviewStatus.Scheduled,
        tag = CardEventTag.Parking,
        message = "첫 줄\n둘째 줄",
        scene = CardScene.HighwayShoulder,
        evidence = CardEvidencePreview(law = "법령 이름", dates = "날짜", penalty = "처분 문장"),
    )

    @Test
    fun cardShowsBadgesSceneAndClosesAfterTheAutoCloseTime() {
        var closed by mutableIntStateOf(0)
        setScreen(highway, onClose = { closed++ }, autoCloseMillis = 2_000)

        compose.onNodeWithTag("card_stage_scheduled").assertIsDisplayed()
        compose.onNodeWithTag("card_tag_Parking").assertIsDisplayed()
        compose.onNodeWithTag("card_scene_HighwayShoulder").assertIsDisplayed()
        compose.onNodeWithTag("card_auto_close_running").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, closed)
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun evidenceStopsAutoCloseAndShowsTheThreeRows() {
        var closed by mutableIntStateOf(0)
        setScreen(highway, onClose = { closed++ }, autoCloseMillis = 2_000)

        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("card_evidence_button").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()

        assertEquals(0, closed)
        compose.onNodeWithTag("evidence_sheet").assertIsDisplayed()
        compose.onNodeWithText("법령 이름").assertIsDisplayed()
        compose.onNodeWithText("날짜").assertIsDisplayed()
        compose.onNodeWithText("처분 문장").assertIsDisplayed()

        compose.onNodeWithTag("evidence_close").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithTag("card_auto_close_stopped").assertIsDisplayed()
        assertEquals(0, closed)
    }

    @Test
    fun sampleCardCannotReportOrOpenSource() {
        setScreen(highway, reportState = CardReportState.Unavailable)
        compose.onNodeWithTag("card_evidence_button").performClick()
        settle()

        compose.onNodeWithTag("evidence_report").assertIsNotEnabled()
        compose.onNodeWithTag("evidence_law_source").assertIsNotEnabled()
        compose.onNodeWithTag("evidence_notice").assertIsDisplayed()
    }

    @Test
    fun publishedCardReportTapIsForwardedOnce() {
        var reports by mutableIntStateOf(0)
        val published = highway.copy(
            evidence = highway.evidence.copy(
                sourceUrl = "https://example.invalid/law",
                reportTarget = CardReportTarget(1, 1),
            ),
        )
        setScreen(published, reportState = CardReportState.Idle, onReport = { reports++ })
        compose.onNodeWithTag("card_evidence_button").performClick()
        settle()

        compose.onNodeWithTag("evidence_law_source").assertIsEnabled()
        compose.onNodeWithTag("evidence_report").assertIsEnabled().performClick()
        assertEquals(1, reports)
    }

    @Test
    fun crosswalkCardPlaysTheLayeredFigmaScene() {
        setScreen(highway.copy(scene = CardScene.UnsignalizedCrosswalk))
        settle()

        compose.onNodeWithTag("card_scene_motion_UnsignalizedCrosswalk").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_300)
        compose.onNodeWithTag("card_scene_motion_UnsignalizedCrosswalk").assertIsDisplayed()
    }

    @Test
    fun destinationSceneKeepsItsAspectAndLegendInACompactWindow() {
        val destination = highway.copy(scene = CardScene.SchoolZoneDestination, legend = "반경 300m")
        setScreen(destination, width = 420, height = 760)
        settle()

        compose.onNodeWithTag("card_scene_SchoolZoneDestination").assertIsDisplayed()
        compose.onNodeWithTag("card_scene_legend").assertIsDisplayed()
        compose.onNodeWithTag("card_message").assertIsDisplayed()
    }

    private fun setScreen(
        card: CardDetailPreview,
        reportState: CardReportState = CardReportState.Unavailable,
        onReport: () -> Unit = {},
        onClose: () -> Unit = {},
        autoCloseMillis: Int = 60_000,
        width: Int = 840,
        height: Int = 1000,
    ) {
        // The auto-close animation keeps Compose busy; every test drives the clock manually.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DriveRuleTheme(darkTheme = true) {
                Box(Modifier.width(width.dp).height(height.dp)) {
                    CardDetailScreen(
                        card = card,
                        reportState = reportState,
                        onReport = onReport,
                        onOpenSource = {},
                        onClose = onClose,
                        sample = true,
                        autoCloseMillis = autoCloseMillis,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun settle() {
        // Past the sheet spring (settles in about 0.5 s).
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
    }
}
