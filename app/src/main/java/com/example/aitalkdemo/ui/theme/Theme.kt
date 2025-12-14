package com.example.aitalkdemo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat

/**
 * Defines the Material3 colour scheme and applies it to the content. A dynamic
 * colour scheme is attempted on Android 12+ devices, but falls back to a
 * static palette based on [DemoColors] otherwise.
 *
 * @param darkTheme whether to use dark colours
 * @param content composable tree wrapped by the theme
 */
@Composable
fun AiTalkDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Static colour palettes
    val LightColorScheme = lightColorScheme(
        primary      = DemoColors.Primary,
        onPrimary    = Color.White,
        secondary    = DemoColors.GradientStart,
        onSecondary  = Color.White,
        tertiary     = DemoColors.GradientEnd,
        onTertiary   = Color.White,
        background   = DemoColors.GradientEnd,
        onBackground = Color.White,
        surface      = DemoColors.GradientStart,
        onSurface    = Color.White
    )
    val DarkColorScheme = darkColorScheme(
        primary      = DemoColors.Primary,
        onPrimary    = Color.White,
        secondary    = DemoColors.GradientEnd,
        onSecondary  = Color.White,
        tertiary     = DemoColors.GradientStart,
        onTertiary   = Color.White,
        background   = DemoColors.GradientStart,
        onBackground = Color.White,
        surface      = DemoColors.GradientEnd,
        onSurface    = Color.White
    )

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        val currentActivity = LocalContext.current as? Activity
        currentActivity?.window?.statusBarColor =
            colorScheme.primary.toArgb()
        ViewCompat.getWindowInsetsController(view)
            ?.isAppearanceLightStatusBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
