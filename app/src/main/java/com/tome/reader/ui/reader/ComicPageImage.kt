package com.tome.reader.ui.reader

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode bounds first, preserving a long strip's full aspect without allocating its original bitmap. */
@Composable
fun ComicPageImage(model: Any, onAspect: (Float) -> Unit = {}) {
    val context = LocalContext.current
    var attempt by remember(model) { mutableStateOf(0) }
    var failed by remember(model, attempt) { mutableStateOf(false) }
    var decoded by remember(model, attempt) { mutableStateOf(false) }
    val dimensions by produceState<Pair<Int, Int>?>(null, model) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                when (model) {
                    is File -> BitmapFactory.decodeFile(model.absolutePath, options)
                    is Uri -> context.contentResolver.openInputStream(model)?.use { BitmapFactory.decodeStream(it, null, options) }
                }
                if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val aspect = dimensions?.let { it.first.toFloat() / it.second }
        val height = with(LocalDensity.current) {
            (widthPx / (aspect ?: 0.7f)).coerceAtMost(30_000f).toDp()
        }
        val request = remember(model, widthPx, dimensions, attempt) {
            val size = dimensions?.let { comicDecodeSize(it.first, it.second, widthPx) } ?: (widthPx to 4096)
            ImageRequest.Builder(context).data(model).size(size.first, size.second)
                .precision(Precision.EXACT).scale(Scale.FIT).allowHardware(false).build()
        }
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { result ->
                    failed = false
                    decoded = true
                    val drawable = result.result.drawable
                    val ratio = aspect ?: drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)
                    if (ratio.isFinite() && ratio > 0f) onAspect(ratio)
                },
                onError = { failed = true },
            )
            if (failed) Text(
                "Image could not be decoded. Tap to retry.", color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.7f)).clickable { attempt++ }.padding(20.dp),
            )
            else if (!decoded) CircularProgressIndicator(color = Color.White)
        }
    }
}
