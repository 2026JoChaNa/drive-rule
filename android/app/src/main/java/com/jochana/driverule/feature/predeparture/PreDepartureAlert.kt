package com.jochana.driverule.feature.predeparture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jochana.driverule.R
import com.jochana.driverule.feature.changes.RuleChangePreview
import com.jochana.driverule.ui.components.DriveRuleLayout
import com.jochana.driverule.ui.components.FigmaIcon
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.theme.LocalDriveRuleColors
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Figma 02 description (1844:32993): read once before departure, closes after 8 s, no buttons.
 * 8 s is the design value (voice within 5 s + 3 s), to be tuned in prototype tests. TTS is not
 * connected yet, so the time runs from when the alert appears.
 */
const val PRE_DEPARTURE_ALERT_MILLIS = 8_000

/**
 * Figma 02 `DRIVE RULE 알림` (1848:27413), drawn inside the app window only (user decision
 * 2026-11-14). It never draws over other apps. Auto-close is not recorded as "heard".
 */
@Composable
fun PreDepartureAlert(
    change: RuleChangePreview,
    driverName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Int = PRE_DEPARTURE_ALERT_MILLIS,
    /** Applied to the card itself, e.g. enter/exit motion anchored at the card, not the window. */
    cardModifier: Modifier = Modifier,
) {
    val message = change.alertMessage ?: return
    val progress = remember(change.id) { Animatable(0f) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(change.id) {
        progress.animateTo(1f, tween(durationMillis = durationMillis, easing = LinearEasing))
        latestDismiss()
    }
    BackHandler(onBack = onDismiss)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = DriveRuleLayout(scale = (maxWidth.value / 1707f).coerceIn(0.46f, 1.16f))
        CompositionLocalProvider(LocalDriveRuleLayout provides layout) {
            val colors = LocalDriveRuleColors.current
            val shape = RoundedCornerShape(layout.space(42.667f))
            val date = change.effectiveOn
            // Figma places the card 32 px from the window's left and 258 px above its bottom.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = layout.space(32f), end = layout.space(32f), bottom = layout.space(258f))
                    .widthIn(max = layout.space(720f))
                    .fillMaxWidth()
                    .then(cardModifier)
                    .shadow(layout.space(26.667f), shape)
                    .background(colors.border, shape)
                    .border(BorderStroke(layout.space(2.667f).coerceAtLeast(1.dp), colors.controlBorder), shape)
                    .padding(layout.space(42.667f))
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("pre_departure_alert"),
                verticalArrangement = Arrangement.spacedBy(layout.space(26.667f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.space(21.333f)),
                ) {
                    Box(
                        Modifier.size(layout.space(74.667f).coerceAtLeast(36.dp))
                            .background(colors.accent, RoundedCornerShape(layout.space(21.333f))),
                        contentAlignment = Alignment.Center,
                    ) {
                        FigmaIcon("alert_brand", layout.space(42.667f).coerceAtLeast(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(layout.space(8f))) {
                        Text(
                            stringResource(R.string.alert_context, driverName),
                            style = layout.text(34.667f, 16f),
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                        Text(
                            stringResource(R.string.alert_effective_badge, date.monthValue, date.dayOfMonth),
                            modifier = Modifier
                                .background(colors.scheduledSoft, RoundedCornerShape(layout.space(16f)))
                                .padding(horizontal = layout.space(21.333f), vertical = layout.space(10.667f)),
                            style = layout.text(34.667f, 16f, FontWeight.Bold)
                                .copy(letterSpacing = layout.space(1.3867f).value.sp),
                            color = colors.scheduled,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    message,
                    modifier = Modifier.testTag("pre_departure_alert_message"),
                    style = layout.text(64f, 26f, FontWeight.Bold),
                    color = colors.textPrimary,
                )
                // Read only while drawing, so the 8 s bar does not recompose the alert every frame.
                Box(
                    Modifier.fillMaxWidth()
                        .height(layout.space(6f).coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(layout.space(3f)))
                        .background(colors.secondaryButton)
                        .drawBehind {
                            drawRoundRect(
                                colors.accent,
                                size = Size(size.width * progress.value.coerceIn(0f, 1f), size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                        },
                )
            }
        }
    }
}
