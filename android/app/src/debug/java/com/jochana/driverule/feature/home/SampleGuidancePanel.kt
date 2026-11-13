package com.jochana.driverule.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/** Debug-only controls; the host owns execution, notifications, and their reported state. */
@Composable
fun SampleGuidancePanel(
    status: String,
    running: Boolean,
    postedCount: Int,
    receivedCount: Int,
    backgroundEventCount: Int,
    lastEventTitle: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSample: () -> Unit,
    onSettings: () -> Unit,
) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(layout.space(42.667f)))
            .padding(
                horizontal = if (layout.compact) 16.dp else layout.space(64f),
                vertical = layout.space(40f),
            )
            .testTag("background_panel"),
        verticalArrangement = Arrangement.spacedBy(layout.space(24f)),
    ) {
        Text(
            stringResource(R.string.background_panel_title),
            style = layout.text(48f, 24f, FontWeight.SemiBold),
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.background_panel_description),
            style = layout.text(34.667f),
            color = colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(layout.space(8f))) {
            Text(
                stringResource(R.string.background_panel_received_count, receivedCount, backgroundEventCount),
                style = layout.text(40f, 20f, FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.testTag("background_received_count"),
            )
            Text(
                if (lastEventTitle == null) stringResource(R.string.background_panel_no_event)
                else stringResource(R.string.background_panel_last_event, lastEventTitle),
                style = layout.text(34.667f),
                color = colors.textSecondary,
                modifier = Modifier.testTag("background_last_event"),
            )
            Text(
                stringResource(R.string.background_panel_status, status),
                style = layout.text(34.667f),
                color = colors.textSecondary,
                modifier = Modifier.testTag("background_status")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                stringResource(R.string.background_panel_posted_count, postedCount),
                style = layout.text(30f, 16f),
                color = colors.textTertiary,
                modifier = Modifier.testTag("background_posted_count"),
            )
        }
        Text(
            stringResource(R.string.background_panel_event_hint),
            style = layout.text(30f, 16f),
            color = colors.textTertiary,
        )
        if (layout.compact) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SampleGuidanceActions(running, onStart, onStop, onSample, onSettings, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.space(24f)),
            ) {
                SampleGuidanceActions(running, onStart, onStop, onSample, onSettings, Modifier.weight(1f))
            }
        }
        Text(
            stringResource(R.string.background_panel_notification_hint),
            style = layout.text(30f, 16f),
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun SampleGuidanceActions(
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSample: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    DriveRuleButton(
        label = stringResource(if (running) R.string.background_panel_stop else R.string.background_panel_start),
        onClick = if (running) onStop else onStart,
        primary = true,
        modifier = modifier.testTag(if (running) "background_stop" else "background_start"),
    )
    if (running) {
        DriveRuleButton(
            label = stringResource(R.string.background_panel_emit),
            onClick = onSample,
            outlined = true,
            modifier = modifier.testTag("background_emit"),
        )
    }
    DriveRuleButton(
        label = stringResource(R.string.background_panel_settings),
        onClick = onSettings,
        outlined = true,
        modifier = modifier.testTag("background_settings"),
    )
}
