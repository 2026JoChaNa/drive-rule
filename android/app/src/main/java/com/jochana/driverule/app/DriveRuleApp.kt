package com.jochana.driverule.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jochana.driverule.R
import com.jochana.driverule.core.model.ThemeMode
import com.jochana.driverule.core.model.VehicleUxState
import com.jochana.driverule.domain.repository.CardErrorReportRepository
import com.jochana.driverule.domain.repository.CardReportOutcome
import com.jochana.driverule.feature.card.CardDetailPreview
import com.jochana.driverule.feature.card.CardDetailScreen
import com.jochana.driverule.feature.card.CardErrorReportViewModel
import com.jochana.driverule.feature.changes.RuleChangePreview
import com.jochana.driverule.feature.changes.RuleChangesScreen
import com.jochana.driverule.feature.changes.preDepartureAlert
import com.jochana.driverule.feature.changes.upcomingRulesBanner
import com.jochana.driverule.feature.predeparture.PreDepartureAlert
import com.jochana.driverule.feature.home.HomePreviewEntry
import com.jochana.driverule.feature.home.HomeScreen
import com.jochana.driverule.feature.profile.DriverChangeSheet
import com.jochana.driverule.feature.profile.DriverFlowUiState
import com.jochana.driverule.feature.profile.DriverFlowViewModel
import com.jochana.driverule.feature.profile.VoiceDriverOnboarding
import com.jochana.driverule.feature.profile.PermissionGuideScreen
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.feature.profile.ProfileInputError
import com.jochana.driverule.ui.components.DriveRuleMotion
import com.jochana.driverule.ui.theme.DriveRuleTheme

@Composable
fun DriveRuleApp(
    appContainer: AppContainer,
    onThemeChanged: (Boolean) -> Unit = {},
    backgroundControls: @Composable () -> Unit = {},
) {
    val factory = remember(appContainer) {
        viewModelFactory {
            initializer {
                DriverFlowViewModel(appContainer.driverProfileRepository,
                    appContainer.uxRestrictionProvider, appContainer.themePreferenceRepository)
            }
        }
    }
    val speechEngineFactory = remember(appContainer) { { appContainer.createNameSpeechEngine() } }
    val model: DriverFlowViewModel = viewModel(factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val dark = when (state.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    SideEffect { onThemeChanged(dark) }
    DriveRuleTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DriverFlowContent(state, model, appContainer.previewEntries,
                Modifier.safeDrawingPadding().imePadding().fillMaxSize(), speechEngineFactory,
                backgroundControls, appContainer.cardDetails, appContainer.cardErrorReportRepository,
                appContainer.ruleChanges, appContainer.preDepartureAlertGate)
        }
    }
}

@Composable
internal fun DriverFlowContent(
    state: DriverFlowUiState,
    model: DriverFlowViewModel,
    entries: List<HomePreviewEntry>,
    modifier: Modifier = Modifier,
    speechEngineFactory: (() -> NameSpeechEngine)? = null,
    backgroundControls: @Composable () -> Unit = {},
    cardDetails: Map<String, CardDetailPreview> = emptyMap(),
    cardErrorReports: CardErrorReportRepository = CardErrorReportRepository { CardReportOutcome.RetryLater },
    ruleChanges: List<RuleChangePreview> = emptyList(),
    preDepartureAlertGate: PreDepartureAlertGate = remember { PreDepartureAlertGate() },
) {
    if (state.vehicleUxState != VehicleUxState.Unrestricted) {
        StatusScreen(stringResource(if (state.vehicleUxState == VehicleUxState.Restricted)
            R.string.predeparture_driving_restricted else R.string.predeparture_vehicle_unavailable), modifier)
        return
    }
    if (state.loading) {
        Column(modifier, Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
        return
    }
    if (state.storageError) {
        Column(modifier.padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(stringResource(R.string.profile_load_failed))
            Button(onClick = model::retryProfiles) { Text(stringResource(R.string.try_again)) }
        }
        return
    }
    // Conditional scope removes dialogs and input focus immediately when UX becomes restricted.
    AllowedDriverContent(state, model, entries, modifier, speechEngineFactory, backgroundControls,
        cardDetails, cardErrorReports, ruleChanges, preDepartureAlertGate)
}

@Composable
private fun AllowedDriverContent(state: DriverFlowUiState, model: DriverFlowViewModel,
    entries: List<HomePreviewEntry>, modifier: Modifier, speechEngineFactory: (() -> NameSpeechEngine)?,
    backgroundControls: @Composable () -> Unit, cardDetails: Map<String, CardDetailPreview>,
    cardErrorReports: CardErrorReportRepository, ruleChanges: List<RuleChangePreview>,
    preDepartureAlertGate: PreDepartureAlertGate) {
    var showTheme by remember { mutableStateOf(false) }
    var showDrivers by remember { mutableStateOf(false) }
    var showGleo by remember { mutableStateOf(false) }
    var showChanges by remember { mutableStateOf(false) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    // Rows without a card detail keep the sample explanation dialog.
    var sampleDetailTitle by remember { mutableStateOf<String?>(null) }
    val openRow: (String?, String) -> Unit = { cardId, title ->
        if (cardId != null && cardDetails.containsKey(cardId)) selectedCardId = cardId else sampleDetailTitle = title
    }
    val upcomingBanner = remember(ruleChanges) { upcomingRulesBanner(ruleChanges) }
    val alertChange = remember(ruleChanges) { preDepartureAlert(ruleChanges) }
    var showAlert by remember { mutableStateOf(false) }
    val themeLabel = stringResource(themeLabelId(state.themeMode))
    val error = profileError(state.error)
    val active = state.activeProfile
    BackHandler(state.addingProfile && !state.saving) { model.cancelAdding() }

    BoxWithConstraints(modifier) {
        Column(Modifier.fillMaxSize()) {
            if (state.themeError) Text(stringResource(R.string.theme_save_failed),
                Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error)
            val screen = when {
                active == null || state.addingProfile -> AppScreen.Onboarding
                state.permissionGuidePending -> AppScreen.PermissionGuide
                selectedCardId?.let { cardDetails[it] } != null -> AppScreen.Card(requireNotNull(selectedCardId))
                showChanges -> AppScreen.RuleChanges
                else -> AppScreen.Home
            }
            // Short crossfade between screens (app default; Figma defines no screen transitions).
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(DriveRuleMotion.screen()) togetherWith fadeOut(DriveRuleMotion.exit()) },
                contentKey = { it.key },
                label = "app-screen",
                modifier = Modifier.weight(1f),
            ) { target ->
                when (target) {
                    AppScreen.Onboarding -> VoiceDriverOnboarding(
                        state = state, model = model, error = error,
                        onThemeClick = { showTheme = true }, themeLabel = themeLabel,
                        engineFactory = speechEngineFactory, modifier = Modifier.fillMaxSize(),
                    )
                    AppScreen.PermissionGuide -> PermissionGuideScreen(
                        onAllow = model::completePermissionGuide,
                        onLater = model::completePermissionGuide,
                        onThemeClick = { showTheme = true }, themeLabel = themeLabel,
                        busy = state.saving, error = error, modifier = Modifier.fillMaxSize(),
                    )
                    // Closing returns to the screen that opened it (home or the monthly list).
                    is AppScreen.Card -> cardDetails[target.id]?.let { card ->
                        CardDetailRoute(card, cardErrorReports, onClose = { selectedCardId = null },
                            modifier = Modifier.fillMaxSize())
                    }
                    AppScreen.RuleChanges -> RuleChangesScreen(
                        changes = ruleChanges,
                        onHome = { showChanges = false },
                        onChangeClick = { openRow(it.cardId, it.title) },
                        sample = ruleChanges.isNotEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppScreen.Home -> Column(Modifier.fillMaxSize()) {
                        // App start stands in for ignition: the first home of this process claims the alert.
                        LaunchedEffect(Unit) {
                            if (alertChange != null && preDepartureAlertGate.claim()) showAlert = true
                        }
                        if (error != null) Text(error, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error)
                        // `active` is non-null whenever Home is the target; keep the last name while fading out.
                        val name = active?.name.orEmpty()
                        HomeScreen(name, entries,
                            onChangeDriver = { if (!state.saving) showDrivers = true },
                            onEntryClick = { openRow(it.id, it.title) }, onGleoClick = { showGleo = true },
                            onChangesClick = { showChanges = true }, onThemeClick = { showTheme = true },
                            themeLabel = themeLabel, modifier = Modifier.weight(1f),
                            backgroundControls = backgroundControls,
                            upcomingBanner = upcomingBanner)
                    }
                }
            }
        }
        val homeShowing = active != null && !state.addingProfile && !state.permissionGuidePending &&
            selectedCardId == null && !showChanges
        // Leaving home ends the alert; it is not shown again in this process.
        LaunchedEffect(homeShowing) { if (!homeShowing) showAlert = false }
        // Rises slightly and fades in; fades out when it closes (app default, see DriveRuleMotion).
        AnimatedVisibility(
            visible = showAlert && homeShowing && alertChange != null,
            enter = fadeIn(DriveRuleMotion.enter()) + slideInVertically(DriveRuleMotion.enter()) { (it * 0.04f).toInt() },
            exit = fadeOut(DriveRuleMotion.exit()),
            modifier = Modifier.fillMaxSize(),
        ) {
            alertChange?.let { change ->
                PreDepartureAlert(change, active?.name.orEmpty(), onDismiss = { showAlert = false })
            }
        }
        if (showDrivers && !state.permissionGuidePending) DriverChangeSheet(
            profiles = state.profiles,
            activeProfileId = active?.id,
            // Shared UI fixture count, never a persisted or per-driver heard-history total.
            sampleGuidanceCount = entries.size.takeIf { it > 0 },
            saving = state.saving,
            availableWidth = maxWidth,
            availableHeight = maxHeight,
            onSelect = { model.selectProfile(it); showDrivers = false },
            onAdd = { model.addProfile(); showDrivers = false },
            onDismiss = { showDrivers = false },
        )
    }
    val dialogScale = (LocalConfiguration.current.screenWidthDp / 1687f).coerceIn(0.5f, 1.16f)
    val dialogBody = (34f * dialogScale).coerceAtLeast(18f)
    val dialogTitle = (48f * dialogScale).coerceAtLeast(24f)
    val dialogTypography = MaterialTheme.typography.let { base ->
        base.copy(
            titleLarge = base.titleLarge.copy(fontSize = dialogTitle.sp, lineHeight = (dialogTitle * 1.3f).sp),
            bodyMedium = base.bodyMedium.copy(fontSize = dialogBody.sp, lineHeight = (dialogBody * 1.4f).sp),
            labelLarge = base.labelLarge.copy(fontSize = dialogBody.sp, lineHeight = (dialogBody * 1.3f).sp),
        )
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides (96f * dialogScale).coerceAtLeast(56f).dp) {
        MaterialTheme(typography = dialogTypography) {
            if (showTheme) AlertDialog(
                onDismissRequest = { showTheme = false }, title = { Text(stringResource(R.string.theme_title)) },
                text = { Column {
                    ThemeMode.entries.forEach { mode ->
                        TextButton(onClick = { model.setTheme(mode); showTheme = false },
                            modifier = Modifier.fillMaxWidth().testTag("theme_${mode.name.lowercase()}")) {
                            Text(stringResource(themeLabelId(mode)))
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { showTheme = false }) { Text(stringResource(R.string.close)) } },
            )
            if (showGleo) InfoDialog(stringResource(R.string.gleo_coming_title), stringResource(R.string.gleo_coming_body)) { showGleo = false }
            sampleDetailTitle?.let { title ->
                InfoDialog(title, stringResource(R.string.sample_details_body)) { sampleDetailTitle = null }
            }
        }
    }
}

@Composable
private fun CardDetailRoute(
    card: CardDetailPreview,
    repository: CardErrorReportRepository,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val reportModel: CardErrorReportViewModel = viewModel(
        key = "card-report-${card.id}",
        factory = viewModelFactory {
            initializer { CardErrorReportViewModel(card.evidence.reportTarget, repository) }
        },
    )
    val reportState by reportModel.state.collectAsStateWithLifecycle()
    CardDetailScreen(
        card = card,
        reportState = reportState,
        onReport = reportModel::report,
        onOpenSource = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (missing: ActivityNotFoundException) {
                Log.w("DriveRuleCard", "No activity can open the law source", missing)
            }
        },
        onClose = onClose,
        // Only the debug fixtures exist today; a real published copy carries a report target.
        sample = card.evidence.reportTarget == null,
        modifier = modifier,
    )
}

@Composable
private fun StatusScreen(message: String, modifier: Modifier) {
    Column(modifier.padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

private fun themeLabelId(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

@Composable
private fun profileError(error: ProfileInputError?): String? = error?.let {
    stringResource(when (it) {
        ProfileInputError.Empty -> R.string.profile_error_empty
        ProfileInputError.TooLong -> R.string.profile_error_long
        ProfileInputError.LimitReached -> R.string.profile_error_limit
        ProfileInputError.NotFound -> R.string.profile_error_not_found
        ProfileInputError.Storage -> R.string.profile_error_storage
    })
}

/** Top-level screens of the driver flow, used to key the screen transition. */
private sealed interface AppScreen {
    val key: String
    data object Onboarding : AppScreen { override val key = "onboarding" }
    data object PermissionGuide : AppScreen { override val key = "permission" }
    data object Home : AppScreen { override val key = "home" }
    data object RuleChanges : AppScreen { override val key = "changes" }
    data class Card(val id: String) : AppScreen { override val key = "card:$id" }
}
