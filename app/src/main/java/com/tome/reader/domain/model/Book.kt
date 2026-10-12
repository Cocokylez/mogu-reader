package com.tome.reader.domain.model

/**
 * A single item in the library. This is the domain model used by the UI; it is
 * mapped to/from [com.tome.reader.data.local.BookEntity] in the data layer.
 *
 * @param uri           the SAF document or tree uri the book was imported from.
 * @param isTreeUri     true when [uri] is a tree uri (an image folder).
 * @param coverPath     absolute path to a cached cover image file, or null.
 * @param entryCount    number of pages (image/pdf) or chapters (text).
 */
data class Book(
    val id: Long,
    val uri: String,
    val isTreeUri: Boolean,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val coverPath: String?,
    val entryCount: Int,
    val addedAt: Long,
    val lastReadAt: Long?,
    val progress: ReadingProgress,
    val directionOverride: ReadingDirection?,
    val textLayoutOverride: TextLayoutMode?,
    val seriesId: Long? = null,
    val seriesOrder: Int = 0,
    val accentColor: Int? = null,
)

/**
 * A resumable reading location, kept deliberately generic so one shape works for
 * every reader:
 *  - image paged/pdf : [primary] = page index.
 *  - image webtoon    : [primary] = first visible item, [secondary] = pixel offset.
 *  - text paged       : [primary] = chapter index, [secondary] = page within chapter.
 *  - text scroll      : [primary] = chapter index, [secondary] = scroll offset (px).
 * [percent] is only used for the progress label in the library (0f..1f).
 */
data class ReadingProgress(
    val primary: Int = 0,
    val secondary: Int = 0,
    val percent: Float = 0f,
)
