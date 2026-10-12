package com.tome.reader.scraper

import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageDownloadsTest {
    @get:Rule val folder = TemporaryFolder()
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + ByteArray(100)

    @Test fun completeImageIsPublishedWithoutTemporaryFiles() {
        val dir = folder.newFolder()
        val output = java.io.File(dir, "page.jpg")
        ImageDownloads.write(jpeg.inputStream(), output, jpeg.size.toLong())
        assertArrayEquals(jpeg, output.readBytes())
        assertEquals(1, dir.listFiles()!!.size)
        assertTrue(ImageDownloads.isValid(output))
    }

    @Test fun truncatedResponsePreservesExistingPage() {
        val output = folder.newFile("page.jpg")
        output.writeBytes(jpeg)
        assertThrows(IOException::class.java) { ImageDownloads.write(jpeg.inputStream(), output, 1000) }
        assertArrayEquals(jpeg, output.readBytes())
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test fun htmlErrorIsNeverSavedAsImage() {
        val output = java.io.File(folder.root, "page.jpg")
        assertThrows(IOException::class.java) {
            ImageDownloads.write("<!DOCTYPE html><title>Access denied</title>".byteInputStream(), output)
        }
        assertFalse(output.exists())
        assertEquals(0, folder.root.listFiles()!!.size)
    }

    @Test fun disconnectedStreamLeavesNoPartialPage() {
        val output = java.io.File(folder.root, "page.jpg")
        val broken = object : InputStream() { override fun read(): Int = throw IOException("Disconnected") }
        assertThrows(IOException::class.java) { ImageDownloads.write(broken, output) }
        assertFalse(output.exists())
        assertEquals(0, folder.root.listFiles()!!.size)
    }
}
