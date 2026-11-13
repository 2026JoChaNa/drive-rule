package com.jochana.driverule.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jochana.driverule.R
import com.jochana.driverule.ui.components.DemoLabel
import com.jochana.driverule.ui.components.DriveRuleButton
import com.jochana.driverule.ui.components.DriveRuleHeader
import com.jochana.driverule.ui.components.DriveRuleWindow
import com.jochana.driverule.ui.components.FigmaIcon
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

@Composable
fun HomeScreen(
    driverName: String,
    entries: List<HomePreviewEntry>,
    onChangeDriver: () -> Unit,
    onEntryClick: (HomePreviewEntry) -> Unit,
    onGleoClick: () -> Unit,
    onChangesClick: () -> Unit,
    onThemeClick: () -> Unit,
    themeLabel: String,
    modifier: Modifier = Modifier,
    backgroundControls: @Composable () -> Unit = {},
) {
    DriveRuleWindow(modifier.testTag("home_screen")) {
        val layout = LocalDriveRuleLayout.current
        val colors = LocalDriveRuleColors.current
        DriveRuleHeader(themeLabel, onThemeClick) {
            DriveRuleButton(
                label = stringResource(R.string.ui_driver_change, driverName),
                onClick = onChangeDriver,
                icon = "profile",
                outlined = true,
                modifier = Modifier.testTag("change_driver"),
            )
        }
        DemoLabel(Modifier.padding(top = layout.space(14f), bottom = layout.space(18f)))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(layout.space(32f)),
        ) {
            backgroundControls()
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(layout.space(42.667f)))
                    .padding(
                        horizontal = if (layout.compact) 16.dp else layout.space(64f),
                        vertical = layout.space(56f),
                    ),
                verticalArrangement = Arrangement.spacedBy(layout.space(21.333f)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(layout.space(10.667f))) {
                    Text(
                        stringResource(R.string.ui_home_title),
                        style = layout.text(50.67f, 24f, FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        stringResource(R.string.ui_sample_explanation),
                        style = layout.text(34.67f, 16f),
                        color = colors.textTertiary,
                    )
                }
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.ui_sample_empty), style = layout.text(40f, 20f), color = colors.textSecondary)
                }
                entries.forEach { entry ->
                    GuidancePreviewRow(entry, onClick = { onEntryClick(entry) })
                }
            }
            if (layout.compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeActions(onGleoClick, onChangesClick, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(layout.space(21.333f))) {
                    HomeActions(onGleoClick, onChangesClick)
                }
            }
        }
    }
}

@Composable
private fun HomeActions(onGleoClick: () -> Unit, onChangesClick: () -> Unit, modifier: Modifier = Modifier) {
    DriveRuleButton(
        label = stringResource(R.string.ui_gleo_action),
        onClick = onGleoClick,
        icon = "microphone",
        modifier = modifier.testTag("gleo_button"),
    )
    DriveRuleButton(
        label = stringResource(R.string.ui_changes_action),
        onClick = onChangesClick,
        icon = "arrow",
        modifier = modifier.testTag("changes_button"),
    )
}

@Composable
private fun GuidancePreviewRow(entry: HomePreviewEntry, onClick: () -> Unit) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = layout.space(112f).coerceAtLeast(64.dp))
            .clip(RoundedCornerShape(layout.space(20f)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = layout.space(24f), vertical = layout.space(4f))
            .testTag("sample_entry_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.space(20f)),
    ) {
        Box(
            Modifier.size(layout.space(64f).coerceAtLeast(32.dp))
                .background(colors.accentSoft, RoundedCornerShape(layout.space(16f))),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (entry.icon) {
                HomePreviewIcon.Parking -> "parking"
                HomePreviewIcon.Crosswalk -> "crosswalk"
                HomePreviewIcon.Turn -> "turn"
                HomePreviewIcon.Camera -> "camera"
            }
            FigmaIcon(icon, layout.space(32f).coerceAtLeast(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(layout.space(4f))) {
            Text(entry.title, style = layout.text(40f, 20f, FontWeight.SemiBold), color = colors.textPrimary)
            Text(entry.subtitle, style = layout.text(35f, 16f), color = colors.textSecondary)
            if (layout.compact) StatusBadge(entry.status)
        }
        if (!layout.compact) StatusBadge(entry.status)
        FigmaIcon("chevron", layout.space(32f).coerceAtLeast(16.dp))
    }
}

@Composable
private fun StatusBadge(status: HomePreviewStatus) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val active = status == HomePreviewStatus.Active
    Text(
        stringResource(if (active) R.string.ui_status_active else R.string.ui_status_scheduled),
        modifier = Modifier.background(
            if (active) colors.greenSoft else colors.scheduledSoft,
            RoundedCornerShape(layout.space(12f)),
        ).padding(horizontal = layout.space(16f), vertical = layout.space(8f)),
        style = layout.text(35f, 16f, FontWeight.Bold),
        color = if (active) colors.green else colors.scheduled,
    )
}
