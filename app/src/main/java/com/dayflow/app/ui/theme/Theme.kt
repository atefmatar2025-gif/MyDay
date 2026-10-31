package com.dayflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayflow.app.R
import com.dayflow.core.model.LifeArea
import com.dayflow.core.model.Priority

// ---------------------------------------------------------------- brand palette
object Brand {
    val DeepNavy = Color(0xFF111D35)
    val RoyalBlue = Color(0xFF294C80)
    val WarmOrange = Color(0xFFF39B42)
    val CreamWhite = Color(0xFFF7F5F0)
    val SoftGray = Color(0xFFE8ECF2)
    val SuccessGreen = Color(0xFF38A878)
    val Danger = Color(0xFFC8423B)
}

private val LightColors = lightColorScheme(
    primary = Brand.RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3F6),
    onPrimaryContainer = Brand.DeepNavy,
    secondary = Brand.WarmOrange,
    onSecondary = Brand.DeepNavy,
    secondaryContainer = Color(0xFFFDE6CC),
    onSecondaryContainer = Color(0xFF4A2A06),
    tertiary = Color(0xFF2B8A62), // darker than brand green for AA contrast on light surfaces
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F0E2),
    onTertiaryContainer = Color(0xFF0B3A26),
    background = Brand.CreamWhite,
    onBackground = Brand.DeepNavy,
    surface = Color.White,
    onSurface = Brand.DeepNavy,
    surfaceVariant = Brand.SoftGray,
    onSurfaceVariant = Color(0xFF46536B),
    surfaceContainer = Color(0xFFF0EEE8),
    surfaceContainerHigh = Color(0xFFEAE8E1),
    outline = Color(0xFF8F9AAE),
    outlineVariant = Color(0xFFD5DBE5),
    error = Brand.Danger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C3EE),
    onPrimary = Color(0xFF0B1730),
    primaryContainer = Brand.RoyalBlue,
    onPrimaryContainer = Color(0xFFE3ECFB),
    secondary = Brand.WarmOrange,
    onSecondary = Brand.DeepNavy,
    secondaryContainer = Color(0xFF5A3A12),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF6BD3A6),
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF1C5A40),
    onTertiaryContainer = Color(0xFFC9F2DD),
    background = Brand.DeepNavy,
    onBackground = Brand.CreamWhite,
    surface = Color(0xFF172544),
    onSurface = Brand.CreamWhite,
    surfaceVariant = Color(0xFF233457),
    onSurfaceVariant = Color(0xFFC3CCDC),
    surfaceContainer = Color(0xFF1B2A4B),
    surfaceContainerHigh = Color(0xFF223356),
    outline = Color(0xFF7D8AA3),
    outlineVariant = Color(0xFF34466B),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A06),
)

// ---------------------------------------------------------------- typography
val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_arabic_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int) =
    TextStyle(fontFamily = PlexArabic, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val DayflowTypography = Typography(
    displaySmall = style(34, FontWeight.Bold, 44),
    headlineLarge = style(30, FontWeight.Bold, 40),
    headlineMedium = style(26, FontWeight.Bold, 36),
    headlineSmall = style(22, FontWeight.SemiBold, 32),
    titleLarge = style(20, FontWeight.SemiBold, 30),
    titleMedium = style(17, FontWeight.SemiBold, 26),
    titleSmall = style(15, FontWeight.Medium, 22),
    bodyLarge = style(16, FontWeight.Normal, 26),
    bodyMedium = style(14, FontWeight.Normal, 22),
    bodySmall = style(12, FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(12, FontWeight.Medium, 18),
    labelSmall = style(11, FontWeight.Medium, 16),
)

private val DayflowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Spacing scale of the design system (4-pt grid). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 16.dp
}

@Immutable
data class ExtraColors(val success: Color, val warning: Color, val danger: Color, val isDark: Boolean)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Brand.SuccessGreen, Brand.WarmOrange, Brand.Danger, false) }

object AppColors {
    fun area(area: LifeArea): Color = when (area) {
        LifeArea.WORK -> Color(0xFF294C80)
        LifeArea.STUDY -> Color(0xFF5B6CC9)
        LifeArea.WORSHIP -> Color(0xFF2E8C83)
        LifeArea.FAMILY -> Color(0xFFD9734E)
        LifeArea.HEALTH -> Color(0xFF38A878)
        LifeArea.REST -> Color(0xFF8A94A6)
        LifeArea.PERSONAL_GROWTH -> Color(0xFF9C6ADE)
        LifeArea.SOCIAL -> Color(0xFFC2589A)
        LifeArea.APPOINTMENT -> Color(0xFFF39B42)
        LifeArea.PERSONAL -> Color(0xFF6B7A90)
    }

    fun priority(p: Priority): Color = when (p) {
        Priority.URGENT -> Color(0xFFD64541)
        Priority.HIGH -> Color(0xFFF39B42)
        Priority.MEDIUM -> Color(0xFF294C80)
        Priority.LOW -> Color(0xFF8A94A6)
    }

    fun parse(hex: String?, fallback: Color = Brand.RoyalBlue): Color = try {
        if (hex == null) fallback else Color(android.graphics.Color.parseColor(hex))
    } catch (e: IllegalArgumentException) {
        fallback
    }
}

/**
 * DAYFLOW theme. The UI is always right-to-left (Arabic-first) regardless of the device language,
 * and the user's font-size preference multiplies the system font scale (accessibility).
 */
@Composable
fun DayflowTheme(themeMode: String = "SYSTEM", fontScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(density.density, density.fontScale * fontScale.coerceIn(0.85f, 1.6f)),
        LocalExtraColors provides ExtraColors(
            success = if (dark) Color(0xFF6BD3A6) else Color(0xFF2B8A62),
            warning = Brand.WarmOrange,
            danger = if (dark) Color(0xFFFF8A80) else Brand.Danger,
            isDark = dark,
        ),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = DayflowTypography,
            shapes = DayflowShapes,
            content = content,
        )
    }
}
