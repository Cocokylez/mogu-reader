package com.tome.reader.data.source

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Derives one representative accent colour from a cover image, used to tint the
 * library card, series header and reader chrome (a Google Play Books-style touch
 * where each book carries the colour of its cover).
 *
 * Deliberately dependency-free: it downscales the bitmap and picks the average of
 * its most saturated, mid-brightness pixels so the result is vivid rather than a
 * muddy overall average. Returns an ARGB [Int], or null if no usable colour found.
 */
object CoverColor {

    fun fromFile(file: File): Int? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeFile(file.absolutePath, opts)?.let { extract(it).also { _ -> it.recycle() } }
    }.getOrNull()

    fun fromBytes(bytes: ByteArray): Int? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?.let { extract(it).also { _ -> it.recycle() } }
    }.getOrNull()

    private fun extract(source: Bitmap): Int? {
        val sample = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
        var rWeighted = 0.0
        var gWeighted = 0.0
        var bWeighted = 0.0
        var weightTotal = 0.0
        // Fallbacks if nothing vivid qualifies.
        var rPlain = 0.0
        var gPlain = 0.0
        var bPlain = 0.0
        var plainCount = 0

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val px = sample.getPixel(x, y)
                val a = (px ushr 24) and 0xFF
                if (a < 128) continue
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF

                rPlain += r; gPlain += g; bPlain += b; plainCount++

                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val saturation = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC
                // Skip washed-out extremes; weight by how colourful + mid-tone it is.
                if (maxC < 30 || minC > 235) continue
                val weight = (saturation * saturation).toDouble()
                rWeighted += r * weight
                gWeighted += g * weight
                bWeighted += b * weight
                weightTotal += weight
            }
        }
        sample.recycle()

        return when {
            weightTotal > 0.0 -> rgb(
                (rWeighted / weightTotal).toInt(),
                (gWeighted / weightTotal).toInt(),
                (bWeighted / weightTotal).toInt(),
            )
            plainCount > 0 -> rgb(
                (rPlain / plainCount).toInt(),
                (gPlain / plainCount).toInt(),
                (bPlain / plainCount).toInt(),
            )
            else -> null
        }
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private const val SIZE = 24
}
