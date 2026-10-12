package com.tome.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query(
        "SELECT *, (SELECT COUNT(*) FROM book_collection WHERE collectionId = collections.id) " +
            "AS bookCount FROM collections ORDER BY name COLLATE NOCASE"
    )
    fun observeCollections(): Flow<List<CollectionWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLink(ref: BookCollectionCrossRef)

    @Query("DELETE FROM book_collection WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeLink(bookId: Long, collectionId: Long)

    @Query("SELECT * FROM book_collection")
    fun observeAllLinks(): Flow<List<BookCollectionCrossRef>>

    /** Snapshots used by backup export. */
    @Query("SELECT * FROM collections")
    suspend fun getAllCollectionsOnce(): List<CollectionEntity>

    @Query("SELECT * FROM book_collection")
    suspend fun getAllLinksOnce(): List<BookCollectionCrossRef>

    @Query("SELECT collectionId FROM book_collection WHERE bookId = :bookId")
    fun observeCollectionIdsForBook(bookId: Long): Flow<List<Long>>
}
