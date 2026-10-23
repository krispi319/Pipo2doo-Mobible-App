package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The approved B palette, scoped to the requested screens only. */
@Composable internal fun ApprovedDesign(content:@Composable ()->Unit) {
    val base=LocalAppThemeTokens.current
    val night=base.colors.background.luminance()<.3f
    val c=if(night)base.colors else base.colors.copy(
        primary=Color(0xFFF1A084),onPrimary=Color(0xFF432B20),
        secondary=Color(0xFFA8B9A0),accent=Color(0xFFDE8E9F),
        background=Color(0xFFFFF7EC),surface=Color(0xFFFFFCF6),
        surfaceElevated=Color(0xFFFFFCF6),surfaceSunken=Color(0xFFF7ECDD),
        textPrimary=Color(0xFF4A2E22),textSecondary=Color(0xFF735B4C),separator=Color(0xFFA17B64))
    val scheme=MaterialTheme.colorScheme.copy(primary=c.primary,onPrimary=c.onPrimary,
        secondary=c.secondary,onSecondary=c.onPrimary,tertiary=c.accent,onTertiary=c.onPrimary,
        surface=c.surface,onSurface=c.textPrimary,onSurfaceVariant=c.textSecondary,
        outline=c.separator,outlineVariant=c.separator,background=c.background,onBackground=c.textPrimary)
    MaterialTheme(colorScheme=scheme) {CompositionLocalProvider(LocalAppThemeTokens provides base.copy(colors=c),content=content)}
}
