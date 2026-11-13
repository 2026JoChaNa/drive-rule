package com.jochana.driverule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jochana.driverule.R

/** Resolved fills from the Figma day/night frames, not the generated CSS fallbacks. */
@Immutable
data class DriveRuleColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val picker: Color,
    val border: Color,
    val controlBorder: Color,
    val secondaryButton: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val scheduled: Color,
    val scheduledSoft: Color,
    val filledButton: Color,
    val filledButtonText: Color,
    val error: Color,
)

private val DarkTokens = DriveRuleColors(
    isDark = true,
    background = Color(0xFF1D1E21),
    surface = Color(0xFF313236),
    picker = Color(0xFF1D1E21),
    border = Color(0xFF3A3B40),
    controlBorder = Color(0xFF44464E),
    secondaryButton = Color(0xFF44464E),
    textPrimary = Color.White,
    textSecondary = Color(0xFFC7C9CE),
    textTertiary = Color(0xFFA4A8AE),
    accent = Color(0xFF0082FF),
    onAccent = Color(0xFF131417),
    accentText = Color(0xFF3195FF),
    accentSoft = Color(0xFF0F2740),
    green = Color(0xFF00E074),
    greenSoft = Color(0xFF0E3322),
    scheduled = Color(0xFFFF7EB6),
    scheduledSoft = Color(0xFF3D1A2C),
    filledButton = Color(0xFFE0E1E6),
    filledButtonText = Color(0xFF131417),
    error = Color(0xFFFFB4AB),
)

private val LightTokens = DriveRuleColors(
    isDark = false,
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    picker = Color(0xFFE0E1E6),
    border = Color(0xFFEDEEF2),
    controlBorder = Color(0xFFE0E1E6),
    secondaryButton = Color(0xFFE0E1E6),
    textPrimary = Color(0xFF131417),
    textSecondary = Color(0xFF44464E),
    textTertiary = Color(0xFF686A72),
    accent = Color(0xFF0064FF),
    onAccent = Color.White,
    accentText = Color(0xFF0058DE),
    accentSoft = Color(0xFFE0EDFF),
    green = Color(0xFF007A3D),
    greenSoft = Color(0xFFDFF7EA),
    scheduled = Color(0xFFC2185B),
    scheduledSoft = Color(0xFFFFE4EF),
    filledButton = Color(0xFF44464E),
    filledButtonText = Color.White,
    error = Color(0xFFB3261E),
)

val LocalDriveRuleColors = staticCompositionLocalOf { LightTokens }

val AstaSans = FontFamily(
    Font(R.font.asta_sans_regular, FontWeight.Normal),
    Font(R.font.asta_sans_medium, FontWeight.Medium),
    Font(R.font.asta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.asta_sans_bold, FontWeight.Bold),
)

private fun textStyle(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = AstaSans, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

private val DriveRuleTypography = Typography(
    displayLarge = textStyle(48, 60, FontWeight.Bold),
    displayMedium = textStyle(40, 50, FontWeight.Bold),
    displaySmall = textStyle(36, 46, FontWeight.Bold),
    headlineLarge = textStyle(32, 40, FontWeight.Bold),
    headlineMedium = textStyle(28, 36, FontWeight.SemiBold),
    headlineSmall = textStyle(26, 34, FontWeight.SemiBold),
    titleLarge = textStyle(24, 32, FontWeight.SemiBold),
    titleMedium = textStyle(20, 28, FontWeight.SemiBold),
    titleSmall = textStyle(18, 26, FontWeight.SemiBold),
    bodyLarge = textStyle(20, 28),
    bodyMedium = textStyle(18, 26),
    bodySmall = textStyle(16, 24),
    labelLarge = textStyle(18, 26, FontWeight.SemiBold),
    labelMedium = textStyle(16, 24, FontWeight.SemiBold),
    labelSmall = textStyle(14, 20, FontWeight.SemiBold),
)

@Composable
fun DriveRuleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = if (darkTheme) Color(0xFF60A5FF) else tokens.accentText, onPrimary = if (darkTheme) Color(0xFF131417) else Color.White,
            background = tokens.background, onBackground = tokens.textPrimary,
            surface = tokens.background, onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surface, onSurfaceVariant = tokens.textSecondary,
            surfaceContainer = tokens.surface, surfaceContainerHighest = tokens.secondaryButton,
            outline = tokens.textTertiary, outlineVariant = tokens.border, error = tokens.error,
        )
    } else {
        lightColorScheme(
            primary = if (darkTheme) Color(0xFF60A5FF) else tokens.accentText, onPrimary = if (darkTheme) Color(0xFF131417) else Color.White,
            background = tokens.background, onBackground = tokens.textPrimary,
            surface = tokens.background, onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surface, onSurfaceVariant = tokens.textSecondary,
            surfaceContainer = tokens.surface, surfaceContainerHighest = tokens.secondaryButton,
            outline = tokens.textTertiary, outlineVariant = tokens.border, error = tokens.error,
        )
    }
    CompositionLocalProvider(LocalDriveRuleColors provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = DriveRuleTypography, content = content)
    }
}
