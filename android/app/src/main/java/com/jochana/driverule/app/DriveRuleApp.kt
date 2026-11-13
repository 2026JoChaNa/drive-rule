package com.jochana.driverule.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
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
import com.jochana.driverule.feature.home.HomePreviewEntry
import com.jochana.driverule.feature.home.HomePreviewStatus
import com.jochana.driverule.feature.home.HomeScreen
import com.jochana.driverule.feature.profile.DriverChangeSheet
import com.jochana.driverule.feature.profile.DriverFlowUiState
import com.jochana.driverule.feature.profile.DriverFlowViewModel
import com.jochana.driverule.feature.profile.VoiceDriverOnboarding
import com.jochana.driverule.feature.profile.PermissionGuideScreen
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.feature.profile.ProfileInputError
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
                backgroundControls, appContainer.cardDetails, appContainer.cardErrorReportRepository)
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
        cardDetails, cardErrorReports)
}

@Composable
private fun AllowedDriverContent(state: DriverFlowUiState, model: DriverFlowViewModel,
    entries: List<HomePreviewEntry>, modifier: Modifier, speechEngineFactory: (() -> NameSpeechEngine)?,
    backgroundControls: @Composable () -> Unit, cardDetails: Map<String, CardDetailPreview>,
    cardErrorReports: CardErrorReportRepository) {
    var showTheme by remember { mutableStateOf(false) }
    var showDrivers by remember { mutableStateOf(false) }
    var showGleo by remember { mutableStateOf(false) }
    var showChanges by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<HomePreviewEntry?>(null) }
    val themeLabel = stringResource(themeLabelId(state.themeMode))
    val error = profileError(state.error)
    val active = state.activeProfile
    BackHandler(state.addingProfile && !state.saving) { model.cancelAdding() }

    BoxWithConstraints(modifier) {
        Column(Modifier.fillMaxSize()) {
            if (state.themeError) Text(stringResource(R.string.theme_save_failed),
                Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error)
            if (active == null || state.addingProfile) {
                VoiceDriverOnboarding(
                    state = state, model = model, error = error,
                    onThemeClick = { showTheme = true }, themeLabel = themeLabel,
                    engineFactory = speechEngineFactory, modifier = Modifier.weight(1f),
                )
            } else if (state.permissionGuidePending) {
                PermissionGuideScreen(
                    onAllow = model::completePermissionGuide,
                    onLater = model::completePermissionGuide,
                    onThemeClick = { showTheme = true }, themeLabel = themeLabel,
                    busy = state.saving, error = error, modifier = Modifier.weight(1f),
                )
            } else if (selectedEntry?.let { cardDetails[it.id] } != null) {
                val card = requireNotNull(cardDetails[requireNotNull(selectedEntry).id])
                CardDetailRoute(card, cardErrorReports, onClose = { selectedEntry = null },
                    modifier = Modifier.weight(1f))
            } else {
                if (error != null) Text(error, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error)
                HomeScreen(active.name, entries,
                    onChangeDriver = { if (!state.saving) showDrivers = true },
                    onEntryClick = { selectedEntry = it }, onGleoClick = { showGleo = true },
                    onChangesClick = { showChanges = true }, onThemeClick = { showTheme = true },
                    themeLabel = themeLabel, modifier = Modifier.weight(1f),
                    backgroundControls = backgroundControls)
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
            if (showChanges) InfoDialog(stringResource(R.string.sample_changes_title),
                entries.filter { it.status == HomePreviewStatus.Scheduled }.joinToString("\n") { it.title } +
                    "\n\n" + stringResource(R.string.sample_details_body)) { showChanges = false }
            selectedEntry?.takeIf { cardDetails[it.id] == null }?.let { entry ->
                InfoDialog(entry.title, stringResource(R.string.sample_details_body)) { selectedEntry = null }
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
