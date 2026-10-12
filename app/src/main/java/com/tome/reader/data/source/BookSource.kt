package com.tome.reader.data.source

import com.tome.reader.domain.model.BookFormat
import java.io.Closeable

/**
 * Cheap metadata gathered at import time. Reading the whole book is deliberately
 * avoided here; we only pull a title, an optional author, a cover image and a
 * count of pages/chapters.
 */
data class SourceProbe(
    val title: String,
    val author: String?,
    val coverBytes: ByteArray?,
    val entryCount: Int,
)

/**
 * Common abstraction over every supported file format. A concrete source knows
 * how to read exactly one format from a SAF uri.
 *
 * Image formats additionally implement [ImageBookSource]; text formats implement
 * [TextBookSource]. Adding a new format is just a matter of implementing one of
 * these and registering it in [BookSourceFactory].
 */
interface BookSource {
    val format: BookFormat

    /** Cheap metadata + cover for the library. Call on a background thread. */
    suspend fun probe(): SourceProbe
}

/** A source whose content is a sequence of page images. */
interface ImageBookSource : BookSource {
    /** Prepare for reading. May extract/copy into the app cache the first time. */
    suspend fun openImages(): PageImageProvider
}

/** A source whose content is flowing text split into chapters. */
interface TextBookSource : BookSource {
    suspend fun openText(): TextDocument
}

/**
 * Supplies page images to the image reader on demand. [getPage] returns a model
 * Coil can load directly — a [java.io.File] for archive/PDF pages, or an
 * [android.net.Uri] for loose images in a folder.
 */
interface PageImageProvider : Closeable {
    val pageCount: Int
    suspend fun getPage(index: Int): Any?
    override fun close() {}
}

/** A parsed text book. */
data class TextDocument(val chapters: List<TextChapter>) {
    val isEmpty: Boolean get() = chapters.isEmpty()
}

sealed interface TextContentBlock {
    data class Text(val value: String) : TextContentBlock
    data class Image(val path: String, val altText: String? = null) : TextContentBlock
}

data class TextChapter(
    val title: String?,
    val text: String,
    val blocks: List<TextContentBlock> = emptyList(),
) {
    val contentBlocks: List<TextContentBlock>
        get() = blocks.ifEmpty {
            if (text.isBlank()) emptyList() else listOf(TextContentBlock.Text(text))
        }
}
