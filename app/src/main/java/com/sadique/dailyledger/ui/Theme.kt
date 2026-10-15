package com.sadique.dailyledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LedgerLightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFF14B8A6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6FFFB),
    tertiary = Color(0xFFECFDF5),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
)

private val LedgerDarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFECFEFF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF1E293B),
    background = Color(0xFF020817),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF111827),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
)

private val AquaColors = lightColorScheme(
    primary = Color(0xFF0EA5E9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBF4FF),
    onPrimaryContainer = Color(0xFF082F49),
    secondary = Color(0xFF14B8A6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFECFEFF),
    background = Color(0xFFF0F9FF),
    onBackground = Color(0xFF0A1744),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A1744),
    surfaceVariant = Color(0xFFF8FBFF),
    onSurfaceVariant = Color(0xFF567094),
    outline = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
)

private val SunsetColors = lightColorScheme(
    primary = Color(0xFFF97316),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7D6),
    onPrimaryContainer = Color(0xFF4A1F00),
    secondary = Color(0xFFEC4899),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4F1),
    tertiary = Color(0xFFFFF7ED),
    background = Color(0xFFFFF7F3),
    onBackground = Color(0xFF201324),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF201324),
    surfaceVariant = Color(0xFFFEF2F2),
    onSurfaceVariant = Color(0xFF5B3E46),
    outline = Color(0xFFF3D8D7),
    error = Color(0xFFEF4444),
)

private val MidnightColors = darkColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E1065),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF111827),
    tertiary = Color(0xFF1F2937),
    background = Color(0xFF070B1A),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF111827),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
)

private val ForestColors = lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF22C55E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECFDF5),
    tertiary = Color(0xFFF0FDF4),
    background = Color(0xFFF4FBF6),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF365A47),
    outline = Color(0xFFE5F0E8),
    error = Color(0xFFEF4444),
)

private val RoseColors = lightColorScheme(
    primary = Color(0xFFEC4899),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE7F3),
    onPrimaryContainer = Color(0xFF4C0519),
    secondary = Color(0xFFFB7185),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF1F2),
    tertiary = Color(0xFFFFF1F2),
    background = Color(0xFFFFF7FB),
    onBackground = Color(0xFF1F2937),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFFFF5F7),
    onSurfaceVariant = Color(0xFF5B2D42),
    outline = Color(0xFFF8DDE7),
    error = Color(0xFFEF4444),
)

@Composable
fun DailyLedgerTheme(
    mode: String,
    content: @Composable () -> Unit,
) {
    val displayPrefs = LocalDisplayPreferences.current
    val deviceDensity = LocalDensity.current
    val adjustedDensity = Density(deviceDensity.density, deviceDensity.fontScale * displayPrefs.fontMultiplier)
    val normalized = mode.uppercase()
    val dark = when (normalized) {
        "DARK", "MIDNIGHT" -> true
        "LIGHT" -> false
        "AQUA", "SUNSET", "FOREST", "ROSE" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = when (normalized) {
        "AQUA" -> AquaColors
        "SUNSET" -> SunsetColors
        "MIDNIGHT" -> MidnightColors
        "FOREST" -> ForestColors
        "ROSE" -> RoseColors
        "DARK" -> LedgerDarkColors
        "LIGHT" -> AquaColors
        else -> if (dark) LedgerDarkColors else AquaColors
    }
    val readable = scheme.copy(
        primary = when (normalized) { "AQUA" -> Color(0xFF0369A1); "SUNSET" -> Color(0xFFB84308); "FOREST" -> Color(0xFF15803D); "ROSE" -> Color(0xFFBE185D); "MIDNIGHT" -> Color(0xFFC4B5FD); else -> scheme.primary },
        onPrimary = if (normalized == "MIDNIGHT") Color(0xFF2E1065) else scheme.onPrimary,
        onSecondaryContainer = scheme.onSurface,
        surfaceContainerLowest = scheme.surface,
        surfaceContainerLow = lerp(scheme.surface, scheme.primaryContainer, 0.15f),
        surfaceContainer = lerp(scheme.surface, scheme.primaryContainer, 0.25f),
        surfaceContainerHigh = lerp(scheme.surface, scheme.primaryContainer, 0.35f),
        surfaceContainerHighest = lerp(scheme.surface, scheme.primaryContainer, 0.45f),
        outline = lerp(scheme.onSurface, scheme.surface, 0.55f))
    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
    MaterialTheme(colorScheme = readable,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 37.sp, letterSpacing = (-.6).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
            headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp)
        ), content = content)
    }
}
