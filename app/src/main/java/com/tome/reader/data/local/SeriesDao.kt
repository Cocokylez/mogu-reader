package com.tome.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(series: SeriesEntity): Long


    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: Long): SeriesEntity?

    /** All series rows (used by backup export). */
    @Query("SELECT * FROM series")
    suspend fun getAllOnce(): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE sourceUri = :uri LIMIT 1")
    suspend fun getBySourceUri(uri: String): SeriesEntity?

    @Query("UPDATE series SET title = :title, coverPath = :coverPath WHERE id = :id")
    suspend fun updateMeta(id: Long, title: String, coverPath: String?)

    @Query("UPDATE series SET accentColor = :color WHERE id = :id")
    suspend fun updateAccent(id: Long, color: Int)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun delete(id: Long)

    // A chapter counts as "read" once progress reaches ~the end (progressPercent >= 0.95).

    @Query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF')) AS chapterCount, " +
            "(SELECT COUNT(*) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF') AND progressPercent >= 0.95) AS readCount, " +
            "(SELECT MAX(lastReadAt) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF')) AS lastChapterReadAt " +
            "FROM series s ORDER BY s.title COLLATE NOCASE"
    )
    fun observeSeries(): Flow<List<SeriesWithProgress>>

    @Query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF')) AS chapterCount, " +
            "(SELECT COUNT(*) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF') AND progressPercent >= 0.95) AS readCount, " +
            "(SELECT MAX(lastReadAt) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF')) AS lastChapterReadAt " +
            "FROM series s WHERE s.id = :id"
    )
    fun observeSeriesById(id: Long): Flow<SeriesWithProgress?>

    @Query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF')) AS chapterCount, " +
            "(SELECT COUNT(*) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF') AND progressPercent >= 0.95) AS readCount, " +
            "(SELECT MAX(lastReadAt) FROM books WHERE seriesId = s.id AND format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF')) AS lastChapterReadAt " +
            "FROM series s WHERE s.sourceUri LIKE 'scraper:%' ORDER BY s.title COLLATE NOCASE"
    )
    fun observeScraperSeries(): Flow<List<SeriesWithProgress>>
}

