package com.shinemusic.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.shinemusic.data.AppSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinemusic.R

/** Poppins, bundled in res/font – geometric, bold, great for music UIs. */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold)
)

// True-black AMOLED palette: pixels are fully off on the background, indigo + orchid accents.
private val Amoled = darkColorScheme(
    primary = Color(0xFF9FB0FF),
    onPrimary = Color(0xFF0B1450),
    primaryContainer = Color(0xFF26337A),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondary = Color(0xFFB8C0E8),
    onSecondary = Color(0xFF1B2040),
    secondaryContainer = Color(0xFF1B1F38),
    onSecondaryContainer = Color(0xFFDFE3FF),
    tertiary = Color(0xFFFF9AD1),
    onTertiary = Color(0xFF4A1B3A),
    tertiaryContainer = Color(0xFF4A1B3A),
    onTertiaryContainer = Color(0xFFFFD9EE),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F8),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF4F4F8),
    surfaceVariant = Color(0xFF1F1F27),
    onSurfaceVariant = Color(0xFFB4B4C2),
    outline = Color(0xFF6E6E7C),
    outlineVariant = Color(0xFF2A2A33),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0B0B0E),
    surfaceContainer = Color(0xFF111116),
    surfaceContainerHigh = Color(0xFF18181F),
    surfaceContainerHighest = Color(0xFF1F1F27)
)

private val base = Typography()

private val ShineTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Poppins),
    displayMedium = base.displayMedium.copy(fontFamily = Poppins),
    displaySmall = base.displaySmall.copy(fontFamily = Poppins),
    headlineLarge = base.headlineLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = Poppins, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = Poppins, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = Poppins, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium)
)

private val ShineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0B1450),
    secondary = Color(0xFF5A5F7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E5F5),
    onSecondaryContainer = Color(0xFF1B2040),
    tertiary = Color(0xFFB0306F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9EE),
    onTertiaryContainer = Color(0xFF3B0A28),
    background = Color(0xFFF8F8FC),
    onBackground = Color(0xFF16161C),
    surface = Color(0xFFF8F8FC),
    onSurface = Color(0xFF16161C),
    surfaceVariant = Color(0xFFE6E6EE),
    onSurfaceVariant = Color(0xFF4A4A57),
    outline = Color(0xFF7A7A88),
    outlineVariant = Color(0xFFD0D0DA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F8),
    surfaceContainer = Color(0xFFECECF3),
    surfaceContainerHigh = Color(0xFFE6E6EE),
    surfaceContainerHighest = Color(0xFFDFDFE8)
)

/** Re-colours the primary family with one accent (light themes get a darker shade for contrast). */
private fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val a = if (dark) accent else lerp(accent, Color.Black, 0.35f)
    val on = if (a.luminance() > 0.45f) Color(0xFF101018) else Color.White
    return copy(
        primary = a,
        onPrimary = on,
        primaryContainer = lerp(background, a, if (dark) 0.30f else 0.20f),
        onPrimaryContainer = if (dark) lerp(a, Color.White, 0.75f) else lerp(a, Color.Black, 0.65f)
    )
}

/**
 * [artTint]: colour of the playing album art (only when "Dynamic colour" is on); it replaces the accent.
 * Theme mode and accent come from [AppSettings].
 */
@Composable
fun ShineTheme(artTint: Color? = null, content: @Composable () -> Unit) {
    val mode = AppSettings.themeMode
    val dark = mode == 0 || (mode == 2 && isSystemInDarkTheme())
    val base = if (dark) Amoled else LightScheme
    val scheme = when {
        artTint != null -> base.withAccent(if (dark) lerp(artTint, Color.White, 0.5f) else artTint, dark)
        AppSettings.accentIndex != 0 -> base.withAccent(AppSettings.accentColor(), dark)
        else -> base
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = scheme, typography = ShineTypography, shapes = ShineShapes) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}
