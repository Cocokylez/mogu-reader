package com.tome.reader.domain.model

/** Whether a book is read as a sequence of page images or as flowing text. */
enum class ContentKind { IMAGES, TEXT }

/**
 * Persisted formats. Only image formats are offered for import and reading.
 * EPUB/TXT remain here so older libraries and backups can be read without
 * deleting or misclassifying their legacy entries.
 *
 * To add a new format later you:
 *  1. add a value here,
 *  2. teach [com.tome.reader.data.source.FormatDetector] to recognise it,
 *  3. implement a [com.tome.reader.data.source.BookSource] for it,
 *  4. register that source in [com.tome.reader.data.source.BookSourceFactory].
 */
enum class BookFormat(
    val label: String,
    val kind: ContentKind,
) {
    CBZ("CBZ", ContentKind.IMAGES),
    CBR("CBR", ContentKind.IMAGES),
    ZIP("ZIP", ContentKind.IMAGES),
    IMAGE_FOLDER("Folder", ContentKind.IMAGES),
    PDF("PDF", ContentKind.IMAGES), // pages are rendered to images via PdfRenderer
    EPUB("EPUB", ContentKind.TEXT),
    TXT("TXT", ContentKind.TEXT);

    val isImages: Boolean get() = kind == ContentKind.IMAGES
    val isText: Boolean get() = kind == ContentKind.TEXT

    companion object {
        val supported: List<BookFormat> = entries.filter { it.isImages }
        fun isSupported(name: String): Boolean = supported.any { it.name == name }
    }
}
