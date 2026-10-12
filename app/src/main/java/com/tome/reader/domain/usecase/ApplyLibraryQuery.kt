package com.tome.reader.domain.usecase

import com.tome.reader.domain.model.ContentKind
import com.tome.reader.domain.model.LibraryItem
import com.tome.reader.domain.model.LibraryQuery
import com.tome.reader.domain.model.SortOrder

/**
 * Pure filtering + sorting of the library, over a mixed list of standalone books
 * and series. Side-effect free for easy testing.
 *
 * @param membership bookId -> the set of collection ids it belongs to (series are
 *                   not collection members in v1, so a collection filter hides them).
 */
class ApplyLibraryQuery {

    operator fun invoke(
        items: List<LibraryItem>,
        query: LibraryQuery,
        membership: Map<Long, Set<Long>>,
    ): List<LibraryItem> {
        val search = query.search.trim().lowercase()

        val filtered = items.asSequence()
            .filter { item ->
                when (item) {
                    is LibraryItem.BookItem -> item.book.format.isImages
                    is LibraryItem.SeriesItem -> item.series.kind == ContentKind.IMAGES
                    // A followed manga/manhwa is image content by definition.
                    is LibraryItem.FollowItem -> true
                }
            }
            .filter { item ->
                search.isEmpty() ||
                    item.title.lowercase().contains(search) ||
                    authorContains(item, search)
            }
            .filter { item -> matchesFormat(item, query.formatFilter) }
            .filter { item -> matchesCollection(item, query.collectionId, membership) }
            .toList()

        val comparator: Comparator<LibraryItem> = when (query.sort) {
            SortOrder.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortOrder.ADDED -> compareByDescending { it.addedAt }
            SortOrder.FORMAT -> compareBy<LibraryItem> { formatKey(it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortOrder.RECENT -> compareByDescending<LibraryItem> { it.lastReadAt ?: Long.MIN_VALUE }
                .thenByDescending { it.addedAt }
        }
        return filtered.sortedWith(comparator)
    }

    private fun authorContains(item: LibraryItem, search: String): Boolean = when (item) {
        is LibraryItem.BookItem -> item.book.author?.lowercase()?.contains(search) == true
        is LibraryItem.FollowItem -> item.follow.author?.lowercase()?.contains(search) == true
        else -> false
    }

    private fun matchesFormat(item: LibraryItem, filter: com.tome.reader.domain.model.BookFormat?): Boolean {
        if (filter == null) return true
        return when (item) {
            is LibraryItem.BookItem -> item.book.format == filter
            is LibraryItem.SeriesItem -> item.series.kind == filter.kind
            // Follows have no local file format, so a format filter excludes them.
            is LibraryItem.FollowItem -> false
        }
    }

    private fun matchesCollection(
        item: LibraryItem,
        collectionId: Long?,
        membership: Map<Long, Set<Long>>,
    ): Boolean {
        if (collectionId == null) return true
        return item is LibraryItem.BookItem && membership[item.book.id]?.contains(collectionId) == true
    }

    private fun formatKey(item: LibraryItem): String = when (item) {
        is LibraryItem.BookItem -> item.book.format.label
        is LibraryItem.SeriesItem ->
            if (item.series.kind == ContentKind.IMAGES) "Series-images" else "Series-text"
        is LibraryItem.FollowItem -> "Online"
    }
}
