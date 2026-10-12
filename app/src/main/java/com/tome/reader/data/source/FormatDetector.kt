package com.tome.reader.data.source

import android.content.Context
import android.net.Uri
import com.tome.reader.domain.model.BookFormat

/**
 * Decides which [BookFormat] a single file is. Content (magic bytes) wins over
 * the file extension, so a mislabelled ".cbz" that is really a RAR archive, or a
 * file with no extension at all, is still classified correctly.
 */
object FormatDetector {

    private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46)             // %PDF
    private val RAR_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21)             // Rar!
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)             // PK\x03\x04

    /** @return the detected format, or null if the file is not supported. */
    fun detect(context: Context, uri: Uri, displayName: String): BookFormat? {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext in setOf("epub", "txt", "text", "md", "log")) return null
        val header = readHeader(context, uri, 16)

        when {
            header.startsWith(PDF_MAGIC) -> return BookFormat.PDF
            header.startsWith(RAR_MAGIC) -> return BookFormat.CBR
            header.startsWith(ZIP_MAGIC) -> return classifyZip(context, uri, ext)
        }

        return null
    }

    /** Distinguish an EPUB from a plain image archive by peeking at entry names. */
    private fun classifyZip(context: Context, uri: Uri, ext: String): BookFormat? {
        var isEpub = false
        var hasImage = false
        runCatching {
            Zips.openArchive(context, uri).use { zip ->
                    for (e in zip.entries()) {
                        val lower = e.name.lowercase()
                        if (lower == "mimetype" ||
                            lower == "meta-inf/container.xml" ||
                            lower.endsWith(".opf")
                        ) {
                            isEpub = true
                        }
                        if (!e.isDirectory && !e.name.startsWith("__MACOSX") &&
                            !e.name.substringAfterLast('/').startsWith('.') && ImageExtensions.isImage(e.name)) hasImage = true
                    }
            }
        }
        return when {
            isEpub || !hasImage -> null
            ext == "zip" -> BookFormat.ZIP
            ext == "cbz" -> BookFormat.CBZ
            else -> BookFormat.CBZ
        }
    }

    private fun readHeader(context: Context, uri: Uri, n: Int): ByteArray {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(n)
                val read = input.read(buf)
                if (read <= 0) ByteArray(0) else buf.copyOf(read)
            } ?: ByteArray(0)
        }.getOrDefault(ByteArray(0))
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}
