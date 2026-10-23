package io.github.ocpabdurrahim.skilltrack.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

// A Material 3 scheme around an indigo seed, the same palette as the web app.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF3F0F4),
    inversePrimary = Color(0xFFBAC3FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF1ECF4),
    surfaceContainerHigh = Color(0xFFEBE6EE),
    surfaceContainerHighest = Color(0xFFE5E1E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF44263D),
    tertiaryContainer = Color(0xFF5D3C55),
    onTertiaryContainer = Color(0xFFFFD7F1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    inverseSurface = Color(0xFFE4E1E6),
    inverseOnSurface = Color(0xFF303034),
    inversePrimary = Color(0xFF4355B9),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF29292F),
    surfaceContainerHighest = Color(0xFF34343A),
)

/** Colours Material has no slot for: paid, due, attention, information, the hero card. */
@Immutable
data class SkillColors(
    val success: Color,
    val danger: Color,
    val warning: Color,
    val info: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val LightSkillColors = SkillColors(
    success = Color(0xFF1E8E4E),
    danger = Color(0xFFD23F31),
    warning = Color(0xFFE09A12),
    info = Color(0xFF1A73E8),
    heroStart = Color(0xFF4F5FD0),
    heroEnd = Color(0xFF2B3A9E),
)

private val DarkSkillColors = SkillColors(
    success = Color(0xFF6DD58C),
    danger = Color(0xFFFF8A80),
    warning = Color(0xFFFFCC66),
    info = Color(0xFF8AB4F8),
    heroStart = Color(0xFF2F3D9C),
    heroEnd = Color(0xFF1A245E),
)

val LocalSkillColors = staticCompositionLocalOf { LightSkillColors }

object SkillTheme {
    val colors: SkillColors
        @Composable @ReadOnlyComposable get() = LocalSkillColors.current
}

private val SkillTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun SkillTheme(dark: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme: ColorScheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val base = if (dark) DarkSkillColors else LightSkillColors
    val colors = when {
        useDynamic && dark -> base.copy(heroStart = scheme.primaryContainer, heroEnd = scheme.secondaryContainer)
        useDynamic -> base.copy(heroStart = scheme.primary, heroEnd = scheme.tertiary)
        else -> base
    }
    CompositionLocalProvider(LocalSkillColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = SkillTypography, content = content)
    }
}
