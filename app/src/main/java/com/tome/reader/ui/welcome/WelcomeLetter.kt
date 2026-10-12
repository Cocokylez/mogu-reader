package com.tome.reader.ui.welcome

import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tome.reader.R
import kotlinx.coroutines.delay

private val Blush = Color(0xFFFFC2DD)
private val Rose = Color(0xFFF48FB1)
private val DeepRose = Color(0xFFD81B60)
private val Paper = Color(0xFFFFF7FB)

/**
 * The letter shown once, on the very first launch.
 *
 * A closed pink envelope sits on a dimmed screen; tapping it swings the flap open,
 * lifts the note out, and the note's "blehh" wobbles gently so the greeting feels
 * alive rather than a static dialog. Dismissing marks it seen, so it never
 * reappears.
 */
@Composable
fun WelcomeLetter(onDismiss: () -> Unit) {
    var opened by remember { mutableStateOf(false) }

    // Flap swings back past vertical so it reads as lifting toward the viewer.
    val flap = remember { Animatable(0f) }
    // The note travels from inside the pocket to above the envelope.
    val lift = remember { Animatable(0f) }

    // Confetti fires with the flap and stays out while the note is up.
    val burst = remember { Animatable(0f) }

    LaunchedEffect(opened) {
        if (!opened) return@LaunchedEffect
        flap.animateTo(-165f, tween(durationMillis = 620, easing = FastOutSlowInEasing))
        launch { burst.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing)) }
        lift.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    // A slow breath on the closed envelope hints that it can be tapped.
    val idle = rememberInfiniteTransition(label = "idle")
    val bob by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f))
            // detectTapGestures rather than clickable: the envelope and note cover
            // most of the screen, and a child clickable consumes the tap in its own
            // bounds, so "tap anywhere" only worked on the thin margins around them.
            .pointerInput(opened) {
                detectTapGestures {
                    if (opened) onDismiss() else opened = true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(if (opened) 520.dp else 210.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Envelope body.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(186.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationY = if (opened) 0f else (bob * 6f - 3f)
                        }
                        .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = DeepRose, spotColor = DeepRose)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(Rose, Blush)))
                        // No clickable here: the scrim's tap detector already
                        // opens and closes, and a clickable in these bounds would
                        // eat the closing tap.
                ) {
                    // The pocket: two diagonals meeting in the middle, like a real
                    // envelope's folded front.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val pocket = Path().apply {
                                    moveTo(0f, size.height)
                                    lineTo(0f, size.height * 0.18f)
                                    lineTo(size.width / 2f, size.height * 0.72f)
                                    lineTo(size.width, size.height * 0.18f)
                                    lineTo(size.width, size.height)
                                    close()
                                }
                                onDrawWithContent {
                                    drawContent()
                                    drawPath(pocket, Blush.copy(alpha = 0.85f))
                                }
                            },
                    )
                }

                // Top flap, hinged along its upper edge.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                        .fillMaxWidth()
                        .height(96.dp)
                        .graphicsLayer {
                            rotationX = flap.value
                            // Without depth the fold looks flat instead of hinged.
                            cameraDistance = 14f * density
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            // Past vertical the flap is behind the envelope in
                            // reality, so ease it out rather than let it hang over
                            // the note.
                            alpha = 1f - (flap.value / -165f).coerceIn(0f, 1f) * 0.85f
                        }
                        .drawWithCache {
                            val flapPath = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            onDrawWithContent {
                                drawPath(flapPath, Rose)
                                drawContent()
                            }
                        },
                )

                // The note is drawn last so it lifts clear of the envelope and the
                // opened flap instead of being covered by them. Its own graphics
                // layer does the travel, fade and settle.
                if (opened) {
                    Note(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                translationY = (1f - lift.value) * 240f
                                alpha = lift.value.coerceIn(0f, 1f)
                                val settle = 0.85f + 0.15f * lift.value.coerceIn(0f, 1f)
                                scaleX = settle
                                scaleY = settle
                            },
                    )
                }

                if (burst.value > 0f) {
                    Sparkles(
                        burst = burst.value,
                        twinkle = bob,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (!opened) {
                Text(
                    text = "A letter for you - tap to open",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 26.dp),
                )
            } else {
                // An explicit control rather than only a scrim tap: the note and
                // envelope cover most of the screen, so "tap anywhere" leaves very
                // little anywhere to tap.
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W700,
                    color = DeepRose,
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 30.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Note(modifier: Modifier = Modifier) {
    // The greeting drifts and tilts so it reads as floating off the paper.
    val float = rememberInfiniteTransition(label = "float")
    val drift by float.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(250.dp)
            .shadow(20.dp, RoundedCornerShape(18.dp), ambientColor = DeepRose, spotColor = DeepRose)
            .clip(RoundedCornerShape(18.dp))
            .background(Paper)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.welcome_cat),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(158.dp)
                .graphicsLayer {
                    translationY = drift * -8f
                    rotationZ = drift * 3f - 1.5f
                },
        )
        Text(
            text = "blehh",
            fontSize = 36.sp,
            fontWeight = FontWeight.W800,
            color = DeepRose,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = drift * -12f
                rotationZ = drift * 5f - 2.5f
            },
        )
        Text(
            text = "by Kyle",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W600,
            color = DeepRose.copy(alpha = 0.7f),
        )
    }
}

/**
 * Confetti that bursts out of the envelope, then keeps twinkling.
 *
 * Deliberately hand-drawn rather than an image sequence: a handful of circles on
 * fixed trajectories costs nothing to animate and scales to any screen. Positions
 * come from a fixed seed so the burst looks scattered but never flickers between
 * recompositions.
 */
@Composable
private fun Sparkles(burst: Float, twinkle: Float, modifier: Modifier = Modifier) {
    val bits = remember {
        val rng = kotlin.random.Random(seed = 24)
        List(34) {
            SparkleBit(
                angle = rng.nextFloat() * 2f * Math.PI.toFloat(),
                distance = 0.35f + rng.nextFloat() * 0.65f,
                radius = 5f + rng.nextFloat() * 11f,
                phase = rng.nextFloat(),
                color = listOf(Rose, DeepRose, Color(0xFFFFD54F), Color.White)[rng.nextInt(4)],
            )
        }
    }
    Canvas(modifier = modifier) {
        val originX = size.width / 2f
        val originY = size.height * 0.72f
        val reach = size.minDimension * 1.15f
        bits.forEach { bit ->
            val travel = burst * bit.distance
            val x = originX + kotlin.math.cos(bit.angle) * reach * travel
            // Slight upward bias so the burst rises out of the envelope.
            val y = originY + kotlin.math.sin(bit.angle) * reach * travel - burst * 220f
            val blink = 0.55f + 0.45f * kotlin.math.sin((twinkle + bit.phase) * 2f * Math.PI.toFloat())
            drawCircle(
                color = bit.color,
                radius = bit.radius * (0.5f + burst * 0.5f),
                center = Offset(x, y),
                alpha = (burst * blink).coerceIn(0f, 1f),
            )
        }
    }
}

private data class SparkleBit(
    val angle: Float,
    val distance: Float,
    val radius: Float,
    val phase: Float,
    val color: Color,
)
