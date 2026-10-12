package com.tome.reader.domain.model

/** Sort options offered in the library. */
enum class SortOrder(val label: String) {
    RECENT("Recently read"),
    TITLE("Title"),
    ADDED("Date added"),
    FORMAT("Format"),
}

/** The current library view: free-text search, sort, and optional filters. */
data class LibraryQuery(
    val search: String = "",
    val sort: SortOrder = SortOrder.RECENT,
    val formatFilter: BookFormat? = null,
    val collectionId: Long? = null,
)
