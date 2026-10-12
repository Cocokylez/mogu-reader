package com.tome.reader.data.repository

import com.tome.reader.data.local.OnlineHistoryDao
import com.tome.reader.data.local.OnlineHistoryEntity
import com.tome.reader.domain.model.OnlineHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stores and resumes reading positions for online (streamed) content. */
class OnlineHistoryRepository(private val dao: OnlineHistoryDao) {
    // Resume writes and read marks both merge the same row. Serialize to prevent lost fields.
    private val writes = Mutex()

    fun observeRecent(limit: Int = 100): Flow<List<OnlineHistoryEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(scraperId: String, seriesUrl: String): OnlineHistoryEntry? =
        dao.get(scraperId, seriesUrl)?.toDomain()

    suspend fun getReadChapterIndices(scraperId: String, seriesUrl: String): Set<Int> =
        dao.get(scraperId, seriesUrl)?.readChapterIndices.toIndexSet()

    /**
     * Insert or update the position for one series. Keeps the existing row id (and
     * therefore a stable history key) when one already exists.
     */
    suspend fun save(
        scraperId: String,
        seriesUrl: String,
        title: String,
        author: String?,
        coverUrl: String?,
        category: String,
        sourceName: String,
        chapterIndex: Int,
        chapterTitle: String?,
        scrollItem: Int,
        scrollOffset: Int,
    ) = writes.withLock {
        val existing = dao.get(scraperId, seriesUrl)
        val row = OnlineHistoryEntity(
            id = existing?.id ?: 0,
            scraperId = scraperId,
            seriesUrl = seriesUrl,
            title = title,
            author = author,
            coverUrl = coverUrl,
            category = category,
            sourceName = sourceName,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            scrollItem = scrollItem.coerceAtLeast(0),
            scrollOffset = scrollOffset.coerceAtLeast(0),
            readChapterIndices = existing?.readChapterIndices ?: "",
            lastReadAt = System.currentTimeMillis(),
        )
        if (existing == null) dao.insert(row) else dao.update(row)
    }

    /**
     * Mark several chapters read in one write. Used when reaching a later chapter
     * implies every earlier (lower-numbered) chapter is read too. Unions into the
     * existing read set and leaves the saved resume position untouched.
     */
    suspend fun markChaptersRead(
        scraperId: String,
        seriesUrl: String,
        title: String,
        author: String?,
        coverUrl: String?,
        category: String,
        sourceName: String,
        chapterIndices: Collection<Int>,
        latestChapterIndex: Int,
        latestChapterTitle: String?,
    ) = writes.withLock {
        val additions = chapterIndices.filter { it >= 0 }
        if (additions.isEmpty()) return@withLock
        val existing = dao.get(scraperId, seriesUrl)
        val read = existing?.readChapterIndices.toIndexSet() + additions
        val row = OnlineHistoryEntity(
            id = existing?.id ?: 0,
            scraperId = scraperId,
            seriesUrl = seriesUrl,
            title = existing?.title ?: title,
            author = existing?.author ?: author,
            coverUrl = existing?.coverUrl ?: coverUrl,
            category = existing?.category ?: category,
            sourceName = existing?.sourceName ?: sourceName,
            chapterIndex = existing?.chapterIndex ?: latestChapterIndex,
            chapterTitle = existing?.chapterTitle ?: latestChapterTitle,
            scrollItem = existing?.scrollItem ?: 0,
            scrollOffset = existing?.scrollOffset ?: 0,
            readChapterIndices = read.toIndexString(),
            lastReadAt = System.currentTimeMillis(),
        )
        if (existing == null) dao.insert(row) else dao.update(row)
    }

    private fun OnlineHistoryEntity.toDomain() = OnlineHistoryEntry(
        scraperId = scraperId,
        seriesUrl = seriesUrl,
        title = title,
        author = author,
        coverUrl = coverUrl,
        category = category,
        sourceName = sourceName,
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        scrollItem = scrollItem.coerceAtLeast(0),
        scrollOffset = scrollOffset.coerceAtLeast(0),
        readChapterIndices = readChapterIndices.toIndexSet(),
        lastReadAt = lastReadAt,
    )

    private fun String?.toIndexSet(): Set<Int> =
        this.orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 0 }
            .toSet()

    private fun Set<Int>.toIndexString(): String =
        sorted().joinToString(",")
}
