package com.jochana.driverule.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/**
 * Proposed debug-only surface using the existing day/night tokens, not a Figma-approved popup.
 * Read-only normal app content. Lifetime and speech belong to the service, never composition.
 */
@Composable
internal fun SampleGuidanceBanner(title: String, message: String, modifier: Modifier = Modifier) {
    val colors = LocalDriveRuleColors.current
    Surface(
        modifier = modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("sample_guidance_banner")
            // Do not let a touch on this read-only card activate a covered home control.
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("sample_guidance_banner_title"))
            // This short fixture text contains no law; real law content requires a separately
            // reviewed concise copy and must not be silently cut to fit this sample surface.
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("sample_guidance_banner_body"))
        }
    }
}
