package com.jochana.driverule.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jochana.driverule.ui.theme.DriveRuleTheme
import com.jochana.driverule.ui.theme.LocalDriveRuleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Proposed normal-app surface only; these tests never start a service, SDK or speech. */
@RunWith(AndroidJUnit4::class)
class GuidanceBannerTest {
    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)
    private val shown = mutableStateOf(true)
    private var coveredControlClicks = 0

    @Test fun publishedTextAndContextStayWholeInDayAndNightAndCardIsReadOnly() {
        showBanner(width = 720.dp)
        assertWholeCard()
        compose.onNodeWithTag("guidance_banner").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, coveredControlClicks); dark.value = true }
        assertWholeCard()
    }

    @Test fun compactLargeFontWrapsWithoutShorteningPublishedText() {
        showBanner(width = 400.dp, fontScale = 1.3f)
        assertWholeCard()
    }

    @Test fun overlyLongBodyOmitsTheEntireBannerInsteadOfPublishingAnExcerpt() {
        showBanner(width = 400.dp, message = "표시 한계를 넘는 테스트 본문입니다. ".repeat(30))
        compose.onNodeWithTag("guidance_banner").assertDoesNotExist()
        compose.onNodeWithTag("guidance_banner_title").assertDoesNotExist()
    }

    @Test fun overlyLongProvenanceOmitsTheEntireBannerInsteadOfDroppingContext() {
        showBanner(width = 400.dp, context = "긴 출처 이름과 상태 정보 ".repeat(20))
        compose.onNodeWithTag("guidance_banner").assertDoesNotExist()
        compose.onNodeWithTag("guidance_banner_body").assertDoesNotExist()
    }

    @Test fun insufficientAppHeightOmitsCardAndClearedStateDoesNotReplayOnThemeChange() {
        showBanner(width = 720.dp, height = 80.dp)
        compose.onNodeWithTag("guidance_banner").assertDoesNotExist()
        compose.runOnIdle { shown.value = false; dark.value = true }
        compose.onNodeWithTag("guidance_banner").assertDoesNotExist()
    }

    @Test fun clearingOwnerStateRemovesVisibleCardWithoutAUiTimer() {
        showBanner(width = 720.dp)
        assertWholeCard()
        compose.runOnIdle { shown.value = false; dark.value = true }
        compose.onNodeWithTag("guidance_banner").assertDoesNotExist()
    }

    private fun showBanner(
        width: Dp,
        height: Dp = 480.dp,
        fontScale: Float = 1f,
        message: String = MESSAGE,
        context: String = CONTEXT,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DriveRuleTheme(darkTheme = dark.value) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(width).height(height)
                            .background(LocalDriveRuleColors.current.background)
                            .testTag("guidance_test_window")) {
                            Box(Modifier.fillMaxSize().clickable { coveredControlClicks++ })
                            if (shown.value) GuidanceBanner(TITLE, message,
                                Modifier.align(Alignment.BottomCenter).padding(16.dp), contextLabel = context)
                        }
                    }
                }
            }
        }
    }

    private fun assertWholeCard() {
        val card = compose.onNodeWithTag("guidance_banner").assertIsDisplayed().fetchSemanticsNode()
        assertFalse(card.config.contains(SemanticsActions.OnClick))
        val window = compose.onNodeWithTag("guidance_test_window").fetchSemanticsNode().boundsInRoot
        val bounds = card.boundsInRoot
        assertTrue(bounds.left >= window.left && bounds.right <= window.right)
        assertTrue(bounds.top >= window.top && bounds.bottom <= window.bottom)
        assertTrue(bounds.center.y > window.center.y)
        listOf("guidance_banner_title" to TITLE, "guidance_banner_body" to MESSAGE,
            "guidance_banner_context" to CONTEXT).forEach { (tag, expected) ->
            compose.onNodeWithTag(tag).assertIsDisplayed().assertTextEquals(expected)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
    }

    private companion object {
        const val TITLE = "게시된 안내 제목"
        const val MESSAGE = "게시 본문을 바꾸지 않고 그대로 표시합니다."
        const val CONTEXT = "시행 상태 · 출처 정보"
    }
}
