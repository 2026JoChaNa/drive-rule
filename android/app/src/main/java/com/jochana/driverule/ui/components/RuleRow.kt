package com.jochana.driverule.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jochana.driverule.R
import com.jochana.driverule.feature.home.HomePreviewStatus
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/** Figma `Rule row` (1676:1741): icon tile, two text lines, status badge and chevron. */
@Composable
fun RuleRow(
    title: String,
    subtitle: String,
    status: HomePreviewStatus,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = layout.space(112f).coerceAtLeast(64.dp))
            .clip(RoundedCornerShape(layout.space(20f)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = layout.space(24f), vertical = layout.space(4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.space(20f)),
    ) {
        Box(
            Modifier.size(layout.space(64f).coerceAtLeast(32.dp))
                .background(colors.accentSoft, RoundedCornerShape(layout.space(16f))),
            contentAlignment = Alignment.Center,
        ) {
            FigmaIcon(icon, layout.space(32f).coerceAtLeast(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(layout.space(4f))) {
            Text(title, style = layout.text(40f, 20f, FontWeight.SemiBold), color = colors.textPrimary, maxLines = 2)
            Text(subtitle, style = layout.text(35f, 16f), color = colors.textSecondary)
            if (layout.compact) RuleStatusBadge(status)
        }
        if (!layout.compact) RuleStatusBadge(status)
        FigmaIcon("chevron", layout.space(32f).coerceAtLeast(16.dp))
    }
}

/** Badge variant inside a Rule row (35 px text, 12 px radius). */
@Composable
fun RuleStatusBadge(status: HomePreviewStatus) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val active = status == HomePreviewStatus.Active
    Text(
        stringResource(if (active) R.string.ui_status_active else R.string.ui_status_scheduled),
        modifier = Modifier.background(
            if (active) colors.greenSoft else colors.scheduledSoft,
            RoundedCornerShape(layout.space(12f)),
        ).padding(horizontal = layout.space(16f), vertical = layout.space(8f)),
        style = layout.text(35f, 16f, FontWeight.Bold).copy(letterSpacing = layout.space(1.4f).value.sp),
        color = if (active) colors.green else colors.scheduled,
        maxLines = 1,
    )
}
