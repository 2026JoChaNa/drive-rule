package com.jochana.driverule.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/**
 * Read-only published text in normal app bounds, using existing day/night tokens.
 * This proposed surface has no Figma-approved popup specification. It owns no timer or speech.
 * If the complete title, message and provenance do not fit, omit the entire visual card;
 * never turn truncated legal text into an apparently complete instruction.
 */
@Composable
internal fun GuidanceBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
) {
    if (title.isBlank() || message.isBlank()) return
    val colors = LocalDriveRuleColors.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleMedium
    val messageStyle = MaterialTheme.typography.bodyMedium
    val contextStyle = MaterialTheme.typography.labelSmall
    val metadata = contextLabel?.takeIf { it.isNotBlank() }
    BoxWithConstraints(modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        val horizontalPadding = with(density) { 24.dp.roundToPx() }
        val verticalPadding = with(density) { 18.dp.roundToPx() }
        val gap = with(density) { 6.dp.roundToPx() }
        val textWidth = constraints.maxWidth - 2 * horizontalPadding
        if (textWidth <= 0) return@BoxWithConstraints
        val textConstraints = Constraints(maxWidth = textWidth)
        val titleLayout = measurer.measure(title, style = titleStyle, constraints = textConstraints)
        val messageLayout = measurer.measure(message, style = messageStyle, constraints = textConstraints)
        val contextLayout = metadata?.let {
            measurer.measure(it, style = contextStyle, constraints = textConstraints)
        }
        val requiredHeight = titleLayout.size.height + messageLayout.size.height +
            (contextLayout?.size?.height ?: 0) + gap * (if (contextLayout == null) 1 else 2) +
            2 * verticalPadding
        if (!titleLayout.fits(maxLines = 2) || !messageLayout.fits(maxLines = 3) ||
            contextLayout?.fits(maxLines = 2) == false || requiredHeight > constraints.maxHeight) {
            return@BoxWithConstraints
        }
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("guidance_banner")
                // A read-only card must not forward touches to the home controls it covers.
                .pointerInput(Unit) { awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                } },
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = BorderStroke(1.dp, colors.controlBorder),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), Arrangement.spacedBy(6.dp)) {
                metadata?.let {
                    Text(it, style = contextStyle, color = colors.textSecondary,
                        overflow = TextOverflow.Clip, modifier = Modifier.testTag("guidance_banner_context"))
                }
                Text(title, style = titleStyle, color = colors.textPrimary,
                    overflow = TextOverflow.Clip, modifier = Modifier.testTag("guidance_banner_title"))
                Text(message, style = messageStyle, color = colors.textSecondary,
                    overflow = TextOverflow.Clip, modifier = Modifier.testTag("guidance_banner_body"))
            }
        }
    }
}

private fun TextLayoutResult.fits(maxLines: Int): Boolean = lineCount <= maxLines &&
    !didOverflowHeight && (0 until lineCount).all { line ->
        // Compose 1.7.6 can report didOverflowWidth for a wider paragraph with narrow text.
        // Verify the actual laid-out lines rather than rejecting that false positive.
        !isLineEllipsized(line) && getLineLeft(line) >= -1f && getLineRight(line) <= size.width + 1f
    }
