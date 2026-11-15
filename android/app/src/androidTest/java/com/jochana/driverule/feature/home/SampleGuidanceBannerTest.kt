package com.jochana.driverule.feature.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jochana.driverule.R
import com.jochana.driverule.ui.theme.DriveRuleTheme
import com.jochana.driverule.ui.theme.LocalDriveRuleColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Captures only the proposed in-app debug banner; no service or TTS is started by these tests. */
@RunWith(AndroidJUnit4::class)
class SampleGuidanceBannerTest {
    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)
    private val shown = mutableStateOf(true)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bannerUsesDayAndNightTokensAndRemainsReadOnlyInsideAppBounds() {
        showBanner(720.dp)
        assertBannerReadable()
        capture("sample-guidance-banner-light")
        compose.runOnIdle { dark.value = true }
        assertBannerReadable()
        capture("sample-guidance-banner-dark")
    }

    @Test fun compactLargeFontKeepsSampleWarningOnOneLine() {
        showBanner(400.dp, fontScale = 1.3f)
        assertBannerReadable()
        capture("sample-guidance-banner-compact")
    }

    @Test fun clearingServiceStateRemovesBannerWithoutAnyUiTimerOrReplay() {
        showBanner(720.dp)
        assertBannerReadable()
        compose.runOnIdle { shown.value = false }
        compose.onNodeWithTag("sample_guidance_banner").assertDoesNotExist()
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithTag("sample_guidance_banner").assertDoesNotExist()
    }

    private fun showBanner(width: Dp, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DriveRuleTheme(darkTheme = dark.value) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(width).height(480.dp)
                            .background(LocalDriveRuleColors.current.background)
                            .testTag("sample_guidance_test_window")) {
                            if (shown.value) SampleGuidanceBanner(
                                context.getString(R.string.bg_sample_school),
                                context.getString(R.string.bg_sample_body),
                                Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertBannerReadable() {
        val card = compose.onNodeWithTag("sample_guidance_banner").assertIsDisplayed().fetchSemanticsNode()
        assertFalse("Read-only banner must not claim a click action", card.config.contains(SemanticsActions.OnClick))
        val window = compose.onNodeWithTag("sample_guidance_test_window").fetchSemanticsNode().boundsInRoot
        val bounds = card.boundsInRoot
        assertTrue(bounds.left >= window.left && bounds.right <= window.right)
        assertTrue(bounds.top >= window.top && bounds.bottom <= window.bottom)
        assertTrue("Banner belongs at the bottom of the app area", bounds.center.y > window.center.y)
        compose.onNodeWithTag("sample_guidance_banner_title").assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.bg_sample_school))
        compose.onNodeWithTag("sample_guidance_banner_body").assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.bg_sample_body))
        listOf("sample_guidance_banner_title", "sample_guidance_banner_body").forEach { tag ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$tag overflows vertically", layout.didOverflowHeight)
            for (line in 0 until layout.lineCount) {
                assertFalse("$tag line $line is ellipsized", layout.isLineEllipsized(line))
                assertTrue("$tag line $line lies outside its bounds", layout.getLineRight(line) <= layout.size.width + 1)
            }
            if (tag.endsWith("body")) assertEquals(1, layout.lineCount)
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("sample_guidance_test_window").captureToImage().asAndroidBitmap()
        val directory = File(context.filesDir, "screenshots")
        check(directory.exists() || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
