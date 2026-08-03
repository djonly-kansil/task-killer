package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val GeometricColorScheme = darkColorScheme(
    primary = GeometricPrimary,
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
  darkTheme: Boolean = true, // Force dark theme for Geometric Balance
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Disable dynamic colors to enforce theme
  content: @Composable () -> Unit,
) {
  val colorScheme = GeometricColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
