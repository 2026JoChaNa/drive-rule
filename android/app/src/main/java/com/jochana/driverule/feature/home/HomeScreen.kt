package com.jochana.driverule.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import com.jochana.driverule.feature.changes.UpcomingRulesBanner
import com.jochana.driverule.R
import com.jochana.driverule.ui.components.DemoLabel
import com.jochana.driverule.ui.components.DriveRuleButton
import com.jochana.driverule.ui.components.DriveRuleHeader
import com.jochana.driverule.ui.components.DriveRuleWindow
import com.jochana.driverule.ui.components.FigmaIcon
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.components.RuleRow
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
    upcomingBanner: UpcomingRulesBanner? = null,
    onUpcomingBannerClick: () -> Unit = onChangesClick,
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
            upcomingBanner?.let { UpcomingRulesBannerRow(it, onUpcomingBannerClick) }
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
                    RuleRow(
                        title = entry.title,
                        subtitle = entry.subtitle,
                        status = entry.status,
                        icon = when (entry.icon) {
                            HomePreviewIcon.Parking -> "parking"
                            HomePreviewIcon.Crosswalk -> "crosswalk"
                            HomePreviewIcon.Turn -> "turn"
                            HomePreviewIcon.Camera -> "camera"
                        },
                        onClick = { onEntryClick(entry) },
                        modifier = Modifier.testTag("sample_entry_${entry.id}"),
                    )
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

/** Figma 00-b `곧 바뀌는 규칙` (1862:30505). Opens the monthly list (00-1). */
@Composable
private fun UpcomingRulesBannerRow(banner: UpcomingRulesBanner, onClick: () -> Unit) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val date = banner.effectiveOn
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(layout.space(42.667f)))
            .background(colors.border)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = layout.space(40f), vertical = layout.space(28f))
            .testTag("upcoming_rules_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.space(24f)),
    ) {
        Text(
            stringResource(R.string.changes_banner_badge, banner.items.size),
            modifier = Modifier
                .background(colors.scheduledSoft, RoundedCornerShape(layout.space(16f)))
                .padding(horizontal = layout.space(21.333f), vertical = layout.space(10.667f)),
            style = layout.text(34.667f, 16f, FontWeight.Bold).copy(letterSpacing = layout.space(1.3867f).value.sp),
            color = colors.scheduled,
            maxLines = 1,
        )
        Text(
            stringResource(
                R.string.changes_banner_summary,
                banner.items.joinToString(" · ") { it.title },
                date.year, date.monthValue, date.dayOfMonth,
            ),
            modifier = Modifier.weight(1f),
            style = layout.text(37.33f, 18f, FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        FigmaIcon("banner_chevron", layout.space(43f).coerceAtLeast(20.dp))
    }
}
