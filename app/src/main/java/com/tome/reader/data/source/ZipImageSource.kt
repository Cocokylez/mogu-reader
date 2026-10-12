package com.tome.reader.data.source

import android.content.Context
import android.net.Uri
import com.tome.reader.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads CBZ / ZIP image archives with java.util.zip.
 *
 * On first open every image entry is extracted, in natural page order, to a
 * per-book cache directory as numbered files. Reading then loads plain [File]s,
 * which gives Coil stable cache keys and instant random access for both paged
 * and webtoon modes. A marker file lets subsequent opens skip extraction.
 */
class ZipImageSource(
    private val context: Context,
    private val uri: Uri,
    override val format: BookFormat,
    private val storage: AppStorage,
) : ImageBookSource {

    private val key = Keys.forUri(uri.toString())

    override suspend fun probe(): SourceProbe = withContext(Dispatchers.IO) {
        val names = listImageEntries()
        val coverName = names.firstOrNull()
        val cover = coverName?.let { Zips.readEntry(context, uri, it) }
        SourceProbe(
            title = Saf.title(context, uri),
            author = null,
            coverBytes = cover,
            entryCount = names.size,
        )
    }

    override suspend fun openImages(): PageImageProvider = withContext(Dispatchers.IO) {
        val ordered = listImageEntries()
        val dir = storage.pagesDir(key)
        ensureExtracted(ordered, dir)
        FilePageProvider(ordered.size, dir)
    }

    private fun listImageEntries(): List<String> {
        val out = ArrayList<String>()
        Zips.openArchive(context, uri).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.isDirectory && isUsableImage(entry.name)) out.add(entry.name)
            }
        }
        out.sortWith(NaturalOrder)
        return out
    }

    private fun ensureExtracted(ordered: List<String>, dir: File) {
        val marker = File(dir, MARKER)
        val alreadyDone = marker.exists() &&
            marker.readText().trim() == ordered.size.toString() &&
            ordered.indices.all { File(dir, it.toString()).length() > 0 }
        if (alreadyDone) return

        marker.delete()
        Zips.openArchive(context, uri).use { zip ->
            ordered.forEachIndexed { index, name ->
                val entry = zip.getEntry(name) ?: error("Missing comic page ${index + 1}")
                val target = File(dir, index.toString())
                val partial = File.createTempFile("page-", ".part", dir)
                try {
                    zip.getInputStream(entry).use { input ->
                        partial.outputStream().use { input.copyTo(it, 64 * 1024) }
                    }
                    check(partial.length() > 0 && (entry.size < 0 || partial.length() == entry.size)) {
                        "Incomplete comic page ${index + 1}"
                    }
                    check(partial.renameTo(target)) { "Couldn't save comic page ${index + 1}" }
                } finally {
                    partial.delete()
                }
            }
        }
        check(ordered.isNotEmpty()) { "No comic pages found in this archive" }
        marker.writeText(ordered.size.toString())
    }

    private fun isUsableImage(name: String): Boolean {
        if (name.startsWith("__MACOSX")) return false
        val base = name.substringAfterLast('/')
        if (base.startsWith(".")) return false
        return ImageExtensions.isImage(base)
    }

    companion object {
        private const val MARKER = ".extracted"
    }
}
