package com.tome.reader.domain.model

/**
 * A resumable online-reading record (streamed content, not downloaded). Carries
 * everything needed to show it in History and to reopen the streaming reader at the
 * last chapter and scroll position.
 */
data class OnlineHistoryEntry(
    val scraperId: String,
    val seriesUrl: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val category: String,
    val sourceName: String,
    val chapterIndex: Int,
    val chapterTitle: String?,
    val scrollItem: Int,
    val scrollOffset: Int,
    val readChapterIndices: Set<Int>,
    val lastReadAt: Long,
)
