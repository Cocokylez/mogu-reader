package com.tome.reader.scraper

import java.io.File
import java.io.IOException
import java.io.InputStream

/** Streams pages to a temporary file; readers never see half-written images. */
internal object ImageDownloads {
    private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024

    fun isValid(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 16) return@runCatching false
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        isImage(header)
    }.getOrDefault(false)

    fun write(input: InputStream, destination: File, expectedLength: Long = -1): File {
        if (expectedLength > MAX_IMAGE_BYTES) throw IOException("Page image exceeds 64 MB")
        val parent = destination.absoluteFile.parentFile!!
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create download folder")
        val temporary = File.createTempFile(".page-", ".part", parent)
        try {
            val total = temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(32 * 1024)
                var count = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw IOException("Download cancelled")
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > MAX_IMAGE_BYTES) throw IOException("Page image exceeds 64 MB")
                    output.write(buffer, 0, read)
                }
                count
            }
            if (total < 16 || (expectedLength >= 0 && total != expectedLength)) {
                throw IOException("Incomplete page image; please retry")
            }
            val header = ByteArray(16)
            temporary.inputStream().use { it.read(header) }
            if (!isImage(header)) throw IOException("The source returned a non-image page")
            if (!temporary.renameTo(destination)) throw IOException("Cannot save page image")
            return destination
        } finally {
            temporary.delete()
        }
    }

    private fun isImage(bytes: ByteArray): Boolean {
        fun at(index: Int) = bytes[index].toInt() and 0xff
        val ascii = String(bytes, Charsets.ISO_8859_1)
        return (at(0) == 0xff && at(1) == 0xd8 && at(2) == 0xff) ||
            (at(0) == 0x89 && ascii.substring(1, 4) == "PNG") ||
            ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a") ||
            (ascii.startsWith("RIFF") && ascii.substring(8, 12) == "WEBP") ||
            (ascii.substring(4, 8) == "ftyp" && ascii.substring(8, 12) in listOf("avif", "avis")) ||
            ascii.startsWith("BM")
    }
}
