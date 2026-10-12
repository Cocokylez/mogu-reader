package com.tome.reader.data.source

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Decodes and downscales cover images so the library grid stays light on memory. */
object ImageScaling {

    /**
     * Decode [bytes] downscaled so its longest edge is roughly [maxEdge] px and
     * write it as a JPEG to [target]. Returns true on success.
     */
    fun saveScaledJpeg(
        bytes: ByteArray,
        target: File,
        maxEdge: Int = 1024,
        quality: Int = 85,
    ): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
            target.parentFile?.mkdirs()
            target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            bmp.recycle()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
