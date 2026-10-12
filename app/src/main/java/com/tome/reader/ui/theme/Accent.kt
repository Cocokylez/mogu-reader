package com.tome.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Helpers for the cover-derived accent colour (the Google Play Books-style tint
 * where each book carries the colour of its cover). Stored accents are plain ARGB
 * ints; these turn them into tasteful, readable Compose colours.
 */
object Accent {

    /** The raw accent as a [Color], or null when the book has none. */
    fun color(value: Int?): Color? = value?.let { Color(it) }

    /**
     * A soft surface tint: the accent gently blended into [surface] so it reads as
     * a coloured panel rather than a harsh fill. Falls back to [surface].
     */
    fun container(value: Int?, surface: Color, strength: Float = 0.30f): Color {
        val accent = color(value) ?: return surface
        return lerp(surface, accent, strength)
    }

    /** Black or white text/icon colour that stays legible on [background]. */
    fun on(background: Color): Color =
        if (background.luminance() > 0.5f) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
}
