package com.example.videosaver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00332E),
    background = Color(0xFFF5F7FF),
    onBackground = Color(0xFF171A23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A23),
    surfaceVariant = Color(0xFFE9ECF8),
    onSurfaceVariant = Color(0xFF44485A),
    outline = Color(0xFF75798B),
    error = Color(0xFFD32F2F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEB9FF),
    onPrimary = Color(0xFF14216B),
    primaryContainer = Color(0xFF2A3BA8),
    onPrimaryContainer = Color(0xFFDEE4FF),
    secondary = Color(0xFF7BD3C7),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFB2DFDB),
    background = Color(0xFF0F1219),
    onBackground = Color(0xFFE2E3EA),
    surface = Color(0xFF161A24),
    onSurface = Color(0xFFE2E3EA),
    surfaceVariant = Color(0xFF232838),
    onSurfaceVariant = Color(0xFFB8BDD0),
    outline = Color(0xFF8B90A3),
    error = Color(0xFFFF6B6B),
)

@Composable
fun QingYinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
