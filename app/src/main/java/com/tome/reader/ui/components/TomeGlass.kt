package com.tome.reader.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The redesign's glass tokens: `--glass` (fill), `--glass2` (raised fill),
 * `--hair` (hairline border) and `--shine` (bright top edge).
 */
/**
 * The blur source for floating glass overlays.
 *
 * [TomeGlassBackdrop] creates it; the screen decides which content feeds the
 * blur by applying `Modifier.haze(state)` to it, and floating panels sample it
 * with `blurBackdrop = true`. Deliberately not applied to the backdrop art
 * itself — doing that renders the blur onto the layer *behind* the overlays,
 * which shows up as a hard rectangle behind them rather than frosted glass.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

@Immutable
data class TomeGlassTokens(
    val fill: Color,
    val raised: Color,
    val hairline: Color,
    val shine: Color,
)

/**
 * The softly lit canvas behind the Tome Glass surfaces. Keeping the blooms in a
 * single parent is cheaper than asking every translucent card to light itself.
 */
@Composable
fun TomeGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    // Matte finish: the blooms used to wash the page in accent and cyan, which
    // read as bright and glossy. They are now a whisper -- just enough to stop the
    // background being dead flat.
    val accentAlpha = if (dark) 0.05f else 0.04f
    val cyanAlpha = if (dark) 0.03f else 0.03f
    val hazeState = remember { HazeState() }

    Box(modifier = modifier.background(colors.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.maxDimension * 0.72f
            val accentCenter = Offset(size.width * 0.08f, size.height * 0.03f)
            val cyanCenter = Offset(size.width * 1.02f, size.height * 0.72f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.primary.copy(alpha = accentAlpha), Color.Transparent),
                    center = accentCenter,
                    radius = radius,
                ),
                radius = radius,
                center = accentCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0891B2).copy(alpha = cyanAlpha), Color.Transparent),
                    center = cyanCenter,
                    radius = radius * 0.82f,
                ),
                radius = radius * 0.82f,
                center = cyanCenter,
            )
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}

@Composable
fun rememberTomeGlassTokens(): TomeGlassTokens {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(dark) {
        if (dark) {
            TomeGlassTokens(
                fill = Color.White.copy(alpha = 0.07f),
                raised = Color.White.copy(alpha = 0.12f),
                hairline = Color.White.copy(alpha = 0.13f),
                shine = Color.White.copy(alpha = 0.10f),
            )
        } else {
            // Light and sepia share the design's light glass: a bright translucent
            // fill with a dark hairline, so surfaces stay legible on pale paper.
            TomeGlassTokens(
                fill = Color.White.copy(alpha = 0.58f),
                raised = Color(0xFF1D1B24).copy(alpha = 0.06f),
                hairline = Color(0xFF1D1B24).copy(alpha = 0.10f),
                shine = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * A translucent panel with a hairline border and a bright top edge — the surface
 * used by the redesign's nav pill, layout switcher, hero card and list rows.
 */
@Composable
fun TomeGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    raised: Boolean = false,
    elevation: Dp = 0.dp,
    shadowColor: Color? = null,
    blurBackdrop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    // NOTE ON [elevation]: Android draws an elevation shadow underneath the whole
    // shape, and this panel's fill is only ~7% white, so that shadow shows THROUGH
    // the card. Its falloff ends partway down and reads as a rectangle inside the
    // card -- measured as a ~5/255 step at the same y across the full width, which
    // disappeared completely at elevation 0. Only pass an elevation to a panel
    // that is opaque enough to hide it.
    val glass = rememberTomeGlassTokens()
    // Only panels that float *over* scrolling content blur it. Inline panels
    // sit inside that content, so blurring there would sample themselves.
    val hazeState = if (blurBackdrop) LocalHazeState.current else null

    // The design tops every glass panel with a brighter edge that fades out; a
    // flat fill was most of why the first pass read dull.
    // A two-stop gradient to Transparent stopped dead about a third of the way
    // down the panel instead of fading out, and that hard boundary read as a
    // rectangle inside every card. Explicit stops keep the highlight short and
    // deliberate, then hold a long, almost-invisible tail to the bottom edge so
    // there is nowhere for an edge to appear. Measured with the green diagnostic:
    // the falloff is now monotonic from the top edge to the bottom of the panel.
    // Over a live blur the tint is only a wash; without one it carries the
    // whole surface.
    // Explicit stops so the highlight fades smoothly the whole way down instead
    // of a two-stop ramp that visibly gives out partway.
    val sheen = Brush.verticalGradient(
        0f to glass.shine.copy(alpha = glass.shine.alpha * 0.45f),
        0.18f to glass.shine.copy(alpha = glass.shine.alpha * 0.16f),
        0.45f to glass.shine.copy(alpha = glass.shine.alpha * 0.05f),
        1f to Color.Transparent,
    )

    val fill = when {
        raised -> glass.raised
        hazeState != null -> glass.fill.copy(alpha = glass.fill.alpha * 0.5f)
        else -> glass.fill
    }

    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        clip = false,
                        ambientColor = shadowColor ?: DefaultShadowColor,
                        spotColor = shadowColor ?: DefaultShadowColor,
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(
                if (hazeState != null) {
                    // haze's default style mixes in film grain, which on the nav
                    // pill reads as a sandy, rough texture over the blur rather
                    // than clean frosted glass.
                    Modifier.hazeChild(
                        state = hazeState,
                        shape = shape,
                        style = HazeStyle(noiseFactor = 0f),
                    )
                } else {
                    Modifier
                }
            )
            .background(fill)
            .background(sheen)
            .border(0.5.dp, glass.hairline, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(glass.shine),
        )
        content()
    }
}
