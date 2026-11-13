package com.jochana.driverule.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jochana.driverule.R
import com.jochana.driverule.ui.components.DriveRuleButton
import com.jochana.driverule.ui.components.DriveRuleHeader
import com.jochana.driverule.ui.components.DriveRuleWindow
import com.jochana.driverule.ui.components.FigmaIcon
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/**
 * Figma 01-3 app windows: night 1844:32191, day 1870:30812.
 * This is an explanation screen; callbacks do not imply an Android permission grant.
 */
@Composable
fun PermissionGuideScreen(
    onAllow: () -> Unit,
    onLater: () -> Unit,
    onThemeClick: () -> Unit,
    themeLabel: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: String? = null,
) {
    DriveRuleWindow(modifier.testTag("permission_guide_screen")) {
        val layout = LocalDriveRuleLayout.current
        val colors = LocalDriveRuleColors.current
        DriveRuleHeader(themeLabel, onThemeClick) {
            DriveRuleButton(
                label = stringResource(R.string.permission_guide_later),
                onClick = onLater,
                enabled = !busy,
                outlined = true,
                modifier = Modifier.testTag("permission_later"),
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // The footer stays at the bottom when there is room, and scrolls with the copy
            // in short windows or at large font scales so every action remains reachable.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = layout.space(32f)),
                    verticalArrangement = Arrangement.spacedBy(layout.space(32f)),
                ) {
                    PermissionEyebrow()
                    Text(
                        stringResource(R.string.permission_guide_title),
                        modifier = Modifier.semantics { heading() },
                        style = layout.text(64f, 28f, FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(layout.space(56f))) {
                        PermissionRow(
                            icon = "permission_bell",
                            title = stringResource(R.string.permission_guide_notifications_title),
                            description = stringResource(R.string.permission_guide_notifications_description),
                        )
                        PermissionRow(
                            icon = "permission_road",
                            title = stringResource(R.string.permission_guide_vehicle_title),
                            description = stringResource(R.string.permission_guide_vehicle_description),
                        )
                        PermissionRow(
                            icon = "permission_microphone",
                            title = stringResource(R.string.permission_guide_voice_title),
                            description = stringResource(R.string.permission_guide_voice_description),
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = layout.space(20f)),
                    verticalArrangement = Arrangement.spacedBy(layout.space(24f)),
                ) {
                    if (error != null) {
                        Text(
                            text = error,
                            color = colors.error,
                            style = layout.text(34f),
                            modifier = Modifier.testTag("permission_error")
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    PermissionFooter(onAllow = onAllow, busy = busy)
                }
            }
        }
    }
}

@Composable
private fun PermissionEyebrow() {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val badge: @Composable () -> Unit = {
        Text(
            stringResource(R.string.permission_guide_badge),
            modifier = Modifier.background(colors.secondaryButton, RoundedCornerShape(layout.space(16f)))
                .padding(horizontal = layout.space(21.333f), vertical = layout.space(10.667f)),
            style = layout.text(34.667f, 16f, FontWeight.Bold),
            color = colors.textSecondary,
        )
    }
    if (layout.compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            badge()
            Text(
                stringResource(R.string.permission_guide_helper),
                style = layout.text(34.667f),
                color = colors.textSecondary,
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.space(16f)),
        ) {
            badge()
            Text(
                stringResource(R.string.permission_guide_helper),
                modifier = Modifier.weight(1f),
                style = layout.text(34.667f),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PermissionRow(icon: String, title: String, description: String) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.space(40f)),
    ) {
        Box(
            Modifier.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(layout.space(28f)))
                .padding(layout.space(28f)),
            contentAlignment = Alignment.Center,
        ) {
            FigmaIcon(icon, layout.space(51.2f))
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(layout.space(8f)),
        ) {
            Text(title, style = layout.text(48f, 22f, FontWeight.Bold), color = colors.textPrimary)
            Text(description, style = layout.text(37f), color = colors.textSecondary)
        }
    }
}

@Composable
private fun PermissionFooter(onAllow: () -> Unit, busy: Boolean) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val continueAction: @Composable (Modifier) -> Unit = { modifier ->
        DriveRuleButton(
            label = stringResource(if (busy) R.string.permission_guide_busy else R.string.permission_guide_continue),
            onClick = onAllow,
            enabled = !busy,
            primary = true,
            modifier = modifier.widthIn(min = layout.space(266.667f)).testTag("permission_allow"),
        )
    }
    if (layout.compact) {
        Column(verticalArrangement = Arrangement.spacedBy(layout.space(24f))) {
            Text(
                stringResource(R.string.permission_guide_footer),
                style = layout.text(37.333f, 16f, FontWeight.SemiBold),
                color = colors.textSecondary,
            )
            continueAction(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.space(32f)),
        ) {
            Text(
                stringResource(R.string.permission_guide_footer),
                modifier = Modifier.weight(1f),
                style = layout.text(37.333f, 16f, FontWeight.SemiBold),
                color = colors.textSecondary,
            )
            continueAction(Modifier)
        }
    }
}
