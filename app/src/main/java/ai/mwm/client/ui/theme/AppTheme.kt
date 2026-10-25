package ai.mwm.client.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppSpacing(
    val s1: Float,
    val s2: Float,
    val s3: Float,
    val s4: Float,
    val s5: Float,
    val s6: Float,
    val s8: Float,
    val s10: Float,
    val s12: Float,
    val s16: Float,
)

@Immutable
data class AppRadius(
    val xs: Float,
    val sm: Float,
    val md: Float,
    val lg: Float,
    val xl: Float,
    val pill: Float,
)

@Immutable
data class AppMotion(
    val fastMillis: Int,
    val baseMillis: Int,
    val slowMillis: Int,
)

@Immutable
data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val surfaceSunken: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

@Immutable
data class AppThemeTokens(
    val spacing: AppSpacing,
    val radius: AppRadius,
    val motion: AppMotion,
    val colors: AppColors,
)

internal fun appColorsFrom(
    colorScheme: ColorScheme,
    success: Color,
    warning: Color,
    textTertiary: Color,
): AppColors = AppColors(
    primary = colorScheme.primary,
    onPrimary = colorScheme.onPrimary,
    secondary = colorScheme.secondary,
    accent = colorScheme.tertiary,
    background = colorScheme.background,
    surface = colorScheme.surface,
    surfaceSecondary = colorScheme.surfaceVariant,
    surfaceElevated = colorScheme.surfaceVariant,
    surfaceSunken = colorScheme.surfaceContainerLow,
    textPrimary = colorScheme.onBackground,
    textSecondary = colorScheme.onSurfaceVariant,
    textTertiary = textTertiary,
    separator = colorScheme.outline,
    success = success,
    warning = warning,
    error = colorScheme.error,
)

internal val LightAppColors = appColorsFrom(
    colorScheme = LightAppColorScheme,
    success = Color(0xFF4A8058),
    warning = Color(0xFFA36C0F),
    textTertiary = Color(0xFF846B5B),
)

internal val DarkAppColors = appColorsFrom(
    colorScheme = DarkAppColorScheme,
    success = Color(0xFF8CC69A),
    warning = Color(0xFFE3B054),
    textTertiary = Color(0xFFA8927F),
)

internal val LightAppThemeTokens = AppThemeTokens(
    spacing = AppSpacing(
        s1 = 4f,
        s2 = 8f,
        s3 = 12f,
        s4 = 16f,
        s5 = 20f,
        s6 = 24f,
        s8 = 32f,
        s10 = 40f,
        s12 = 48f,
        s16 = 64f,
    ),
    radius = AppRadius(
        xs = 3f,
        sm = 8f,
        md = 14f,
        lg = 20f,
        xl = 28f,
        pill = 9999f,
    ),
    motion = AppMotion(
        fastMillis = 150,
        baseMillis = 250,
        slowMillis = 400,
    ),
    colors = LightAppColors,
)

internal val DarkAppThemeTokens = LightAppThemeTokens.copy(colors = DarkAppColors)

internal val LocalAppThemeTokens = staticCompositionLocalOf { LightAppThemeTokens }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppThemeTokens.current.colors

    val spacing: AppSpacing
        @Composable get() = LocalAppThemeTokens.current.spacing

    val radius: AppRadius
        @Composable get() = LocalAppThemeTokens.current.radius

    val motion: AppMotion
        @Composable get() = LocalAppThemeTokens.current.motion
}
