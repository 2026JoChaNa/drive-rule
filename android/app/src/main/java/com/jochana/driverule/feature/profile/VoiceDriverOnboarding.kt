package com.jochana.driverule.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jochana.driverule.R
import com.jochana.driverule.domain.speech.NameSpeechEngine
import com.jochana.driverule.domain.speech.NameSpeechError

/** Speech belongs to the visible name-entry screen, never the application lifetime. */
@Composable
internal fun VoiceDriverOnboarding(
    state: DriverFlowUiState,
    model: DriverFlowViewModel,
    error: String?,
    themeLabel: String,
    onThemeClick: () -> Unit,
    engineFactory: (() -> NameSpeechEngine)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(engineFactory, scope) {
        NameSpeechController(engineFactory ?: { UnavailableNameSpeechEngine() }, scope)
    }
    val speech by controller.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyboard = LocalSoftwareKeyboardController.current
    val defaultName = stringResource(R.string.default_driver_name)
    DisposableEffect(lifecycle, controller, model) {
        fun startAutomaticallyIfReady() {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                model.consumeAutomaticNameSpeechStart()) {
                controller.start()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startAutomaticallyIfReady()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.cancel()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        startAutomaticallyIfReady()
        onDispose {
            lifecycle.removeObserver(observer)
            controller.close()
        }
    }
    LaunchedEffect(speech.result) {
        val result = speech.result ?: return@LaunchedEffect
        val current = model.uiState.value
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            controller.state.value.result == result && current.interactionAllowed && !current.saving &&
            (current.activeProfile == null || current.addingProfile) && !current.permissionGuidePending) {
            model.changeName(result.text)
        }
        controller.consumeResult(result.id)
    }
    BackHandler(speech.phase != NameSpeechPhase.Idle) { controller.cancel() }
    DriverOnboardingScreen(
        name = state.name,
        error = error,
        saving = state.saving,
        isAddingProfile = state.addingProfile,
        onNameChange = { controller.cancel(); model.changeName(it) },
        onNext = { controller.cancel(); keyboard?.hide(); model.saveName() },
        onSkip = { controller.cancel(); keyboard?.hide(); model.skip(defaultName) },
        onBack = { controller.cancel(); model.cancelAdding() },
        onThemeClick = { controller.cancel(); onThemeClick() },
        themeLabel = themeLabel,
        speech = speech,
        onVoiceClick = {
            if (speech.phase != NameSpeechPhase.Idle) controller.cancel()
            else if (model.uiState.value.interactionAllowed && !model.uiState.value.saving &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                keyboard?.hide()
                controller.start()
            }
        },
        modifier = modifier,
    )
}

private class UnavailableNameSpeechEngine : NameSpeechEngine {
    override fun start(listener: NameSpeechEngine.Listener) = listener.onError(NameSpeechError.Unavailable)
    override fun close() = Unit
}
