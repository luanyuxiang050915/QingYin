package com.example.videosaver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 品牌色：青蓝 → 紫（与桌面版一致） */
val BrandBlue = Color(0xFF4A7CFF)
val BrandPurple = Color(0xFF9C6CFF)
val BrandCyan = Color(0xFF00C6FF)

/** 主按钮/品牌渐变 */
val BrandGradient = Brush.linearGradient(listOf(BrandBlue, BrandPurple))
val BrandGradientSoft = Brush.linearGradient(listOf(BrandCyan, BrandBlue, BrandPurple))

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE7FF),
    onPrimaryContainer = Color(0xFF14356F),
    secondary = BrandPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE3FF),
    onSecondaryContainer = Color(0xFF3D1F70),
    tertiary = Color(0xFF00B0A6),
    onTertiary = Color.White,
    background = Color(0xFFF6F7FC),
    onBackground = Color(0xFF1A1D29),
    surface = Color.White,
    onSurface = Color(0xFF1A1D29),
    surfaceVariant = Color(0xFFEEF0F8),
    onSurfaceVariant = Color(0xFF4A4E63),
    outline = Color(0xFF9AA0B5),
    outlineVariant = Color(0xFFE2E5F0),
    error = Color(0xFFE5484D),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF0D2654),
    primaryContainer = Color(0xFF1E3B78),
    onPrimaryContainer = Color(0xFFDDE7FF),
    secondary = Color(0xFFC6B1FF),
    onSecondary = Color(0xFF33166B),
    secondaryContainer = Color(0xFF4A2D8C),
    onSecondaryContainer = Color(0xFFEDE3FF),
    tertiary = Color(0xFF4FE3D9),
    onTertiary = Color(0xFF003734),
    background = Color(0xFF0F1118),
    onBackground = Color(0xFFE6E8F2),
    surface = Color(0xFF171A24),
    onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF1F2230),
    onSurfaceVariant = Color(0xFFB9BDD0),
    outline = Color(0xFF6E7388),
    outlineVariant = Color(0xFF2C3040),
    error = Color(0xFFFF6B6E),
    onError = Color(0xFF5C1012),
)

@Composable
fun VideoSaverTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
