package com.jochana.driverule.feature.predeparture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.jochana.driverule.feature.changes.RuleChangeIcon
import com.jochana.driverule.feature.changes.RuleChangePreview
import com.jochana.driverule.feature.home.HomePreviewStatus
import com.jochana.driverule.ui.theme.DriveRuleTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreDepartureAlertTest {
    @get:Rule
    val compose = createComposeRule()

    private val change = RuleChangePreview(
        "a", "규칙", LocalDate.of(2027, 6, 3), "제1호", HomePreviewStatus.Scheduled, RuleChangeIcon.Drink,
        alertMessage = "첫 줄\n둘째 줄",
    )

    @Test
    fun alertShowsDriverDateAndMessageThenClosesAfterItsDuration() {
        var dismissed by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DriveRuleTheme(darkTheme = false) {
                Box(Modifier.width(1680.dp).height(1000.dp)) {
                    PreDepartureAlert(change, "운전자 1", onDismiss = { dismissed++ }, durationMillis = 2_000)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithTag("pre_departure_alert").assertIsDisplayed()
        compose.onNodeWithText("드라이브룰 · 운전자 1 님").assertIsDisplayed()
        compose.onNodeWithText("6월 3일 시행").assertIsDisplayed()
        compose.onNodeWithTag("pre_departure_alert_message").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0, dismissed)
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun changeWithoutAlertSentenceDrawsNothing() {
        compose.setContent {
            DriveRuleTheme {
                PreDepartureAlert(change.copy(alertMessage = null), "운전자 1", onDismiss = {})
            }
        }
        compose.onNodeWithTag("pre_departure_alert").assertDoesNotExist()
    }
}
