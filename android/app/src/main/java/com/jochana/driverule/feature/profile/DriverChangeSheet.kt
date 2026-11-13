package com.jochana.driverule.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jochana.driverule.R
import com.jochana.driverule.core.model.DriverProfile
import com.jochana.driverule.domain.profile.ProfileNamePolicy
import com.jochana.driverule.ui.components.DriveRuleLayout
import com.jochana.driverule.ui.theme.LocalDriveRuleColors
import java.text.BreakIterator

/** Figma 09. A modal inside the app window; it never draws over vehicle system UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverChangeSheet(
    profiles: List<DriverProfile>,
    activeProfileId: Long?,
    sampleGuidanceCount: Int?,
    saving: Boolean,
    availableWidth: Dp,
    availableHeight: Dp,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDriveRuleColors.current
    val layout = DriveRuleLayout(
        scale = (availableWidth.value / 1687f).coerceIn(0.46f, 1.16f),
        compact = availableWidth.value / LocalDensity.current.fontScale < 620f,
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val horizontalPadding = if (layout.compact) 20.dp else layout.space(64f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("driver_sheet"),
        sheetState = sheetState,
        sheetMaxWidth = availableWidth,
        shape = RoundedCornerShape(
            topStart = layout.space(53.333f), topEnd = layout.space(53.333f),
            bottomStart = 0.dp, bottomEnd = 0.dp,
        ),
        containerColor = colors.border,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = layout.space(26.667f), bottom = layout.space(32f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(layout.space(96f), layout.space(8f))
                    .background(colors.textTertiary, RoundedCornerShape(layout.space(4f))))
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = (availableHeight * 0.92f - layout.space(66.667f)).coerceAtLeast(160.dp))
                .padding(horizontal = horizontalPadding),
        ) {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.space(32f)),
            ) {
                Text(stringResource(R.string.driver_picker_title), Modifier.weight(1f),
                    style = layout.text(50.667f, 22f, FontWeight.SemiBold))
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("driver_sheet_close")
                        .heightIn(min = layout.space(117.333f).coerceAtLeast(56.dp))
                        .widthIn(min = layout.space(266.667f)),
                    shape = RoundedCornerShape(layout.space(26.667f)),
                    color = Color.Transparent, contentColor = colors.textPrimary,
                    border = BorderStroke(layout.space(2.667f).coerceAtLeast(1.dp), colors.secondaryButton),
                ) {
                    Box(Modifier.padding(horizontal = layout.space(48f), vertical = 12.dp), Alignment.Center) {
                        Text(stringResource(R.string.close), style = layout.text(37.333f, 16f, FontWeight.SemiBold))
                    }
                }
            }
            Spacer(Modifier.height(layout.space(32f)))
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).selectableGroup()
                    .padding(bottom = layout.space(53.333f)),
                verticalArrangement = Arrangement.spacedBy(layout.space(24f)),
            ) {
                profiles.forEach { profile ->
                    DriverSheetRow(profile, profile.id == activeProfileId, sampleGuidanceCount,
                        enabled = !saving, layout = layout, onClick = { onSelect(profile.id) })
                }
                AddDriverRow(profiles.size, !saving && profiles.size < ProfileNamePolicy.MAX_PROFILES, layout, onAdd)
            }
        }
    }
}

@Composable
private fun DriverSheetRow(
    profile: DriverProfile,
    selected: Boolean,
    sampleGuidanceCount: Int?,
    enabled: Boolean,
    layout: DriveRuleLayout,
    onClick: () -> Unit,
) {
    val colors = LocalDriveRuleColors.current
    val shape = RoundedCornerShape(layout.space(32f))
    // Names are dynamic; keep an entire character (including surrogate pairs) in the avatar.
    val initial = remember(profile.name) {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(profile.name) }
        val end = iterator.next()
        if (end == BreakIterator.DONE) "" else profile.name.substring(0, end)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("profile_${profile.id}").clip(shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = shape, color = colors.picker,
        border = if (selected) BorderStroke(layout.space(4f).coerceAtLeast(2.dp), colors.accent) else null,
    ) {
        Row(
            Modifier.padding(horizontal = layout.space(40f), vertical = layout.space(if (selected) 32f else 28f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.space(32f)),
        ) {
            Box(
                Modifier.size(layout.space(96f).coerceAtLeast(44.dp))
                    .background(if (selected) colors.accent else colors.secondaryButton, CircleShape)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = if (selected) colors.onAccent else colors.textPrimary,
                    style = layout.text(44f, 20f, FontWeight.Bold))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(layout.space(6f))) {
                val nameStyle = layout.text(48f, 22f, FontWeight.Bold)
                Text(profile.name, color = colors.textPrimary,
                    style = nameStyle.copy(lineHeight = nameStyle.fontSize * 1.167f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val countStyle = layout.text(35f, 16f)
                Text(
                    if (sampleGuidanceCount == null) stringResource(R.string.driver_guidance_unavailable)
                    else stringResource(R.string.driver_guidance_sample, sampleGuidanceCount),
                    color = colors.textSecondary,
                    style = countStyle.copy(lineHeight = countStyle.fontSize * 1.229f),
                )
                if (selected && layout.compact) CurrentDriverLabel(layout)
            }
            if (selected && !layout.compact) CurrentDriverLabel(layout)
        }
    }
}

@Composable
private fun CurrentDriverLabel(layout: DriveRuleLayout) {
    Text(stringResource(R.string.active_driver), color = LocalDriveRuleColors.current.accentText,
        style = layout.text(38f, 16f, FontWeight.SemiBold))
}

@Composable
private fun AddDriverRow(count: Int, enabled: Boolean, layout: DriveRuleLayout, onClick: () -> Unit) {
    val colors = LocalDriveRuleColors.current
    val radius = layout.space(32f)
    val strokeWidth = layout.space(3f).coerceAtLeast(1.dp)
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("add_driver")
            .drawBehind {
                val stroke = strokeWidth.toPx()
                drawRoundRect(
                    color = colors.secondaryButton,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 5, stroke * 4))),
                )
            },
        shape = RoundedCornerShape(radius), color = Color.Transparent,
        contentColor = if (enabled) colors.textPrimary else colors.textTertiary,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = layout.space(36f)), Alignment.Center) {
            Text(stringResource(R.string.add_driver_count, count),
                style = layout.text(44f, 20f, FontWeight.SemiBold))
        }
    }
}
