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

private val DarkColorScheme =
  darkColorScheme(
    primary = PolishDarkPrimary,
    onPrimary = PolishDarkOnPrimary,
    secondary = PolishDarkSecondary,
    onSecondary = PolishDarkOnBackground,
    tertiary = PolishDarkTertiary,
    background = PolishDarkBackground,
    onBackground = PolishDarkOnBackground,
    surface = PolishDarkSurface,
    onSurface = PolishDarkOnSurface,
    outline = PolishDarkOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PolishLightPrimary,
    onPrimary = PolishLightOnPrimary,
    secondary = PolishLightSecondary,
    onSecondary = PolishLightOnBackground,
    tertiary = PolishLightTertiary,
    background = PolishLightBackground,
    onBackground = PolishLightOnBackground,
    surface = PolishLightSurface,
    onSurface = PolishLightOnSurface,
    outline = PolishLightOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set default dynamicColor to false to preserve carefully crafted palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
