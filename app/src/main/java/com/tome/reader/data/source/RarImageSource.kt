package com.tome.reader.data.source

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.tome.reader.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reads CBR / RAR image archives with junrar.
 *
 * junrar needs random access, so the archive is copied into the cache once and
 * opened as a [File]. Like the ZIP source, every image is extracted to numbered
 * cache files in natural order on first open.
 *
 * Note: junrar fully supports the classic RAR4 format used by the vast majority
 * of CBR files. RAR5 support is partial; an unreadable archive surfaces as an
 * empty book rather than a crash.
 */
class RarImageSource(
    private val context: Context,
    private val uri: Uri,
    private val storage: AppStorage,
) : ImageBookSource {

    override val format: BookFormat = BookFormat.CBR
    private val key = Keys.forUri(uri.toString())

    override suspend fun probe(): SourceProbe = withContext(Dispatchers.IO) {
        val file = ensureLocalCopy()
        var count = 0
        var cover: ByteArray? = null
        runCatching {
            Archive(file).use { archive ->
                val images = orderedImages(archive)
                count = images.size
                images.firstOrNull()?.let { header ->
                    ByteArrayOutputStream().use { bos ->
                        archive.extractFile(header, bos)
                        cover = bos.toByteArray()
                    }
                }
            }
        }
        SourceProbe(Saf.title(context, uri), null, cover, count)
    }

    override suspend fun openImages(): PageImageProvider = withContext(Dispatchers.IO) {
        val file = ensureLocalCopy()
        val dir = storage.pagesDir(key)
        var pageCount = 0
        runCatching {
            Archive(file).use { archive ->
                val images = orderedImages(archive)
                pageCount = images.size
                val marker = File(dir, MARKER)
                val done = marker.exists() &&
                    marker.readText().trim() == pageCount.toString() &&
                    images.indices.all { File(dir, it.toString()).exists() }
                if (!done) {
                    images.forEachIndexed { index, header ->
                        File(dir, index.toString()).outputStream().use { os ->
                            archive.extractFile(header, os)
                        }
                    }
                    marker.writeText(pageCount.toString())
                }
            }
        }
        FilePageProvider(pageCount, dir)
    }

    private fun orderedImages(archive: Archive): List<FileHeader> =
        archive.fileHeaders
            .filter { !it.isDirectory && isUsableImage(nameOf(it)) }
            .sortedWith(compareBy(NaturalOrder) { nameOf(it) })

    private fun nameOf(header: FileHeader): String = header.fileName ?: ""

    private fun isUsableImage(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("__MACOSX")) return false
        val base = normalized.substringAfterLast('/')
        if (base.startsWith(".")) return false
        return ImageExtensions.isImage(base)
    }

    private fun ensureLocalCopy(): File {
        val file = storage.sourceFile(key, "cbr")
        if (file.exists() && file.length() > 0) return file
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        return file
    }

    companion object {
        private const val MARKER = ".extracted"
    }
}
