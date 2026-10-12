package com.tome.reader.ui.reader

import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Bound each visible bitmap to 16 MB and 8192 px, including unusually tall manhwa strips. */
internal fun comicDecodeSize(width: Int, height: Int, displayWidth: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return displayWidth.coerceIn(1, 1536) to 4096
    val scale = minOf(
        1.0,
        displayWidth.coerceIn(1, 1536).toDouble() / width,
        8192.0 / height,
        sqrt(4_000_000.0 / (width.toDouble() * height)),
    )
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}
