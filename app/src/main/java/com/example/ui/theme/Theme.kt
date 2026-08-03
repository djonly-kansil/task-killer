package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GeometricColorScheme = darkColorScheme(
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

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = GeometricColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
