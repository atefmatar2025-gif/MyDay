package com.myday.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.myday.app.data.Priority
import com.myday.app.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0E6),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2BF),
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E0FF),
    onTertiaryContainer = Color(0xFF13105E),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBF9),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBF9),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5F3),
    surfaceContainer = Color(0xFFE8F0EE),
    surfaceContainerHigh = Color(0xFFE3EAE8),
    surfaceContainerHighest = Color(0xFFDDE4E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD8CB),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB7F0E6),
    secondary = Color(0xFFFFB872),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A3C00),
    onSecondaryContainer = Color(0xFFFFE2BF),
    tertiary = Color(0xFFC0C1FF),
    onTertiary = Color(0xFF1E1A8A),
    tertiaryContainer = Color(0xFF3730A3),
    onTertiaryContainer = Color(0xFFE0E0FF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
)

private val AppTypography = Typography().let { t ->
    Typography(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = t.bodyMedium.copy(lineHeight = 22.sp),
        bodySmall = t.bodySmall,
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium,
        labelSmall = t.labelSmall,
        displayLarge = t.displayLarge,
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Light),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object PriorityColors {
    fun of(p: Priority, dark: Boolean): Color = when (p) {
        Priority.HIGH -> if (dark) Color(0xFFFF8A80) else Color(0xFFDC2626)
        Priority.MEDIUM -> if (dark) Color(0xFFFFCC80) else Color(0xFFD97706)
        Priority.LOW -> if (dark) Color(0xFF9FD4A6) else Color(0xFF16A34A)
    }
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MyDayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
        // واجهة عربية بالكامل من اليمين لليسار بغض النظر عن لغة الجهاز
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
