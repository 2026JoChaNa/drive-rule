package com.jochana.driverule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.jochana.driverule.R
import com.jochana.driverule.ui.theme.AstaSans
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/** Figma coordinates describe a 1687 px app window; Android controls remain adaptive. */
@Immutable
data class DriveRuleLayout(val scale: Float = 0.5f, val compact: Boolean = false) {
    fun space(designPixels: Float): Dp = (designPixels * scale).dp
    fun text(designPixels: Float, minSp: Float = 16f, weight: FontWeight = FontWeight.Medium) =
        TextStyle(
            fontFamily = AstaSans,
            fontWeight = weight,
            fontSize = (designPixels * scale).coerceAtLeast(minSp).sp,
            lineHeight = (designPixels * scale * 1.3f).coerceAtLeast(minSp * 1.3f).sp,
        )
}

val LocalDriveRuleLayout = staticCompositionLocalOf { DriveRuleLayout() }

@Composable
fun DriveRuleWindow(
    modifier: Modifier = Modifier,
    horizontalPadding: Float = 64f,
    topPadding: Float = 32f,
    bottomPadding: Float = 32f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalDriveRuleColors.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = DriveRuleLayout(
            scale = (maxWidth.value / 1687f).coerceIn(0.46f, 1.16f),
            compact = maxWidth.value / LocalDensity.current.fontScale < 620f,
        )
        CompositionLocalProvider(LocalDriveRuleLayout provides layout) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(layout.space(32f)),
                color = colors.background,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(
                        // A zero padding means the caller lays out edge-to-edge content (sheets).
                        start = if (layout.compact && horizontalPadding > 0f) 20.dp else layout.space(horizontalPadding),
                        end = if (layout.compact && horizontalPadding > 0f) 20.dp else layout.space(horizontalPadding),
                        top = layout.space(topPadding),
                        bottom = layout.space(bottomPadding),
                    ),
                    content = content,
                )
            }
        }
    }
}

/** The blue app tile and name used at the top of every DRIVE RULE window. */
@Composable
fun DriveRuleBrand(modifier: Modifier = Modifier) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Row(
        modifier.testTag("drive_rule_brand"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(layout.space(20f)),
    ) {
        Box(
            Modifier.size(layout.space(72f)).background(colors.accent, RoundedCornerShape(layout.space(20.571f))),
            contentAlignment = Alignment.Center,
        ) {
            FigmaIcon("brand", layout.space(41.143f))
        }
        Text(
            stringResource(R.string.ui_brand),
            style = layout.text(36f, 18f, FontWeight.Bold),
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * Larger original Figma exports (scene illustrations). The SVG keeps its own width, height and
 * viewBox; the caller chooses the slot and how the export is fitted into it.
 */
@Composable
fun FigmaImage(
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val mode = if (LocalDriveRuleColors.current.isDark) "dark" else "light"
    val request = remember(context, name, mode) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/figma/${name}_$mode.svg")
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
    )
}

/** These SVGs are original Figma exports. Their viewBox and root dimensions are unmodified. */
@Composable
fun FigmaIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mode = if (LocalDriveRuleColors.current.isDark) "dark" else "light"
    val request = remember(context, name, mode) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/figma/${name}_$mode.svg")
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun DriveRuleHeader(
    themeLabel: String,
    onThemeClick: () -> Unit,
    action: @Composable () -> Unit,
) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.space(16f))) {
        val brand: @Composable () -> Unit = { DriveRuleBrand() }
        if (layout.compact) {
            brand()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.space(20f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeAction(themeLabel, onThemeClick)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { action() }
            }
        } else {
            // Reserve the measured brand and theme widths before measuring a dynamic name.
            // A normal short action retains its Figma alignment at the right edge.
            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    Box { brand() }
                    Box { ThemeAction(themeLabel, onThemeClick) }
                    Box { action() }
                },
            ) { measurables, constraints ->
                val gap = layout.space(20f).roundToPx()
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val brandPlaceable = measurables[0].measure(loose)
                val themePlaceable = measurables[1].measure(loose.copy(
                    maxWidth = (constraints.maxWidth - brandPlaceable.width - gap * 2).coerceAtLeast(0),
                ))
                val actionPlaceable = measurables[2].measure(loose.copy(
                    maxWidth = (constraints.maxWidth - brandPlaceable.width - themePlaceable.width - gap * 2)
                        .coerceAtLeast(0),
                ))
                val height = maxOf(
                    brandPlaceable.height, themePlaceable.height, actionPlaceable.height,
                ).coerceIn(constraints.minHeight, constraints.maxHeight)
                this.layout(constraints.maxWidth, height) {
                    brandPlaceable.placeRelative(0, (height - brandPlaceable.height) / 2)
                    themePlaceable.placeRelative(
                        constraints.maxWidth - actionPlaceable.width - gap - themePlaceable.width,
                        (height - themePlaceable.height) / 2,
                    )
                    actionPlaceable.placeRelative(
                        constraints.maxWidth - actionPlaceable.width,
                        (height - actionPlaceable.height) / 2,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.border, modifier = Modifier.padding(top = layout.space(4f)))
    }
}

@Composable
private fun ThemeAction(label: String, onClick: () -> Unit) {
    val colors = LocalDriveRuleColors.current
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 56.dp).testTag("theme_picker")) {
        Text(
            stringResource(R.string.ui_theme_action, label),
            color = colors.textSecondary,
            style = LocalDriveRuleLayout.current.text(28f, 16f, FontWeight.SemiBold),
        )
    }
}

@Composable
fun DemoLabel(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.ui_demo_label),
        modifier = modifier,
        color = LocalDriveRuleColors.current.textTertiary,
        style = LocalDriveRuleLayout.current.text(26f, 14f),
    )
}

@Composable
fun DriveRuleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    outlined: Boolean = false,
    primary: Boolean = false,
    /** Figma header-button metrics (218 min width, 21.8 radius, 30.5 text) on a filled surface. */
    dense: Boolean = false,
    /** Overrides the Figma text size for buttons whose label differs from the shared metrics. */
    textSize: Float? = null,
    minHeight: Float = 117.333f,
) {
    val layout = LocalDriveRuleLayout.current
    val colors = LocalDriveRuleColors.current
    val small = outlined || dense
    val background = when {
        outlined -> colors.background
        primary -> colors.filledButton
        else -> colors.secondaryButton
    }
    val foreground = if (primary) colors.filledButtonText else colors.textPrimary
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (small) Modifier.widthIn(min = layout.space(218.182f)) else Modifier)
            .heightIn(min = layout.space(minHeight).coerceAtLeast(56.dp)),
        shape = RoundedCornerShape(layout.space(if (small) 21.818f else 26.667f)),
        color = if (enabled) background else colors.secondaryButton,
        contentColor = if (enabled) foreground else colors.textSecondary,
        border = if (outlined) BorderStroke(1.dp, colors.controlBorder) else null,
    ) {
        Row(
            Modifier.padding(horizontal = layout.space(if (small) 39.273f else 48f), vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.space(if (small) 15.273f else 18.667f), Alignment.CenterHorizontally),
        ) {
            if (icon != null) {
                FigmaIcon(icon, layout.space(if (small) 34.909f else 42.667f).coerceAtLeast(20.dp))
            }
            Text(
                label,
                style = layout.text(textSize ?: if (small) 30.545f else 37.333f, 16f, FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
