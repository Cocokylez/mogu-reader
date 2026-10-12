package com.tome.reader.domain.model

import com.tome.reader.scraper.BookmarkedSeries

/**
 * A series groups multiple chapter files (manga chapters/volumes, or novel
 * volumes) under one library entry. Reading progress is per chapter (each chapter
 * is a [Book]); the aggregate counts here drive the library/series UI.
 */
data class Series(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val kind: ContentKind,
    val chapterCount: Int,
    val readCount: Int,
    val lastReadAt: Long?,
    val createdAt: Long,
    val accentColor: Int? = null,
) {
    val unreadCount: Int get() = (chapterCount - readCount).coerceAtLeast(0)
    val progress: Float get() = if (chapterCount > 0) readCount.toFloat() / chapterCount else 0f
}

/**
 * One row in the library: either a standalone [Book] or a [Series]. Common fields
 * are exposed so the grid and sorter can treat both uniformly.
 */
sealed interface LibraryItem {
    val key: String
    val title: String
    val coverPath: String?
    val addedAt: Long
    val lastReadAt: Long?
    val accentColor: Int?

    data class BookItem(val book: Book) : LibraryItem {
        override val key get() = "book-${book.id}"
        override val title get() = book.title
        override val coverPath get() = book.coverPath
        override val addedAt get() = book.addedAt
        override val lastReadAt get() = book.lastReadAt
        override val accentColor get() = book.accentColor
    }

    data class SeriesItem(val series: Series) : LibraryItem {
        override val key get() = "series-${series.id}"
        override val title get() = series.title
        override val coverPath get() = series.coverPath
        override val addedAt get() = series.createdAt
        override val lastReadAt get() = series.lastReadAt
        override val accentColor get() = series.accentColor
    }

    /**
     * A followed online series.
     *
     * Follows used to live in their own strip above the library, which split one
     * collection across two places. As a LibraryItem they sit in the same grid,
     * shelves, list or card view as imported comics, so everything the reader
     * keeps is in one list. Its cover is a remote URL, hence [coverPath] is null
     * and the URL is surfaced separately for the image loader.
     */
    data class FollowItem(val follow: BookmarkedSeries) : LibraryItem {
        override val key get() = "follow-${follow.scraperId}:${follow.url}"
        override val title get() = follow.title
        override val coverPath: String? get() = null
        override val addedAt get() = follow.lastOpenedAt
        override val lastReadAt get() = follow.lastOpenedAt
        override val accentColor: Int? get() = null
    }
}
