package com.tome.reader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Projection for the history screen: a read row plus its series fallbacks. */
data class HistoryRow(
    val id: Long,
    val title: String,
    val bookCover: String?,
    val seriesCover: String?,
    val seriesTitle: String?,
    val accentColor: Int?,
    val lastReadAt: Long,
    val progressPercent: Float,
)

@Dao
interface BookDao {

    /** Returns the new row id, or -1 if a book with the same uri already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    /** Books that are not part of a series (shown directly in the library). */
    @Query("SELECT * FROM books WHERE seriesId IS NULL ORDER BY addedAt DESC")
    fun observeStandalone(): Flow<List<BookEntity>>

    /** Chapters of a series, in reading order. */
    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY seriesOrder")
    fun observeChapters(seriesId: Long): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY seriesOrder")
    suspend fun getChapters(seriesId: Long): List<BookEntity>

    @Query("UPDATE books SET seriesOrder = :order WHERE id = :id")
    suspend fun updateSeriesOrder(id: Long, order: Int)

    @Query("DELETE FROM books WHERE seriesId = :seriesId")
    suspend fun deleteChapters(seriesId: Long)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): BookEntity?

    /** All books, used by one-off maintenance (e.g. the download-path migration). */
    @Query("SELECT * FROM books")
    suspend fun getAllOnce(): List<BookEntity>

    /**
     * Recently read books and chapters for the history screen. Joins each row to
     * its series (if any) so chapters can borrow the series cover/title.
     */
    @Query(
        "SELECT b.id AS id, b.title AS title, b.coverPath AS bookCover, " +
            "s.coverPath AS seriesCover, s.title AS seriesTitle, " +
            "b.accentColor AS accentColor, b.lastReadAt AS lastReadAt, " +
            "b.progressPercent AS progressPercent " +
            "FROM books b LEFT JOIN series s ON b.seriesId = s.id " +
            "WHERE b.lastReadAt IS NOT NULL AND b.format IN ('CBZ', 'CBR', 'ZIP', 'IMAGE_FOLDER', 'PDF') " +
            "ORDER BY b.lastReadAt DESC LIMIT :limit"
    )
    fun observeHistory(limit: Int): Flow<List<HistoryRow>>

    @Query("UPDATE books SET uri = :uri WHERE id = :id")
    suspend fun updateUri(id: Long, uri: String)

    @Query(
        "UPDATE books SET progressPrimary = :primary, progressSecondary = :secondary, " +
            "progressPercent = :percent, lastReadAt = :timestamp WHERE id = :id"
    )
    suspend fun updateProgress(
        id: Long,
        primary: Int,
        secondary: Int,
        percent: Float,
        timestamp: Long,
    )

    /** Mark a chapter/book fully read without opening it (Kotatsu-style). */
    @Query("UPDATE books SET progressPercent = 1.0, lastReadAt = :timestamp WHERE id = :id")
    suspend fun markRead(id: Long, timestamp: Long)

    /** Reset reading progress so the item counts as unread again. */
    @Query("UPDATE books SET progressPrimary = 0, progressSecondary = 0, progressPercent = 0.0 WHERE id = :id")
    suspend fun markUnread(id: Long)

    @Query("UPDATE books SET directionOverride = :direction WHERE id = :id")
    suspend fun updateDirectionOverride(id: Long, direction: String?)

    @Query("UPDATE books SET textLayoutOverride = :layout WHERE id = :id")
    suspend fun updateTextLayoutOverride(id: Long, layout: String?)
}
