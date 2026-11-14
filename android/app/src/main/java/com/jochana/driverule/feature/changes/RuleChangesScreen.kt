package com.jochana.driverule.feature.changes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jochana.driverule.R
import com.jochana.driverule.ui.components.DemoLabel
import com.jochana.driverule.ui.components.DriveRuleBrand
import com.jochana.driverule.ui.components.DriveRuleButton
import com.jochana.driverule.ui.components.DriveRuleWindow
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.components.RuleRow
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/** Figma 00-1 `달라지는 교통법규 · 월별` (1851:29155). Reached from the home banner or button. */
@Composable
fun RuleChangesScreen(
    changes: List<RuleChangePreview>,
    onHome: () -> Unit,
    onChangeClick: (RuleChangePreview) -> Unit,
    modifier: Modifier = Modifier,
    sample: Boolean = false,
) {
    BackHandler(onBack = onHome)
    val sections = groupRuleChanges(changes)
    DriveRuleWindow(modifier.testTag("rule_changes_screen"), bottomPadding = 53.333f) {
        val layout = LocalDriveRuleLayout.current
        val colors = LocalDriveRuleColors.current
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DriveRuleBrand()
                DriveRuleButton(
                    label = stringResource(R.string.changes_home),
                    onClick = onHome,
                    icon = "home",
                    modifier = Modifier.testTag("rule_changes_home"),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = layout.space(20f)),
                thickness = layout.space(2f).coerceAtLeast(1.dp),
                color = colors.border,
            )
        }
        Column(
            Modifier.padding(top = layout.space(32f), bottom = layout.space(32f)),
            verticalArrangement = Arrangement.spacedBy(layout.space(10.667f)),
        ) {
            Text(
                stringResource(R.string.changes_title),
                style = layout.text(64f, 28f, FontWeight.Bold),
                color = colors.textPrimary,
            )
            Text(
                stringResource(R.string.changes_subtitle),
                style = layout.text(34.67f, 16f),
                color = colors.textTertiary,
            )
            if (sample) DemoLabel()
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(layout.space(42.667f)))
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (layout.compact) 16.dp else layout.space(64f),
                    vertical = layout.space(48f),
                )
                .testTag("rule_changes_list"),
            verticalArrangement = Arrangement.spacedBy(layout.space(18.667f)),
        ) {
            if (changes.isEmpty()) {
                Text(stringResource(R.string.changes_empty), style = layout.text(40f, 20f), color = colors.textSecondary)
            }
            Section(R.string.changes_upcoming, sections.upcoming, onChangeClick)
            Section(R.string.changes_recent, sections.recent, onChangeClick)
        }
    }
}

@Composable
private fun ColumnScope.Section(title: Int, months: List<RuleChangeMonth>, onClick: (RuleChangePreview) -> Unit) {
    if (months.isEmpty()) return
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Text(
        stringResource(title),
        style = layout.text(50.67f, 24f, FontWeight.SemiBold),
        color = colors.textPrimary,
    )
    months.forEach { month ->
        Text(
            stringResource(R.string.changes_month, month.year, month.month),
            modifier = Modifier.testTag("rule_changes_month_${month.year}_${month.month}"),
            style = layout.text(34.67f, 16f, FontWeight.Bold).copy(letterSpacing = layout.space(1.3868f).value.sp),
            color = colors.textSecondary,
        )
        month.items.forEach { change ->
            RuleRow(
                title = change.title,
                subtitle = stringResource(
                    R.string.changes_row_subtitle,
                    change.effectiveOn.monthValue, change.effectiveOn.dayOfMonth, change.lawNo,
                ),
                status = change.status,
                icon = change.icon.asset,
                onClick = { onClick(change) },
                modifier = Modifier.testTag("rule_change_${change.id}"),
            )
        }
    }
}
