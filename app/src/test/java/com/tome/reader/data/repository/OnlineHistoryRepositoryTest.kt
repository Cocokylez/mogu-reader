package com.tome.reader.data.repository

import com.tome.reader.data.local.OnlineHistoryDao
import com.tome.reader.data.local.OnlineHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class OnlineHistoryRepositoryTest {
    @Test fun concurrentResumeSavesAndReadMarksKeepBothFields() = runTest {
        val dao = YieldingDao()
        val repository = OnlineHistoryRepository(dao)
        repeat(20) { index ->
            launch {
                repository.save("mangadex", "series", "Title", null, null, "MANGA", "MangaDex", 4, "Chapter 5", index + 1, index * 10)
            }
            launch {
                repository.markChaptersRead("mangadex", "series", "Title", null, null, "MANGA", "MangaDex", listOf(index), 4, "Chapter 5")
            }
        }
        testScheduler.advanceUntilIdle()
        val saved = repository.get("mangadex", "series")!!
        assertEquals((0 until 20).toSet(), saved.readChapterIndices)
        assertEquals(20, saved.scrollItem)
        assertEquals(190, saved.scrollOffset)
        assertEquals(4, saved.chapterIndex)
    }

    private class YieldingDao : OnlineHistoryDao {
        var row: OnlineHistoryEntity? = null
        override fun observeRecent(limit: Int): Flow<List<OnlineHistoryEntity>> = flowOf(listOfNotNull(row))
        override suspend fun get(scraperId: String, seriesUrl: String): OnlineHistoryEntity? {
            val snapshot = row
            yield()
            return snapshot
        }
        override suspend fun getAllOnce() = listOfNotNull(row)
        override suspend fun insert(entry: OnlineHistoryEntity): Long {
            yield()
            if (row != null) return -1
            row = entry.copy(id = 1)
            return 1
        }
        override suspend fun update(entry: OnlineHistoryEntity) { yield(); row = entry }
        override suspend fun delete(id: Long) { row = null }
    }
}
