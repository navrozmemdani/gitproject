package com.visualtrace.ai.core.designsystem

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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visualtrace.ai.domain.model.ThemeMode

object VtColors {
    val Navy950 = Color(0xFF070B1A)
    val Navy900 = Color(0xFF0B1230)
    val Navy800 = Color(0xFF131C45)
    val Navy700 = Color(0xFF1E2A5E)
    val Blue500 = Color(0xFF3D7BFF)
    val Blue300 = Color(0xFF8FB2FF)
    val Violet500 = Color(0xFF8B5CF6)
    val Violet300 = Color(0xFFC4B5FD)
    val Cyan400 = Color(0xFF22D3EE)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
}

private val DarkScheme = darkColorScheme(
    primary = VtColors.Blue300,
    onPrimary = VtColors.Navy950,
    primaryContainer = VtColors.Navy700,
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = VtColors.Violet300,
    onSecondary = Color(0xFF1E1046),
    secondaryContainer = Color(0xFF3A2A75),
    onSecondaryContainer = Color(0xFFEDE7FF),
    tertiary = VtColors.Cyan400,
    background = VtColors.Navy950,
    onBackground = Color(0xFFE6E9F5),
    surface = VtColors.Navy900,
    onSurface = Color(0xFFE6E9F5),
    surfaceVariant = VtColors.Navy800,
    onSurfaceVariant = Color(0xFFB4BCD9),
    surfaceContainer = Color(0xFF10183A),
    surfaceContainerHigh = Color(0xFF162048),
    surfaceContainerHighest = Color(0xFF1C2754),
    surfaceContainerLow = Color(0xFF0D1434),
    outline = Color(0xFF4A5480),
    outlineVariant = Color(0xFF2A3463),
    error = Color(0xFFFF8A80),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2350D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0B1A5C),
    secondary = Color(0xFF6D3FE0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5FF),
    onSecondaryContainer = Color(0xFF250B69),
    tertiary = Color(0xFF0E8FA8),
    background = Color(0xFFF8F9FD),
    onBackground = Color(0xFF111833),
    surface = Color.White,
    onSurface = Color(0xFF111833),
    surfaceVariant = Color(0xFFEEF1FA),
    onSurfaceVariant = Color(0xFF4B5375),
    surfaceContainer = Color(0xFFF2F4FB),
    surfaceContainerHigh = Color(0xFFECEFF8),
    surfaceContainerHighest = Color(0xFFE5E9F5),
    surfaceContainerLow = Color(0xFFF6F7FC),
    outline = Color(0xFF8A91B0),
    outlineVariant = Color(0xFFD5DAEA),
)

private val Default = FontFamily.Default

val VtTypography = Typography(
    displaySmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Default, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Default, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Default, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

val VtShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Immutable
data class VtExtendedColors(val heroGradient: Brush, val accentGradient: Brush, val success: Color, val warning: Color, val danger: Color)

val LocalVtColors = staticCompositionLocalOf {
    VtExtendedColors(Brush.linearGradient(listOf(VtColors.Navy900, VtColors.Navy700)),
        Brush.linearGradient(listOf(VtColors.Blue500, VtColors.Violet500)), VtColors.Success, VtColors.Warning, VtColors.Danger)
}

@Composable
fun VisualTraceTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val extended = if (dark) {
        VtExtendedColors(
            heroGradient = Brush.linearGradient(listOf(Color(0xFF0B1230), Color(0xFF1B2366), Color(0xFF3A1F7A))),
            accentGradient = Brush.linearGradient(listOf(VtColors.Blue500, VtColors.Violet500)),
            success = VtColors.Success, warning = VtColors.Warning, danger = Color(0xFFFF8A80),
        )
    } else {
        VtExtendedColors(
            heroGradient = Brush.linearGradient(listOf(Color(0xFFE8EEFF), Color(0xFFEDE5FF))),
            accentGradient = Brush.linearGradient(listOf(Color(0xFF2350D8), Color(0xFF6D3FE0))),
            success = Color(0xFF15803D), warning = Color(0xFFB45309), danger = Color(0xFFB91C1C),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalVtColors provides extended) {
        MaterialTheme(colorScheme = scheme, typography = VtTypography, shapes = VtShapes, content = content)
    }
}
