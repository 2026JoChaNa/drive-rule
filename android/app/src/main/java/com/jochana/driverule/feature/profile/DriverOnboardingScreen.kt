package com.jochana.driverule.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error as accessibilityError
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jochana.driverule.domain.speech.NameSpeechError
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jochana.driverule.R
import com.jochana.driverule.ui.components.DemoLabel
import com.jochana.driverule.ui.components.DriveRuleButton
import com.jochana.driverule.ui.components.DriveRuleHeader
import com.jochana.driverule.ui.components.DriveRuleWindow
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

@Composable
fun DriverOnboardingScreen(
    name: String,
    error: String?,
    saving: Boolean,
    isAddingProfile: Boolean,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onThemeClick: () -> Unit,
    themeLabel: String,
    modifier: Modifier = Modifier,
    speech: NameSpeechState = NameSpeechState(),
    onVoiceClick: () -> Unit = {},
) {
    DriveRuleWindow(modifier.testTag("onboarding_screen")) {
        val layout = LocalDriveRuleLayout.current
        val colors = LocalDriveRuleColors.current
        DriveRuleHeader(themeLabel, onThemeClick) {
            DriveRuleButton(
                label = stringResource(if (isAddingProfile) R.string.ui_back else R.string.ui_skip),
                onClick = if (isAddingProfile) onBack else onSkip,
                enabled = !saving,
                outlined = true,
                modifier = Modifier.testTag(if (isAddingProfile) "onboarding_back" else "skip_driver"),
            )
        }
        DemoLabel(Modifier.padding(top = layout.space(14f), bottom = layout.space(18f)))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(layout.space(32f)),
        ) {
            if (layout.compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetupBadge(isAddingProfile)
                    Text(stringResource(R.string.ui_profile_limit), style = layout.text(34.667f), color = colors.textSecondary)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(layout.space(16f))) {
                    SetupBadge(isAddingProfile)
                    Text(stringResource(R.string.ui_profile_limit), style = layout.text(34.667f), color = colors.textSecondary)
                }
            }
            Text(
                stringResource(R.string.ui_name_title),
                style = layout.text(64f, 28f, FontWeight.Bold),
                color = colors.textPrimary,
            )
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = layout.space(593f))
                    .background(colors.picker, RoundedCornerShape(layout.space(42.667f)))
                    .padding(top = layout.space(60f)),
                verticalArrangement = Arrangement.spacedBy(layout.space(28f)),
            ) {
                val inputDescription = stringResource(R.string.ui_name_field)
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    enabled = !saving,
                    singleLine = true,
                    textStyle = layout.text(72f, 30f, FontWeight.Bold).copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!saving && name.isNotBlank()) onNext() }),
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = layout.space(170f).coerceAtLeast(80.dp))
                        .border(
                            width = 2.dp,
                            color = if (error != null) colors.error else colors.accent,
                            shape = RoundedCornerShape(layout.space(32f)),
                        )
                        .padding(horizontal = layout.space(48f), vertical = layout.space(32f))
                        .testTag("driver_name")
                        .semantics {
                            contentDescription = inputDescription
                            if (error != null) accessibilityError(error)
                        },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (name.isEmpty()) {
                                Text(
                                    stringResource(R.string.ui_name_placeholder),
                                    style = layout.text(56f, 24f, FontWeight.Medium),
                                    color = colors.textSecondary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (error != null) {
                    Text(error, color = colors.error, style = layout.text(34f), modifier = Modifier.testTag("name_error"))
                }
                SpeechNameControls(speech, !saving, name.isNotBlank(), onVoiceClick)
                Spacer(Modifier.height(layout.space(48f)))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = layout.space(24f), bottom = layout.space(20f)),
            horizontalArrangement = Arrangement.End,
        ) {
            DriveRuleButton(
                label = stringResource(if (saving) R.string.ui_saving else R.string.ui_next),
                onClick = onNext,
                enabled = !saving && name.isNotBlank(),
                primary = true,
                modifier = Modifier.widthIn(min = layout.space(266.667f)).testTag("save_driver"),
            )
        }
    }
}

@Composable
private fun SetupBadge(isAddingProfile: Boolean) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Text(
        stringResource(if (isAddingProfile) R.string.ui_add_driver_badge else R.string.ui_first_setup_badge),
        modifier = Modifier.background(colors.accentSoft, RoundedCornerShape(layout.space(16f)))
            .padding(horizontal = layout.space(21.333f), vertical = layout.space(10.667f)),
        style = layout.text(34.667f, 16f, FontWeight.Bold),
        color = colors.accentText,
    )
}


@Composable
private fun SpeechNameControls(speech: NameSpeechState, enabled: Boolean, hasName: Boolean, onClick: () -> Unit) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val active = speech.phase != NameSpeechPhase.Idle
    val messageId = when (speech.error) {
        NameSpeechError.Unavailable -> R.string.voice_unavailable
        NameSpeechError.PermissionDenied -> R.string.voice_permission_denied
        NameSpeechError.Microphone -> R.string.voice_microphone_error
        NameSpeechError.NoSpeech -> R.string.voice_no_speech
        NameSpeechError.Timeout -> R.string.voice_timeout
        NameSpeechError.Engine -> R.string.voice_engine_error
        NameSpeechError.UnsupportedLanguage -> R.string.voice_language_error
        null -> when (speech.phase) {
            NameSpeechPhase.Idle -> if (hasName) R.string.voice_name_ready else R.string.voice_idle
            NameSpeechPhase.Connecting -> R.string.voice_connecting
            NameSpeechPhase.Listening -> R.string.voice_listening
            NameSpeechPhase.Processing -> R.string.voice_processing
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(layout.space(20f))) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.space(18.667f))) {
            // Original Figma layout shapes. Status is also conveyed with text.
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.space(6.667f))) {
                listOf(18.667f, 34.667f, 48f, 29.333f, 40f).forEach { barHeight ->
                    Box(Modifier.width(layout.space(8f)).height(layout.space(barHeight))
                        .background(if (active) colors.accentText else colors.textSecondary,
                            RoundedCornerShape(layout.space(4f))))
                }
            }
            Text(
                if (speech.partialText.isNotBlank() && speech.error == null) speech.partialText
                else stringResource(messageId),
                modifier = Modifier.weight(1f).testTag("voice_status"),
                style = layout.text(34f, 16f, FontWeight.Medium),
                color = if (speech.error != null) colors.error else colors.textSecondary,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        DriveRuleButton(
            label = stringResource(if (active) R.string.voice_stop else R.string.voice_retry),
            onClick = onClick, enabled = enabled, icon = "microphone", outlined = true,
            modifier = Modifier.testTag("voice_input"),
        )
    }
}
