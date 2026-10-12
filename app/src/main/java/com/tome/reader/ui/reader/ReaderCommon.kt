package com.tome.reader.ui.reader

import androidx.compose.foundation.layout.Column
import com.tome.reader.ui.theme.GlassSpec
import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tome.reader.data.source.PageImageProvider
import com.tome.reader.ui.components.CenteredMessage
import com.tome.reader.ui.components.TomeGlassCircleButton
import com.tome.reader.ui.theme.Accent
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/** Keeps the screen awake while reading, restoring normal behaviour on exit. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Full-screen reading. Home screens retain the Android status bar for safe-inset
 * spacing; the reader hides it and only reveals the navigation bar while controls
 * are visible. Both bars are restored when the reader leaves.
 */
@Composable
fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.let {
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) {
                it.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                it.hide(WindowInsetsCompat.Type.statusBars())
                it.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Bridge for hardware volume-key paging. The active reader installs a handler;
 * [com.tome.reader.MainActivity] consults it from onKeyDown so the keys page
 * instead of changing volume. Cleared automatically when the reader leaves.
 */
object ReaderKeyHandler {
    /** Returns true if the key was consumed. `up` = volume-up was pressed. */
    @Volatile
    var onVolumeKey: ((up: Boolean) -> Boolean)? = null
}

/** Installs a volume-key pager while [enabled]: volume-down = next, up = previous. */
@Composable
fun VolumeKeyPaging(enabled: Boolean, onPage: (forward: Boolean) -> Unit) {
    val latest by rememberUpdatedState(onPage)
    DisposableEffect(enabled) {
        ReaderKeyHandler.onVolumeKey = if (enabled) {
            { up -> latest(!up); true }
        } else {
            null
        }
        onDispose { ReaderKeyHandler.onVolumeKey = null }
    }
}

data class PageLoadFailure(val message: String)

/** Lazily resolves the Coil model (File/Uri) for one page on a background coroutine. */
@Composable
fun rememberPageModel(provider: PageImageProvider, index: Int): State<Any?> =
    produceState<Any?>(initialValue = null, provider, index) {
        value = try { provider.getPage(index) ?: PageLoadFailure("This page is missing from the book.") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { PageLoadFailure(e.message ?: "Could not load this page") }
    }

/** Translucent top bar shown over a reader when controls are visible. */
@Composable
fun ReaderOverlayBar(
    title: String,
    onBack: () -> Unit,
    accent: Int? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent),
                ),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            TomeGlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = title,
                    style = GlassSpec.ListTitle.copy(fontWeight = FontWeight.W700),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = GlassSpec.ListMeta,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            actions()
        }
    }
}


@Composable
fun ReaderLoading(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ReaderOverlayBar(title = "", onBack = onBack)
    }
}

@Composable
fun ReaderError(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CenteredMessage(message)
        ReaderOverlayBar(title = "", onBack = onBack)
    }
}

/**
 * Bottom reader bar with a draggable seek slider, used to jump anywhere in the
 * book. Shows "current / total"; when there is nothing to seek (1 item) it just
 * shows the count. [unit] labels what is being counted ("page", "chapter").
 */
@Composable
fun ReaderSeekBar(
    current: Int,
    total: Int,
    accent: Int?,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "page",
) {
    // Design: rgba(24,24,38,0.55) with a hairline, an inset shine and a
    // 0 16px 44px rgba(0,0,0,0.55) shadow — glass, not an opaque slab.
    Surface(
        color = Color(0x8C181826),
        contentColor = Color.White,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = GlassSpec.ShadowReaderDock,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (total <= 1) {
            Text(
                text = "1 $unit",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )
            return@Surface
        }

        var dragging by remember { mutableStateOf(false) }
        var temp by remember { mutableStateOf(current.toFloat()) }
        val shown = (if (dragging) temp else current.toFloat())
            .coerceIn(0f, (total - 1).toFloat())

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = "${shown.roundToInt() + 1}",
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = shown,
                onValueChange = { dragging = true; temp = it },
                onValueChangeFinished = {
                    onSeek(temp.roundToInt().coerceIn(0, total - 1))
                    dragging = false
                },
                valueRange = 0f..(total - 1).toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$total",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Convenience: a full-screen black backdrop used behind image content. */
val ReaderBlack = Color(0xFF000000)
