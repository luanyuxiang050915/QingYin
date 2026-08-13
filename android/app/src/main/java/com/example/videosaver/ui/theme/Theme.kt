package com.example.videosaver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE4FF),
    onPrimaryContainer = Color(0xFF14216B),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F6FE),
    onSecondaryContainer = Color(0xFF171A23),
    background = Color(0xFFF0F2F7),
    onBackground = Color(0xFF171A23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A23),
    surfaceVariant = Color(0xFFE4EBFA),
    onSurfaceVariant = Color(0xFF3D4457),
    outline = Color(0xFF7A8194),
    error = Color(0xFFD32F2F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEB9FF),
    onPrimary = Color(0xFF14216B),
    primaryContainer = Color(0xFF2A3BA8),
    onPrimaryContainer = Color(0xFFDEE4FF),
    secondary = Color(0xFF7BD3C7),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF2A3242),
    onSecondaryContainer = Color(0xFFE2E6F0),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE2E4EC),
    surface = Color(0xFF1C212B),
    onSurface = Color(0xFFE2E4EC),
    surfaceVariant = Color(0xFF242B39),
    onSurfaceVariant = Color(0xFFB9C0D2),
    outline = Color(0xFF8B92A5),
    error = Color(0xFFFF6B6B),
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
