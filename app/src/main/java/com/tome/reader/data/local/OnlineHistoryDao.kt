package com.tome.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineHistoryDao {

    @Query("SELECT * FROM online_history WHERE category IN ('MANGA', 'MANHWA') ORDER BY lastReadAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OnlineHistoryEntity>>

    @Query("SELECT * FROM online_history WHERE scraperId = :scraperId AND seriesUrl = :seriesUrl LIMIT 1")
    suspend fun get(scraperId: String, seriesUrl: String): OnlineHistoryEntity?

    /** All rows (used by backup export). */
    @Query("SELECT * FROM online_history")
    suspend fun getAllOnce(): List<OnlineHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: OnlineHistoryEntity): Long

    @Update
    suspend fun update(entry: OnlineHistoryEntity)

    @Query("DELETE FROM online_history WHERE id = :id")
    suspend fun delete(id: Long)
}
