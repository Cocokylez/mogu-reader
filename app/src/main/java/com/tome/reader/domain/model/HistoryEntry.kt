package com.tome.reader.domain.model

/**
 * One row in the reading-history screen: a recently read book or series chapter.
 * [subtitle] carries the series title for chapters, null for standalone books.
 */
data class HistoryEntry(
    val bookId: Long,
    val title: String,
    val subtitle: String?,
    val coverPath: String?,
    val accentColor: Int?,
    val lastReadAt: Long,
    val percent: Float,
)
