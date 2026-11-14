package com.jochana.driverule.feature.card

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jochana.driverule.R
import com.jochana.driverule.feature.home.HomePreviewStatus
import com.jochana.driverule.ui.components.DriveRuleBrand
import com.jochana.driverule.ui.components.DriveRuleButton
import com.jochana.driverule.ui.components.DriveRuleMotion
import com.jochana.driverule.ui.components.DriveRuleWindow
import com.jochana.driverule.ui.components.FigmaIcon
import com.jochana.driverule.ui.components.FigmaImage
import com.jochana.driverule.ui.components.LocalDriveRuleLayout
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/**
 * Figma A-1 description: the card closes 8 s after the voice and opening the evidence stops it.
 * This is the design value, not a confirmed product timing policy (source-check §5 item 3).
 */
const val CARD_AUTO_CLOSE_MILLIS = 8_000

/** Width of the Figma one-slot app window (`DRIVE RULE (세로형 창)`). */
private const val ONE_SLOT_WINDOW = 837.333f

@Composable
fun CardDetailScreen(
    card: CardDetailPreview,
    reportState: CardReportState,
    onReport: () -> Unit,
    onOpenSource: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    sample: Boolean = false,
    autoCloseMillis: Int = CARD_AUTO_CLOSE_MILLIS,
) {
    var evidenceOpen by rememberSaveable(card.id) { mutableStateOf(false) }
    var autoCloseStopped by rememberSaveable(card.id) { mutableStateOf(false) }
    val progress = remember(card.id) { Animatable(0f) }
    val latestOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(card.id, autoCloseStopped) {
        if (autoCloseStopped) return@LaunchedEffect
        val remaining = ((1f - progress.value) * autoCloseMillis).toInt().coerceAtLeast(0)
        progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
        latestOnClose()
    }
    BackHandler {
        if (evidenceOpen) evidenceOpen = false else onClose()
    }

    DriveRuleWindow(
        modifier = modifier.testTag("card_detail_${card.id}"),
        horizontalPadding = 0f,
        topPadding = 0f,
        bottomPadding = 0f,
    ) {
        val layout = LocalDriveRuleLayout.current
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = layout.space(ONE_SLOT_WINDOW))
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(
                        start = layout.space(37.333f),
                        end = layout.space(37.333f),
                        top = layout.space(26.667f),
                        bottom = layout.space(37.333f),
                    ),
                verticalArrangement = Arrangement.spacedBy(layout.space(26.667f)),
            ) {
                CardHeader(onEvidence = {
                    autoCloseStopped = true
                    evidenceOpen = true
                })
                Row(horizontalArrangement = Arrangement.spacedBy(layout.space(16f))) {
                    StageBadge(card.status)
                    EventTag(card.tag)
                }
                Text(
                    card.message,
                    modifier = Modifier.testTag("card_message"),
                    style = layout.text(50.667f, 24f, FontWeight.SemiBold),
                    color = LocalDriveRuleColors.current.textPrimary,
                )
                CardScene(card)
                if (sample) {
                    Text(
                        stringResource(R.string.card_sample_label),
                        modifier = Modifier.testTag("card_sample_label"),
                        style = layout.text(26f, 14f),
                        color = LocalDriveRuleColors.current.textTertiary,
                    )
                }
                AutoCloseBar(progress.value, stopped = autoCloseStopped)
            }
            // Children animate individually: the dim fades, the sheet slides up from the bottom.
            // Fully qualified: the window lambda is a ColumnScope, whose overload would not fill the box.
            androidx.compose.animation.AnimatedVisibility(
                visible = evidenceOpen,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                modifier = Modifier.fillMaxSize(),
            ) {
                EvidenceSheet(
                    evidence = card.evidence,
                    reportState = reportState,
                    onReport = onReport,
                    onOpenSource = onOpenSource,
                    onDismiss = { evidenceOpen = false },
                )
            }
        }
    }
}

@Composable
private fun CardHeader(onEvidence: () -> Unit) {
    val layout = LocalDriveRuleLayout.current
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DriveRuleBrand()
            DriveRuleButton(
                label = stringResource(R.string.card_evidence_action),
                onClick = onEvidence,
                icon = "evidence",
                dense = true,
                modifier = Modifier.testTag("card_evidence_button"),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = layout.space(20f)),
            thickness = layout.space(2f).coerceAtLeast(1.dp),
            color = LocalDriveRuleColors.current.border,
        )
    }
}

@Composable
internal fun StageBadge(status: HomePreviewStatus, modifier: Modifier = Modifier) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val active = status == HomePreviewStatus.Active
    Text(
        stringResource(if (active) R.string.ui_status_active else R.string.ui_status_scheduled),
        modifier = modifier
            .testTag("card_stage_${status.name.lowercase()}")
            .background(if (active) colors.greenSoft else colors.scheduledSoft, RoundedCornerShape(layout.space(16f)))
            .padding(horizontal = layout.space(21.333f), vertical = layout.space(10.667f)),
        style = layout.text(34.667f, 16f, FontWeight.Bold).copy(letterSpacing = (layout.space(1.3867f).value).sp),
        color = if (active) colors.green else colors.scheduled,
        maxLines = 1,
    )
}

@Composable
private fun EventTag(tag: CardEventTag) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Row(
        modifier = Modifier
            .testTag("card_tag_${tag.name}")
            .background(colors.secondaryButton, RoundedCornerShape(layout.space(16f)))
            .padding(
                start = layout.space(18.667f),
                end = layout.space(21.333f),
                top = layout.space(10.667f),
                bottom = layout.space(10.667f),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.space(10.667f)),
    ) {
        FigmaIcon(tag.icon, layout.space(42.667f).coerceAtLeast(20.dp))
        Text(
            stringResource(
                when (tag) {
                    CardEventTag.SchoolZoneEntry -> R.string.card_tag_school_zone_entry
                    CardEventTag.Parking -> R.string.card_tag_parking
                    CardEventTag.RightTurnCrosswalk -> R.string.card_tag_right_turn_crosswalk
                },
            ),
            style = layout.text(34.667f, 16f, FontWeight.Bold).copy(letterSpacing = (layout.space(1.3867f).value).sp),
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ColumnScope.CardScene(card: CardDetailPreview) {
    val layout = LocalDriveRuleLayout.current
    val scene = card.scene
    val shape = RoundedCornerShape(layout.space(20f))
    if (scene.fillsWindow) {
        // Figma `Scene (crop)` grows with the window and clips the export from the top.
        val animate = scene.hasMotion() && !animationsDisabled()
        Box(Modifier.weight(1f).fillMaxWidth().clip(shape).testTag("card_scene_${scene.name}")) {
            if (animate) {
                // Figma H-1 / I-1 motion (6.6 s loop). Without motion the static export is the same frame at rest.
                AnimatedCardScene(scene, Modifier.matchParentSize())
            } else {
                FigmaImage(
                    scene.asset,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }
        }
    } else {
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(scene.designWidth / scene.designHeight)
                .clip(shape)
                .testTag("card_scene_${scene.name}"),
        ) {
            FigmaImage(scene.asset, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        }
        card.legend?.let { SceneLegend(it) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SceneLegend(label: String) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Row(
        Modifier.padding(vertical = layout.space(10f)).testTag("card_scene_legend"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.space(10f)),
    ) {
        // The legend dot is a raster fill in Figma (`1849:27950`), so it is packaged as PNG.
        coil.compose.AsyncImage(
            model = "file:///android_asset/figma/legend_zone.png",
            contentDescription = null,
            modifier = Modifier.size(layout.space(24f).coerceAtLeast(12.dp)).clip(CircleShape),
        )
        Text(label, style = layout.text(35f, 16f, FontWeight.SemiBold), color = colors.textSecondary)
    }
}

@Composable
private fun AutoCloseBar(progress: Float, stopped: Boolean) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val description = stringResource(R.string.card_auto_close_description)
    Box(
        Modifier.fillMaxWidth()
            .height(layout.space(6f).coerceAtLeast(3.dp))
            .clip(RoundedCornerShape(layout.space(3f)))
            .background(colors.secondaryButton)
            .testTag(if (stopped) "card_auto_close_stopped" else "card_auto_close_running")
            .semantics {
                if (!stopped) contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(colors.accent))
    }
}

@Composable
private fun AnimatedVisibilityScope.EvidenceSheet(
    evidence: CardEvidencePreview,
    reportState: CardReportState,
    onReport: () -> Unit,
    onOpenSource: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.matchParentSize()
                .animateEnterExit(enter = fadeIn(DriveRuleMotion.enter()), exit = fadeOut(DriveRuleMotion.exit()))
                .background(Color.Black.copy(alpha = 0.5f))
                // Tapping the dim closes the sheet. A pointer handler (not clickable) keeps the sheet's
                // rows out of a merged accessibility node; '닫기' remains the accessible close action.
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .testTag("evidence_dim"),
        )
        Column(
            modifier = Modifier
                .animateEnterExit(
                    enter = slideInVertically(DriveRuleMotion.enter()) { it },
                    exit = slideOutVertically(DriveRuleMotion.exit()) { it },
                )
                .fillMaxHeight()
                .widthIn(max = layout.space(ONE_SLOT_WINDOW))
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .shadow(layout.space(26.667f), RoundedCornerShape(layout.space(32f)))
                .background(colors.surface, RoundedCornerShape(layout.space(32f)))
                // Taps inside the sheet must not reach the dim behind it. A pointer handler keeps the
                // sheet's rows as separate accessibility nodes (an empty clickable would merge them).
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(
                    start = layout.space(37f),
                    end = layout.space(37f),
                    top = layout.space(26.667f),
                    bottom = layout.space(37.333f),
                )
                .testTag("evidence_sheet"),
            verticalArrangement = Arrangement.spacedBy(layout.space(32f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.width(layout.space(96f))
                    .height(layout.space(8f).coerceAtLeast(4.dp))
                    .background(colors.textTertiary, RoundedCornerShape(layout.space(4f))),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.evidence_title),
                    style = layout.text(50.667f, 24f, FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                DriveRuleButton(
                    label = stringResource(R.string.card_close),
                    onClick = onDismiss,
                    dense = true,
                    textSize = 37.33f,
                    minHeight = 96f,
                    modifier = Modifier.testTag("evidence_close"),
                )
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                EvidenceRow(stringResource(R.string.evidence_law), evidence.law, first = true)
                EvidenceRow(stringResource(R.string.evidence_dates), evidence.dates)
                EvidenceRow(stringResource(R.string.evidence_penalty), evidence.penalty)
            }
            EvidenceNotice(evidence, reportState)
            val actions: @Composable (Modifier) -> Unit = { itemModifier ->
                val source = evidence.sourceUrl
                DriveRuleButton(
                    label = stringResource(R.string.evidence_law_source),
                    onClick = { if (source != null) onOpenSource(source) },
                    icon = "law_source",
                    enabled = source != null,
                    modifier = itemModifier.testTag("evidence_law_source"),
                )
                DriveRuleButton(
                    label = stringResource(reportLabel(reportState)),
                    onClick = onReport,
                    icon = "report",
                    enabled = reportState == CardReportState.Idle || reportState == CardReportState.Failed,
                    modifier = itemModifier.testTag("evidence_report"),
                )
            }
            if (layout.compact) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    actions(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(layout.space(21.333f))) {
                    actions(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String, first: Boolean = false) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Column(Modifier.fillMaxWidth()) {
        if (!first) {
            HorizontalDivider(thickness = layout.space(2.667f).coerceAtLeast(1.dp), color = colors.border)
        }
        Column(
            Modifier.fillMaxWidth().padding(vertical = layout.space(14f)),
            verticalArrangement = Arrangement.spacedBy(layout.space(6f)),
        ) {
            Text(label, style = layout.text(37.333f, 18f, FontWeight.SemiBold), color = colors.textSecondary)
            Text(value, style = layout.text(45.333f, 20f, FontWeight.SemiBold), color = colors.textPrimary)
        }
    }
}

@Composable
private fun EvidenceNotice(evidence: CardEvidencePreview, reportState: CardReportState) {
    val notice = when {
        reportState == CardReportState.Unavailable -> R.string.evidence_report_unavailable
        reportState == CardReportState.Failed -> R.string.evidence_report_failed
        reportState == CardReportState.Rejected -> R.string.evidence_report_rejected
        evidence.sourceUrl == null -> R.string.evidence_source_unavailable
        else -> null
    } ?: return
    val layout = LocalDriveRuleLayout.current
    Text(
        stringResource(notice),
        modifier = Modifier.fillMaxWidth().testTag("evidence_notice"),
        style = layout.text(30f, 16f),
        color = LocalDriveRuleColors.current.textTertiary,
    )
}

private fun reportLabel(state: CardReportState) = when (state) {
    CardReportState.Sending -> R.string.evidence_report_sending
    CardReportState.Sent -> R.string.evidence_report_sent
    CardReportState.Failed -> R.string.evidence_report_retry
    CardReportState.Unavailable, CardReportState.Idle, CardReportState.Rejected -> R.string.evidence_report
}
