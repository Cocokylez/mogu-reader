package com.tome.reader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Tome "glass" palette from the redesign: a violet accent (--acc #7C6DF9) on a
// deep near-black navy, with three text tints (--t1/--t2/--t3) and elevated
// surfaces. The dark scheme is the primary experience; light + sepia stay for
// comfortable long-form reading and share the violet accent.

// Design tokens (kept here so the rest of the app can reference the exact values).
val TomeViolet = Color(0xFF7C6DF9)
val TomeBg = Color(0xFF0A0A0B)       // matte black, neutral (was violet-tinted)
val TomeSurface = Color(0xFF121213)  // matte
val TomeElevated = Color(0xFF1A1A1C) // matte
val TomeText1 = Color(0xFFEDEDEE)    // softened off-white
val TomeText2 = Color(0xFF93939A)    // neutral grey
val TomeText3 = Color(0xFF56565C)    // neutral grey

val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E4FF),
    onPrimaryContainer = Color(0xFF15005B),
    secondary = Color(0xFF5D5B72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7E4F4),
    onSecondaryContainer = Color(0xFF1C1A2C),
    background = Color(0xFFF7F5FC),
    onBackground = Color(0xFF1D1B24),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B24),
    surfaceVariant = Color(0xFFE9E5F0),
    onSurfaceVariant = Color(0xFF4A4656),
    surfaceContainerHighest = Color(0xFFEDE9F5),
    outline = Color(0xFF7A7585),
    outlineVariant = Color(0xFFCBC5D6),
)

val DarkColors = darkColorScheme(
    primary = TomeViolet,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2C2556),
    onPrimaryContainer = Color(0xFFDDD8FF),
    secondary = Color(0xFF9B9AC0),
    onSecondary = Color(0xFF13131F),
    secondaryContainer = TomeElevated,
    onSecondaryContainer = TomeText1,
    background = TomeBg,
    onBackground = TomeText1,
    surface = TomeSurface,
    onSurface = TomeText1,
    surfaceVariant = TomeElevated,
    onSurfaceVariant = TomeText2,
    surfaceContainerHighest = TomeElevated,
    outline = TomeText3,
    outlineVariant = Color(0xFF262628),
)

// Sepia reuses the light scheme shape with warm paper tones.
val SepiaColors = lightColorScheme(
    primary = Color(0xFF8B5E34),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3D7B8),
    onPrimaryContainer = Color(0xFF2E1704),
    secondary = Color(0xFF725B45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9D8C3),
    onSecondaryContainer = Color(0xFF2A1B0B),
    background = Color(0xFFF3E7D3),
    onBackground = Color(0xFF35291E),
    surface = Color(0xFFF9EEDC),
    onSurface = Color(0xFF35291E),
    surfaceVariant = Color(0xFFE6D7BE),
    onSurfaceVariant = Color(0xFF5A4937),
    surfaceContainerHighest = Color(0xFFEADBC6),
    outline = Color(0xFF897761),
    outlineVariant = Color(0xFFD4C3AA),
)
