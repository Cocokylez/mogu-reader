package com.tome.reader.ui.reader

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Frame-timed vertical scrolling; manual dragging pauses it and backgrounding stops frames. */
@Composable
fun ReaderAutoScroll(
    listState: LazyListState,
    enabled: Boolean,
    speed: Float,
    onPause: () -> Unit,
) {
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val pause by rememberUpdatedState(onPause)
    val lifecycleOwner = LocalLifecycleOwner.current
    val pixelsPerSecond = with(LocalDensity.current) { speed.coerceIn(20f, 160f).dp.toPx() }
    LaunchedEffect(dragged) { if (dragged) pause() }
    LaunchedEffect(enabled, pixelsPerSecond, lifecycleOwner, listState) {
        if (!enabled) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                if (previous != 0L && listState.canScrollForward) {
                    val seconds = ((frame - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                    listState.scrollBy(pixelsPerSecond * seconds)
                }
                previous = frame
            }
        }
    }
}

@Composable
fun AutoScrollControls(
    running: Boolean,
    speed: Float,
    onToggle: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    var chosenSpeed by remember(speed) { mutableStateOf(speed.coerceIn(20f, 160f)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle) {
            Icon(
                if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (running) "Pause auto-scroll" else "Start auto-scroll",
            )
        }
        if (running) {
            Slider(
                value = chosenSpeed,
                onValueChange = { chosenSpeed = it },
                onValueChangeFinished = { onSpeed(chosenSpeed) },
                valueRange = 20f..160f,
                modifier = Modifier.width(88.dp),
            )
        }
    }
}
