package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GeometricDarkScheme = darkColorScheme(
    primary = GeometricPrimary,
    onPrimary = Color.White,
    background = GeometricBackground,
    surface = GeometricSurface,
    surfaceVariant = GeometricSurfaceVariant,
    onBackground = GeometricOnBackground,
    onSurface = GeometricOnBackground,
    onSurfaceVariant = GeometricOnSurfaceVariant,
    error = GeometricError,
    onError = GeometricOnError,
    outline = GeometricOutline,
    outlineVariant = GeometricOutline
)

private val GeometricLightScheme = lightColorScheme(
    primary = GeometricPrimaryLight,
    onPrimary = Color.White,
    background = GeometricBackgroundLight,
    surface = GeometricSurfaceLight,
    surfaceVariant = GeometricSurfaceVariantLight,
    onBackground = GeometricOnBackgroundLight,
    onSurface = GeometricOnBackgroundLight,
    onSurfaceVariant = GeometricOnSurfaceVariantLight,
    error = GeometricError,
    onError = GeometricOnError,
    outline = GeometricOutlineLight,
    outlineVariant = GeometricOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) GeometricDarkScheme else GeometricLightScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
