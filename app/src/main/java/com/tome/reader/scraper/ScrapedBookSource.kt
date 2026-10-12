package com.tome.reader.scraper

import android.content.Context
import android.net.Uri
import com.tome.reader.data.source.BookSource
import com.tome.reader.data.source.ImageBookSource
import com.tome.reader.data.source.PageImageProvider
import com.tome.reader.data.source.SourceProbe
import com.tome.reader.data.source.TextBookSource
import com.tome.reader.data.source.TextChapter
import com.tome.reader.data.source.TextDocument
import com.tome.reader.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ScrapedImageBookSource(
    private val context: Context,
    private val bookFormat: BookFormat,
    private val cacheDir: File,
) : ImageBookSource {

    override val format: BookFormat get() = bookFormat

    override suspend fun probe(): SourceProbe = withContext(Dispatchers.IO) {
        val files = cacheDir.listFiles()
            ?.filter { it.isFile && it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg") || n.endsWith(".webp") } }
            ?.sortedBy { it.name }
            ?: emptyList()

        val title = cacheDir.name
        val firstFile = files.firstOrNull()
        val coverBytes = firstFile?.let { runCatching { it.readBytes() }.getOrNull() }

        SourceProbe(
            title = title,
            author = null,
            coverBytes = coverBytes,
            entryCount = files.size,
        )
    }

    override suspend fun openImages(): PageImageProvider {
        return ScrapedImagePageProvider(cacheDir)
    }
}

private class ScrapedImagePageProvider(private val cacheDir: File) : PageImageProvider {
    private val files by lazy {
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg") || n.endsWith(".webp") } }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    override val pageCount: Int get() = files.size

    override suspend fun getPage(index: Int): Any? {
        if (index < 0 || index >= files.size) return null
        return files[index]
    }
}

class ScrapedTextBookSource(
    private val context: Context,
    private val bookFormat: BookFormat,
    private val textFile: File,
) : TextBookSource {

    override val format: BookFormat get() = bookFormat

    override suspend fun probe(): SourceProbe = withContext(Dispatchers.IO) {
        val title = textFile.nameWithoutExtension
        SourceProbe(
            title = title,
            author = null,
            coverBytes = null,
            entryCount = 1,
        )
    }

    override suspend fun openText(): TextDocument = withContext(Dispatchers.IO) {
        val text = textFile.readText()
        val title = textFile.nameWithoutExtension
        TextDocument(
            chapters = listOf(
                TextChapter(title = title, text = text)
            )
        )
    }
}
