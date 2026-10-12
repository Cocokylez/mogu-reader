package com.tome.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.ThemeOption

/**
 * Applies the chosen [ThemeOption] across the app, with a user-selectable [accent]
 * overriding the scheme's primary colour. SEPIA is a warm light theme; SYSTEM
 * follows the OS.
 */
@Composable
fun TomeTheme(
    theme: ThemeOption,
    accent: Int = AppSettings.DEFAULT_ACCENT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = theme == ThemeOption.DARK || (theme == ThemeOption.SYSTEM && systemDark)

    val base = when (theme) {
        ThemeOption.SYSTEM -> if (systemDark) DarkColors else LightColors
        ThemeOption.LIGHT -> LightColors
        ThemeOption.DARK -> DarkColors
        ThemeOption.SEPIA -> SepiaColors
    }
    // The chosen accent drives primary everywhere (nav, chips, CTAs, progress).
    val colors = base.copy(primary = Color(accent), onPrimary = Color(0xFFFFFFFF))

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = TomeTypography,
        content = content,
    )
}
