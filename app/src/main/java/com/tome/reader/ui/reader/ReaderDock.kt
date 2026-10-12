package com.tome.reader.ui.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.ui.theme.GlassSpec
import kotlin.math.roundToInt

/**
 * The redesign's reader dock: one glass capsule holding chapter transport, the
 * page scrubber, and — behind a chevron — reading direction and brightness.
 *
 * Everything here is drawn by hand rather than assembled from Material controls.
 * Material's Slider, Switch and TextButton each impose their own metrics (a 48dp
 * minimum touch target, their own thumb and track sizing), which is exactly what
 * pulled earlier passes off the design.
 */
@Composable
fun ReaderDock(
    current: Int,
    total: Int,
    accent: Color,
    onSeek: (Int) -> Unit,
    direction: ReadingDirection,
    onDirection: (ReadingDirection) -> Unit,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    onPrevChapter: (() -> Unit)?,
    onNextChapter: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        // rgba(24,24,38,0.55) — glass, so the page colour comes through.
        color = Color(0x8C181826),
        contentColor = Color.White,
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = GlassSpec.ShadowReaderDock,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    ) {
                        DirectionPill("Left → Right", direction == ReadingDirection.LTR, accent, Modifier.weight(1f)) {
                            onDirection(ReadingDirection.LTR)
                        }
                        DirectionPill("Right → Left", direction == ReadingDirection.RTL, accent, Modifier.weight(1f)) {
                            onDirection(ReadingDirection.RTL)
                        }
                        DirectionPill("Webtoon", direction == ReadingDirection.WEBTOON, accent, Modifier.weight(1f)) {
                            onDirection(ReadingDirection.WEBTOON)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Icon(
                            Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(17.dp),
                        )
                        StepButton("−") { onBrightness((brightness - 0.2f).coerceIn(0.2f, 1f)) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(alpha = 0.14f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(brightness.coerceIn(0f, 1f))
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White),
                            )
                        }
                        StepButton("+") { onBrightness((brightness + 0.2f).coerceIn(0.2f, 1f)) }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            ) {
                CircleAction(Icons.Default.SkipPrevious, "Previous chapter", onPrevChapter)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${(current + 1).coerceAtLeast(1)}",
                            style = GlassSpec.HistoryMeta,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                        Text(
                            text = "$total",
                            style = GlassSpec.HistoryMeta,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                    Scrubber(
                        fraction = if (total > 1) current.toFloat() / (total - 1) else 0f,
                        accent = accent,
                        onSeekFraction = { f ->
                            if (total > 1) onSeek((f * (total - 1)).roundToInt().coerceIn(0, total - 1))
                        },
                    )
                }
                CircleAction(Icons.Default.SkipNext, "Next chapter", onNextChapter)
                CircleAction(
                    icon = Icons.Default.KeyboardArrowUp,
                    description = if (expanded) "Collapse reader options" else "Expand reader options",
                    onClick = { expanded = !expanded },
                    iconModifier = Modifier.rotate(chevronTurn),
                )
            }
        }
    }
}

/** 5dp track, accent fill, 17dp white knob — tap or drag anywhere on the row. */
@Composable
private fun Scrubber(fraction: Float, accent: Color, onSeekFraction: (Float) -> Unit) {
    var widthPx by remember { mutableIntStateOf(1) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragFraction >= 0f) dragFraction else fraction.coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragFraction >= 0f) onSeekFraction(dragFraction)
                        dragFraction = -1f
                    },
                    onDragCancel = { dragFraction = -1f },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(shown)
                .height(5.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent),
        )
        val knob = 17.dp
        Box(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (shown * widthPx - knob.toPx() / 2f).roundToInt().coerceAtLeast(0),
                        0,
                    )
                }
                .size(knob)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    description: String,
    onClick: (() -> Unit)?,
    iconModifier: Modifier = Modifier,
) {
    val enabled = onClick != null
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .then(if (enabled) Modifier.clickable { onClick!!() } else Modifier),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = iconModifier.size(18.dp),
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Text(text = label, style = GlassSpec.SettingLabel, color = Color.White)
    }
}

@Composable
private fun DirectionPill(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else Color.White.copy(alpha = 0.10f))
            .border(
                0.5.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = GlassSpec.ShelfItemTitle,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
        )
    }
}
