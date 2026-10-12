package com.tome.reader.ui.reader

import org.junit.Assert.*
import org.junit.Test

class ComicDecodeSizeTest {
    @Test fun normalPageUsesDisplayWidthWithoutUpscaling() {
        assertEquals(1080 to 1620, comicDecodeSize(2000, 3000, 1080))
        assertEquals(600 to 900, comicDecodeSize(600, 900, 1080))
    }
    @Test fun veryTallManhwaStaysWithinDecodeBudget() {
        val (width, height) = comicDecodeSize(4000, 120000, 1440)
        assertTrue(width > 0 && height > 0)
        assertTrue(width.toLong() * height <= 4_000_000)
        assertTrue(height <= 8192)
        assertEquals(4000.0 / 120000, width.toDouble() / height, 0.001)
    }
    @Test fun invalidAndHugeDimensionsNeverOverflow() {
        assertEquals(1 to 4096, comicDecodeSize(0, 0, 0))
        val (width, height) = comicDecodeSize(Int.MAX_VALUE, Int.MAX_VALUE, 1080)
        assertTrue(width.toLong() * height <= 4_000_000)
        assertEquals(width, height)
    }
}
