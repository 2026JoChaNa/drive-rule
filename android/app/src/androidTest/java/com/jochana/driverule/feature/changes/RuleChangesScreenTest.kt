package com.jochana.driverule.feature.changes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.jochana.driverule.feature.home.HomePreviewEntry
import com.jochana.driverule.feature.home.HomePreviewIcon
import com.jochana.driverule.feature.home.HomePreviewStatus
import com.jochana.driverule.feature.home.HomeScreen
import com.jochana.driverule.ui.theme.DriveRuleTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RuleChangesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val changes = listOf(
        RuleChangePreview("a", "예정 A", LocalDate.of(2027, 6, 3), "제1호", HomePreviewStatus.Scheduled, RuleChangeIcon.Drink),
        RuleChangePreview("b", "예정 B", LocalDate.of(2027, 6, 3), "제1호", HomePreviewStatus.Scheduled, RuleChangeIcon.Calendar),
        RuleChangePreview("c", "예정 C", LocalDate.of(2027, 4, 14), "제2호", HomePreviewStatus.Scheduled,
            RuleChangeIcon.Camera, cardId = "card-c", alreadyHeard = true),
        RuleChangePreview("d", "시행 D", LocalDate.of(2026, 7, 1), "제3호", HomePreviewStatus.Active, RuleChangeIcon.Drug),
        RuleChangePreview("e", "시행 E", LocalDate.of(2025, 6, 4), "제4호", HomePreviewStatus.Active, RuleChangeIcon.License),
    )

    @Test
    fun monthlyListShowsSectionsMonthsAndForwardsTaps() {
        var opened by mutableStateOf<RuleChangePreview?>(null)
        var home by mutableStateOf(0)
        setContent {
            RuleChangesScreen(changes, onHome = { home++ }, onChangeClick = { opened = it }, sample = true)
        }

        compose.onNodeWithTag("rule_changes_month_2027_6").assertIsDisplayed()
        compose.onAllNodesWithText("6월 3일 시행 · 법률 제1호").assertCountEquals(2)
        compose.onNodeWithTag("rule_changes_month_2025_6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("rule_change_c").performScrollTo().performClick()
        compose.onNodeWithTag("rule_changes_home").performClick()

        assertEquals("card-c", opened?.cardId)
        assertEquals(1, home)
    }

    @Test
    fun homeBannerCountsUnheardChangesOfTheNextDateAndOpensTheList() {
        var bannerTaps by mutableStateOf(0)
        val banner = requireNotNull(upcomingRulesBanner(changes))
        setContent {
            HomeScreen(
                driverName = "운전자 1",
                entries = listOf(HomePreviewEntry("x", "제목", "부제", HomePreviewStatus.Active, HomePreviewIcon.Parking)),
                onChangeDriver = {}, onEntryClick = {}, onGleoClick = {}, onChangesClick = {},
                onThemeClick = {}, themeLabel = "시스템",
                upcomingBanner = banner,
                onUpcomingBannerClick = { bannerTaps++ },
            )
        }

        compose.onNodeWithText("곧 바뀌는 규칙 2건").assertIsDisplayed()
        compose.onNodeWithText("예정 A · 예정 B (2027년 6월 3일 시행)").assertIsDisplayed()
        compose.onNodeWithTag("upcoming_rules_banner").performClick()
        assertEquals(1, bannerTaps)
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            DriveRuleTheme(darkTheme = true) {
                Box(Modifier.width(1680.dp).height(1000.dp)) { content() }
            }
        }
    }
}
