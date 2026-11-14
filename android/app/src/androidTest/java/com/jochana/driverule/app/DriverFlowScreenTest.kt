package com.jochana.driverule.app

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.pm.PackageManager
import androidx.compose.ui.test.onAllNodesWithText
import com.jochana.driverule.core.platform.speech.PleosNameSpeechEngine
import org.junit.Assume.assumeTrue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextReplacement
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.feature.changes.RuleChangeIcon
import com.jochana.driverule.feature.changes.RuleChangePreview
import com.jochana.driverule.feature.home.HomePreviewStatus
import java.time.LocalDate
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jochana.driverule.R
import com.jochana.driverule.core.model.DriverProfile
import com.jochana.driverule.core.model.ProfileSnapshot
import com.jochana.driverule.core.model.ThemeMode
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.profile.ProfileMutationResult
import com.jochana.driverule.domain.profile.ProfileNamePolicy
import com.jochana.driverule.domain.profile.ProfileNameValidation
import com.jochana.driverule.domain.repository.DriverProfileRepository
import com.jochana.driverule.domain.repository.ThemePreferenceRepository
import com.jochana.driverule.domain.vehicle.UxRestrictionProvider
import com.jochana.driverule.feature.profile.DriverFlowViewModel
import com.jochana.driverule.ui.theme.DriveRuleTheme
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises screen wiring with the real ViewModel and isolated, in-memory persistence. */
@RunWith(AndroidJUnit4::class)
class DriverFlowScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val stores = mutableListOf<ViewModelStore>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun clearViewModels() {
        compose.runOnUiThread { stores.forEach(ViewModelStore::clear) }
    }

    @Test
    fun firstNameRegistrationOpensPermissionGuideBeforeHome() {
        val fixture = showFlow()
        compose.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        compose.onNodeWithTag("save_driver").assertIsNotEnabled()

        compose.onNodeWithTag("driver_name").performTextInput("  김하늘  ")
        compose.onNodeWithTag("save_driver").performClick()

        compose.onNodeWithTag("permission_guide_screen").assertIsDisplayed()
        compose.onNodeWithTag("home_screen").assertDoesNotExist()
        compose.onNodeWithTag("permission_allow").performScrollTo().performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_screen").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ui_driver_change, "김하늘"))
            .assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("김하늘", fixture.profiles.snapshot.value.activeProfile?.name)
            assertEquals(1, fixture.profiles.snapshot.value.profiles.size)
        }
    }

    @Test
    fun skippingFirstNameStillShowsGuideAndLaterOpensHome() {
        val fixture = showFlow()
        compose.onNodeWithTag("skip_driver").performClick()

        compose.onNodeWithTag("permission_guide_screen").assertIsDisplayed()
        compose.onNodeWithTag("permission_later").performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.onNodeWithTag("driver_name").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                context.getString(R.string.default_driver_name),
                fixture.profiles.snapshot.value.activeProfile?.name,
            )
            assertEquals(1, fixture.profiles.snapshot.value.profiles.size)
        }
    }

    @Test
    fun themePickerWorksInSetupAndHomeAndCapturesBothAppearances() {
        val fixture = showFlow()
        captureScreen("onboarding-light")

        chooseTheme(fixture, ThemeMode.Dark)
        compose.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        captureScreen("onboarding-dark")

        compose.onNodeWithTag("skip_driver").performClick()
        compose.onNodeWithTag("permission_guide_screen").assertIsDisplayed()
        captureScreen("permission-guide-dark")
        chooseTheme(fixture, ThemeMode.Light)
        captureScreen("permission-guide-light")
        chooseTheme(fixture, ThemeMode.Dark)
        compose.onNodeWithTag("permission_allow").performScrollTo().performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        captureScreen("home-dark")

        chooseTheme(fixture, ThemeMode.Light)
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        captureScreen("home-light")

        chooseTheme(fixture, ThemeMode.System)
        compose.runOnIdle {
            assertEquals(
                listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.Dark, ThemeMode.Light, ThemeMode.System),
                fixture.appearance.writtenModes,
            )
        }
    }

    @Test
    fun restrictionOrUnknownStateDismissesOpenDetailsWithoutReopeningThem() {
        val fixture = showFlow(listOf(DriverProfile(1, "테스트 운전자")))
        val detailBody = context.getString(R.string.sample_details_body)

        listOf(VehicleUxState.Restricted, VehicleUxState.Unknown).forEach { restrictedState ->
            compose.onNodeWithTag("sample_entry_sample-parking").performScrollTo().performClick()
            compose.onNodeWithText(detailBody).assertIsDisplayed()

            compose.runOnIdle { fixture.ux.current.value = restrictedState }

            compose.onNodeWithText(detailBody).assertDoesNotExist()
            compose.onNodeWithTag("home_screen").assertDoesNotExist()
            compose.onNodeWithTag("driver_name").assertDoesNotExist()
            compose.onNodeWithText(
                context.getString(
                    if (restrictedState == VehicleUxState.Restricted) {
                        R.string.predeparture_driving_restricted
                    } else {
                        R.string.predeparture_vehicle_unavailable
                    },
                ),
            ).assertIsDisplayed()

            compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Unrestricted }
            compose.onNodeWithTag("home_screen").assertIsDisplayed()
            compose.onNodeWithText(detailBody).assertDoesNotExist()
        }
    }

    @Test
    fun restrictionRemovesNameInputBeforeAnyProfileIsSaved() {
        val fixture = showFlow()
        compose.onNodeWithTag("driver_name").performTextInput("작성 중인 이름")

        compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Restricted }

        compose.onNodeWithTag("onboarding_screen").assertDoesNotExist()
        compose.onNodeWithTag("driver_name").assertDoesNotExist()
        compose.onNodeWithTag("save_driver").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.predeparture_driving_restricted))
            .assertIsDisplayed()
        compose.runOnIdle { assertTrue(fixture.profiles.snapshot.value.profiles.isEmpty()) }
    }

    @Test
    fun addingAndSelectingDriverKeepsExistingProfile() {
        val original = DriverProfile(1, "김하늘")
        val fixture = showFlow(listOf(original))

        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("driver_sheet").assertIsDisplayed()
        compose.onNodeWithTag("add_driver").performScrollTo().performClick()
        compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
        compose.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        compose.onNodeWithTag("skip_driver").assertDoesNotExist()
        compose.onNodeWithTag("driver_name").performTextInput("이바다")
        compose.onNodeWithTag("save_driver").performClick()

        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.onNodeWithTag("permission_guide_screen").assertDoesNotExist()
        compose.runOnIdle { assertEquals("이바다", fixture.profiles.snapshot.value.activeProfile?.name) }

        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("profile_2").assertIsSelected()
        compose.onNodeWithTag("profile_1").assertIsNotSelected().performClick()
        compose.onNodeWithTag("driver_sheet").assertDoesNotExist()

        compose.onNodeWithText(context.getString(R.string.ui_driver_change, original.name))
            .assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(original, fixture.profiles.snapshot.value.activeProfile)
            assertEquals(listOf("김하늘", "이바다"), fixture.profiles.snapshot.value.profiles.map { it.name })
        }
    }

    @Test
    fun closingOrTouchingOutsideDriverSheetKeepsCurrentSelection() {
        val drivers = listOf(DriverProfile(1, "김하늘"), DriverProfile(2, "이바다"))
        val fixture = showFlow(drivers)
        val initialSnapshot = fixture.profiles.snapshot.value

        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("driver_sheet").assertIsDisplayed()
        compose.onNodeWithText("지금 운전하는 사람").assertIsDisplayed()
        compose.onNodeWithTag("profile_1").assertIsSelected()
        compose.onNodeWithTag("profile_2").assertIsNotSelected()
        compose.onNodeWithTag("driver_sheet_close").performClick()

        compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(initialSnapshot, fixture.profiles.snapshot.value) }

        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("driver_sheet").assertIsDisplayed()
        dismissDriverSheetFromOutside()

        compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(initialSnapshot, fixture.profiles.snapshot.value) }
    }

    @Test
    fun threeDriversDisableAddingWhileEveryStoredDriverRemainsSelectable() {
        val drivers = listOf(
            DriverProfile(1, "김하늘"),
            DriverProfile(2, "이바다"),
            DriverProfile(3, "박산"),
        )
        val fixture = showFlow(drivers)
        compose.onNodeWithTag("change_driver").performClick()

        compose.onNodeWithTag("add_driver").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("profile_3").performScrollTo().assertIsDisplayed().performClick()

        compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ui_driver_change, drivers.last().name))
            .assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(drivers.last(), fixture.profiles.snapshot.value.activeProfile)
            assertEquals(drivers, fixture.profiles.snapshot.value.profiles)
        }
    }

    @Test
    fun restrictedOrUnknownUxRemovesDriverSheetAndDoesNotReopenItOnRecovery() {
        val drivers = listOf(DriverProfile(1, "김하늘"), DriverProfile(2, "이바다"))
        val fixture = showFlow(drivers)

        listOf(VehicleUxState.Restricted, VehicleUxState.Unknown).forEach { restrictedState ->
            compose.onNodeWithTag("change_driver").performClick()
            compose.onNodeWithTag("driver_sheet").assertIsDisplayed()
            compose.runOnIdle { fixture.ux.current.value = restrictedState }

            compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
            compose.onNodeWithTag("profile_2").assertDoesNotExist()
            compose.onNodeWithTag("home_screen").assertDoesNotExist()
            compose.onNodeWithText(
                context.getString(
                    if (restrictedState == VehicleUxState.Restricted) {
                        R.string.predeparture_driving_restricted
                    } else {
                        R.string.predeparture_vehicle_unavailable
                    },
                ),
            ).assertIsDisplayed()

            compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Unrestricted }
            compose.onNodeWithTag("home_screen").assertIsDisplayed()
            compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
            compose.runOnIdle { assertEquals(drivers.first(), fixture.profiles.snapshot.value.activeProfile) }
        }
    }

    @Test
    fun driverSheetSupportsLightAndDarkWithoutChangingItsSelectedProfile() {
        val drivers = listOf(DriverProfile(1, "김하늘"), DriverProfile(2, "이바다"))
        val fixture = showFlow(drivers)

        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("driver_sheet").assertIsDisplayed()
        compose.onNodeWithTag("profile_1").assertIsSelected()
        captureNode("driver_sheet", "driver-sheet-light")
        compose.onNodeWithTag("driver_sheet_close").performClick()

        chooseTheme(fixture, ThemeMode.Dark)
        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("driver_sheet").assertIsDisplayed()
        compose.onNodeWithTag("profile_1").assertIsSelected()
        captureNode("driver_sheet", "driver-sheet-dark")
        compose.onNodeWithTag("driver_sheet_close").performClick()

        compose.runOnIdle { assertEquals(drivers.first(), fixture.profiles.snapshot.value.activeProfile) }
    }

    @Test
    fun compactDriverSheetWithLargeFontKeepsSelectionAddingAndCloseReachable() {
        val drivers = listOf(
            DriverProfile(1, "가나다라마바사아자차카타파하가나다라마바"),
            DriverProfile(2, "테스트 운전자"),
        )
        val fixture = showFlow(drivers, width = 540.dp, fontScale = 1.3f)

        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("driver_sheet_close").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("profile_2").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("add_driver").performScrollTo().assertIsDisplayed().assertHasClickAction()
        captureNode("driver_sheet", "driver-sheet-compact-large-font")
        compose.onNodeWithTag("driver_sheet_close").assertIsDisplayed().performClick()

        compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(drivers.first(), fixture.profiles.snapshot.value.activeProfile) }
    }

    @Test
    fun compactHomeWithLargeFontKeepsDriverThemeAndLastActionsReachable() {
        val longName = "가나다라마바사아자차카타파하가나다라마바"
        val fixture = showFlow(
            initialProfiles = listOf(DriverProfile(1, longName)),
            width = 540.dp,
            fontScale = 1.3f,
        )
        compose.onNodeWithTag("change_driver").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithTag("profile_1").performScrollTo().performClick()
        compose.onNodeWithTag("driver_sheet").assertDoesNotExist()
        compose.onNodeWithTag("theme_picker").assertIsDisplayed().assertHasClickAction()
        chooseTheme(fixture, ThemeMode.Dark)
        chooseTheme(fixture, ThemeMode.Light)
        captureScreen("home-compact-large-font")

        compose.onNodeWithTag("sample_entry_sample-camera").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("changes_button").performScrollTo().assertIsDisplayed().performClick()
        // The button now opens the 00-1 monthly screen instead of the sample dialog.
        compose.onNodeWithTag("rule_changes_screen").assertIsDisplayed()
        compose.onNodeWithTag("rule_changes_home").performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(longName, fixture.profiles.snapshot.value.activeProfile?.name) }
    }

    @Test
    fun preDepartureAlertAppearsOnceOnFirstHomeAndNotAfterReturning() {
        val change = RuleChangePreview(
            "alert", "알림 규칙", LocalDate.of(2027, 6, 3), "제1호", HomePreviewStatus.Scheduled,
            RuleChangeIcon.Drink, alertMessage = "알림 문장",
        )
        val gate = PreDepartureAlertGate()
        showFlow(listOf(DriverProfile(1, "테스트 운전자")), ruleChanges = listOf(change), alertGate = gate)

        compose.onNodeWithTag("pre_departure_alert").assertIsDisplayed()
        compose.onNodeWithText("드라이브룰 · 테스트 운전자 님").assertIsDisplayed()

        compose.onNodeWithTag("changes_button").performScrollTo().performClick()
        compose.onNodeWithTag("pre_departure_alert").assertDoesNotExist()
        compose.onNodeWithTag("rule_changes_home").performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.onNodeWithTag("pre_departure_alert").assertDoesNotExist()
        compose.runOnIdle { assertEquals(false, gate.claim()) }
    }

    @Test
    fun wideHeaderReservesBrandAndThemeWhenDriverNameIsLong() {
        val longName = "가나다라마바사아자차카타파하가나다라마바"
        val fixture = showFlow(listOf(DriverProfile(1, longName)), width = 720.dp)

        captureScreen("home-720dp-long-name")
        assertHeaderRemainsReadableAndTouchable()
        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("driver_sheet_close").assertIsDisplayed().performClick()
        chooseTheme(fixture, ThemeMode.Dark)
        captureScreen("home-720dp-long-name-dark")
        assertHeaderRemainsReadableAndTouchable()
        compose.runOnIdle { assertEquals(longName, fixture.profiles.snapshot.value.activeProfile?.name) }
    }

    @Test
    fun shortCompactOnboardingKeepsNameVoiceAndNextReachableWithLargeFont() {
        val fixture = showFlow(width = 400.dp, height = 600.dp, fontScale = 1.3f)
        compose.onNodeWithTag("drive_rule_brand").assertIsDisplayed()
        compose.onNodeWithTag("theme_picker").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("skip_driver").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("driver_name").performScrollTo().assertIsDisplayed().performTextInput("김하늘")
        compose.onNodeWithTag("voice_input").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("save_driver").assertIsDisplayed().assertHasClickAction()
        captureScreen("onboarding-400x600-large-font")
        compose.onNodeWithTag("save_driver").performClick()
        compose.onNodeWithTag("permission_guide_screen").assertIsDisplayed()
        compose.onNodeWithTag("permission_allow").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals("김하늘", fixture.profiles.snapshot.value.activeProfile?.name) }
    }

    private fun assertHeaderRemainsReadableAndTouchable() {
        val root = compose.onNodeWithTag("driver_flow_test_root").fetchSemanticsNode().boundsInRoot
        val bounds = listOf("drive_rule_brand", "theme_picker", "change_driver").map { tag ->
            val node = compose.onNodeWithTag(tag).assertIsDisplayed()
            if (tag != "drive_rule_brand") node.assertHasClickAction()
            node.fetchSemanticsNode().boundsInRoot.also { bounds ->
                assertTrue("$tag must retain positive width", bounds.width > 0f)
                assertTrue("$tag must stay within the app window", bounds.left >= root.left && bounds.right <= root.right)
                assertTrue("$tag must stay vertically visible", bounds.top >= root.top && bounds.bottom <= root.bottom)
                if (tag != "drive_rule_brand") {
                    assertTrue("$tag must keep a 56 dp touch height", bounds.height >= with(compose.density) { 56.dp.toPx() } - 1f)
                }
            }
        }
        assertTrue("Brand and theme must not overlap", bounds[0].right <= bounds[1].left)
        assertTrue("Theme and driver action must not overlap", bounds[1].right <= bounds[2].left)
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.ui_brand))
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        val diagnostics = textLayouts.joinToString("; ") { result ->
            "size=${result.size}, paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}, " +
                "constraints=${result.layoutInput.constraints}, fontSize=${result.layoutInput.style.fontSize}, " +
                "lineHeight=${result.layoutInput.style.lineHeight}, lines=${result.lineCount}, " +
                "overflowWidth=${result.didOverflowWidth}, overflowHeight=${result.didOverflowHeight}, " +
                (0 until result.lineCount).joinToString { line ->
                    "line[$line](right=${result.getLineRight(line)}, bottom=${result.getLineBottom(line)}, " +
                        "ellipsized=${result.isLineEllipsized(line)})"
                }
        }
        // Compose 1.7.6 reconstructs simple Text semantics with the parent's full width,
        // while retaining the actual intrinsic layout size. hasVisualOverflow can therefore
        // report horizontal overflow for fully visible text; inspect the rendered line instead.
        assertTrue("Brand must not be clipped or ellipsized: $diagnostics",
            textLayouts.isNotEmpty() && textLayouts.all { result ->
                result.lineCount == 1 &&
                    result.getLineStart(0) == 0 &&
                    result.getLineEnd(0) == result.layoutInput.text.length &&
                    !result.isLineEllipsized(0) &&
                    result.getLineLeft(0) >= -0.01f &&
                    result.getLineRight(0) <= result.size.width + 0.01f &&
                    result.getLineTop(0) >= -0.01f &&
                    result.getLineBottom(0) <= result.size.height + 0.01f
            })
    }

    @Test
    fun compactPermissionGuideKeepsContinueReachableAndUxRestrictionPreservesProgress() {
        val fixture = showFlow(width = 540.dp, fontScale = 1.3f)
        compose.onNodeWithTag("skip_driver").performClick()
        compose.onNodeWithTag("permission_later").assertIsDisplayed()
        compose.onNodeWithTag("permission_allow").performScrollTo().assertIsDisplayed()
        captureScreen("permission-guide-compact-large-font")
        compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Restricted }
        compose.onNodeWithTag("permission_guide_screen").assertDoesNotExist()
        compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Unrestricted }
        compose.onNodeWithTag("permission_guide_screen").assertIsDisplayed()
        compose.onNodeWithTag("permission_allow").performScrollTo().performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun speechOnlyFillsFinalNameAndUserMustConfirmBeforeSaving() {
        val engine = FakeSpeechEngine()
        val fixture = showFlow(speechEngineFactory = { engine })
        compose.runOnIdle { assertEquals(1, engine.starts) }
        compose.runOnIdle {
            assertEquals(1, engine.starts)
            engine.listener.onReady()
            engine.listener.onPartial("김하")
        }
        compose.runOnIdle { assertEquals("", fixture.model.uiState.value.name) }
        compose.runOnIdle { engine.listener.onResult("  김하늘  ") }
        compose.onNodeWithTag("driver_name").assertTextEquals("김하늘")
        compose.runOnIdle { assertTrue(fixture.profiles.snapshot.value.profiles.isEmpty()) }
        captureScreen("voice-name-confirmation")
        compose.onNodeWithTag("save_driver").performClick()
        compose.onNodeWithTag("permission_guide_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals("김하늘", fixture.profiles.snapshot.value.activeProfile?.name) }
    }

    @Test
    fun typingOrRestrictedUxCancelsSpeechAndIgnoresLateResults() {
        val engines = mutableListOf<FakeSpeechEngine>()
        val fixture = showFlow(speechEngineFactory = { FakeSpeechEngine().also(engines::add) })
        compose.runOnIdle { assertEquals(1, engines.size) }
        compose.onNodeWithTag("driver_name").performTextReplacement("직접 입력")
        compose.runOnIdle {
            assertTrue(engines.first().closes > 0)
            engines.first().listener.onResult("늦은 결과")
        }
        compose.onNodeWithTag("driver_name").assertTextEquals("직접 입력")
        compose.onNodeWithTag("voice_input").performScrollTo().performClick()
        compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Unknown }
        compose.onNodeWithTag("onboarding_screen").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(engines.last().closes > 0)
            engines.last().listener.onResult("제한 중 결과")
            fixture.ux.current.value = VehicleUxState.Unrestricted
        }
        compose.onNodeWithTag("driver_name").assertTextEquals("직접 입력")
        compose.runOnIdle { assertTrue(fixture.profiles.snapshot.value.profiles.isEmpty()) }
    }

    @Test
    fun firstAndAdditionalNameScreensStartSpeechWithoutATapButUxRecoveryDoesNot() {
        val engines = mutableListOf<FakeSpeechEngine>()
        val fixture = showFlow(speechEngineFactory = { FakeSpeechEngine().also(engines::add) })
        compose.runOnIdle { assertEquals(1, engines.size) }
        compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Restricted }
        compose.onNodeWithTag("onboarding_screen").assertDoesNotExist()
        compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Unrestricted }
        compose.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, engines.size) }

        compose.onNodeWithTag("driver_name").performTextInput("첫 운전자")
        compose.onNodeWithTag("save_driver").performClick()
        compose.onNodeWithTag("permission_allow").performScrollTo().performClick()
        compose.onNodeWithTag("change_driver").performClick()
        compose.onNodeWithTag("add_driver").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, engines.size) }
        compose.runOnIdle { engines.last().listener.onResult("추가 운전자") }
        compose.onNodeWithTag("driver_name").assertTextEquals("추가 운전자")
        compose.onNodeWithTag("save_driver").performClick()
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.onNodeWithTag("permission_guide_screen").assertDoesNotExist()
    }

    @Test
    fun realPleosSpeechStartsAutomaticallyOnNameScreenWithoutVoiceButtonTap() {
        assumeTrue("Requires Pleos STT service", context.checkSelfPermission(
            "pleos.car.permission.STT_SERVICE") == PackageManager.PERMISSION_GRANTED)
        val fixture = showFlow(speechEngineFactory = { PleosNameSpeechEngine(context) })
        try {
            val listening = context.getString(R.string.voice_listening)
            compose.waitUntil(45_000) {
                compose.onAllNodesWithText(listening).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("voice_status").assertTextEquals(listening)
            captureScreen("voice-name-listening")
            compose.onNodeWithTag("voice_input").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(fixture.profiles.snapshot.value.profiles.isEmpty()) }
        } finally {
            compose.runOnIdle { fixture.ux.current.value = VehicleUxState.Restricted }
            compose.waitForIdle()
        }
    }

    private fun showFlow(
        initialProfiles: List<DriverProfile> = emptyList(),
        width: Dp? = null,
        height: Dp? = null,
        fontScale: Float = 1f,
        speechEngineFactory: (() -> NameSpeechEngine)? = { FakeSpeechEngine() },
        ruleChanges: List<RuleChangePreview> = emptyList(),
        alertGate: PreDepartureAlertGate = PreDepartureAlertGate(),
    ): Fixture {
        val fixture = Fixture(initialProfiles)
        lateinit var model: DriverFlowViewModel
        compose.runOnUiThread {
            model = DriverFlowViewModel(fixture.profiles, fixture.ux, fixture.appearance)
            stores += ViewModelStore().apply { put("driverFlow", model) }
        }
        fixture.model = model
        compose.setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale),
            ) {
                DriveRuleTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = (if (width == null) Modifier.fillMaxWidth()
                                else Modifier.width(width))
                                .then(if (height == null) Modifier.fillMaxHeight() else Modifier.height(height))
                                .testTag("driver_flow_test_root"),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            DriverFlowContent(
                                state = state,
                                model = model,
                                entries = sampleEntries,
                                speechEngineFactory = speechEngineFactory,
                                modifier = Modifier.safeDrawingPadding().imePadding().fillMaxSize(),
                                ruleChanges = ruleChanges,
                                preDepartureAlertGate = alertGate,
                            )
                        }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { model.uiState.value.interactionAllowed }
        compose.waitForIdle()
        return fixture
    }

    private fun chooseTheme(fixture: Fixture, mode: ThemeMode) {
        compose.onNodeWithTag("theme_picker").performClick()
        val optionTag = "theme_${mode.name.lowercase()}"
        // AlertDialog attaches a separate Android window; await that window's semantics.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(optionTag)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag(optionTag).performClick()
        compose.waitUntil(5_000) {
            fixture.model.uiState.value.themeMode == mode &&
                fixture.appearance.current.value == mode
        }
        compose.onNodeWithTag("theme_${mode.name.lowercase()}").assertDoesNotExist()
    }

    private fun dismissDriverSheetFromOutside() {
        compose.waitForIdle()
        // Material3 1.3.1 applies the caller's modifier before its draggable offset.
        // The close button is inside that offset and therefore has visual coordinates.
        val closeBounds = compose.onNodeWithTag("driver_sheet_close").fetchSemanticsNode().boundsInRoot
        assertTrue("The normal-size sheet must leave an outside dismiss area", closeBounds.top > 0f)
        // ModalBottomSheet is hosted in a separate window; target that window's root.
        compose.onNode(
            isRoot() and hasAnyDescendant(hasTestTag("driver_sheet")),
            useUnmergedTree = true,
        ).performTouchInput {
            click(Offset(closeBounds.center.x, closeBounds.top / 2f))
        }
    }

    private fun captureScreen(name: String) = captureNode("driver_flow_test_root", name)

    private fun captureNode(tag: String, name: String) {
        compose.waitForIdle()
        // A sheet lives in a separate Android window. Capture the composited display
        // so both its panel and scrim are included; this also includes system UI.
        val bitmap = if (tag == "driver_sheet") {
            compose.onNodeWithTag("driver_sheet_close").assertIsDisplayed()
            awaitWindowFrameCommit()
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) {
                "Cannot capture the display containing the driver sheet"
            }
        } else {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        }
        val directory = File(context.filesDir, "screenshots")
        check(directory.exists() || directory.mkdirs()) { "Cannot create screenshot output directory" }
        File(directory, "$name.png").outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun awaitWindowFrameCommit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idleness alone does not wait for a separate Dialog window's render buffer.
        // On the Pleos API 34 test image, await a submitted frame from every visible window.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val callbacks = mutableListOf<Pair<ViewTreeObserver, Runnable>>()
            lateinit var committed: CountDownLatch
            instrumentation.runOnMainSync {
                val roots = WindowInspector.getGlobalWindowViews().filter {
                    it.isAttachedToWindow && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
                }
                check(roots.isNotEmpty()) { "No attached windows to capture" }
                check(roots.all { it.isHardwareAccelerated }) { "Frame-commit capture requires hardware rendering" }
                committed = CountDownLatch(roots.size)
                roots.forEach { root ->
                    val observer = root.viewTreeObserver
                    val callback = Runnable { committed.countDown() }
                    callbacks += observer to callback
                    observer.registerFrameCommitCallback(callback)
                    root.postInvalidateOnAnimation()
                }
            }
            try {
                check(committed.await(5, TimeUnit.SECONDS)) { "Timed out waiting for Dialog frames to commit" }
            } finally {
                instrumentation.runOnMainSync {
                    callbacks.forEach { (observer, callback) ->
                        if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
                    }
                }
            }
        }
        // Wait for the window/accessibility transition to settle after the draw submission.
        instrumentation.uiAutomation.waitForIdle(100, 5_000)
    }

    private class Fixture(initialProfiles: List<DriverProfile>) {
        val profiles = MemoryProfiles(initialProfiles)
        val ux = MutableUx()
        val appearance = MemoryAppearance()
        lateinit var model: DriverFlowViewModel
    }

    private class MemoryProfiles(initialProfiles: List<DriverProfile>) : DriverProfileRepository {
        val snapshot = MutableStateFlow(ProfileSnapshot(initialProfiles, initialProfiles.firstOrNull()?.id))
        override fun observeProfiles() = snapshot.asStateFlow()

        override suspend fun createProfile(name: String): ProfileMutationResult {
            if (snapshot.value.permissionGuidePending) return ProfileMutationResult.PermissionGuideRequired
            val validated = ProfileNamePolicy.validate(name)
            if (validated is ProfileNameValidation.Invalid) {
                return ProfileMutationResult.InvalidName(validated.error)
            }
            if (snapshot.value.profiles.size >= ProfileNamePolicy.MAX_PROFILES) {
                return ProfileMutationResult.LimitReached
            }
            val profile = DriverProfile(
                (snapshot.value.profiles.maxOfOrNull { it.id } ?: 0) + 1,
                (validated as ProfileNameValidation.Valid).normalizedName,
            )
            snapshot.value = ProfileSnapshot(snapshot.value.profiles + profile, profile.id,
                permissionGuidePending = snapshot.value.profiles.isEmpty())
            return ProfileMutationResult.Success(profile)
        }

        override suspend fun completePermissionGuide(): ProfileMutationResult {
            val active = snapshot.value.activeProfile ?: return ProfileMutationResult.ProfileNotFound
            snapshot.value = snapshot.value.copy(permissionGuidePending = false)
            return ProfileMutationResult.Success(active)
        }

        override suspend fun selectProfile(id: Long): ProfileMutationResult {
            if (snapshot.value.permissionGuidePending) return ProfileMutationResult.PermissionGuideRequired
            val profile = snapshot.value.profiles.firstOrNull { it.id == id }
                ?: return ProfileMutationResult.ProfileNotFound
            snapshot.value = snapshot.value.copy(activeProfileId = id)
            return ProfileMutationResult.Success(profile)
        }
    }

    private class FakeSpeechEngine : NameSpeechEngine {
        lateinit var listener: NameSpeechEngine.Listener
        var starts = 0
        var closes = 0
        override fun start(listener: NameSpeechEngine.Listener) {
            starts++
            this.listener = listener
            listener.onReady()
        }
        override fun close() { closes++ }
    }

    private class MutableUx : UxRestrictionProvider {
        val current = MutableStateFlow(VehicleUxState.Unrestricted)
        override val state = current.asStateFlow()
    }

    private class MemoryAppearance : ThemePreferenceRepository {
        val current = MutableStateFlow(ThemeMode.Light)
        val writtenModes = mutableListOf<ThemeMode>()
        override val themeMode = current.asStateFlow()
        override suspend fun setThemeMode(mode: ThemeMode) {
            writtenModes += mode
            current.value = mode
        }
    }

    private companion object {
        val sampleEntries = AppEnvironment.previewEntries()
    }
}
