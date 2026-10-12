package com.tome.reader.data.repository

import com.tome.reader.data.local.BookCollectionCrossRef
import com.tome.reader.data.local.CollectionDao
import com.tome.reader.data.local.CollectionEntity
import com.tome.reader.domain.model.Collection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CollectionRepository(private val collectionDao: CollectionDao) {

    fun observeCollections(): Flow<List<Collection>> =
        collectionDao.observeCollections().map { rows ->
            rows.map { Collection(it.collection.id, it.collection.name, it.bookCount) }
        }

    fun observeAllLinks(): Flow<List<BookCollectionCrossRef>> = collectionDao.observeAllLinks()

    fun observeCollectionIdsForBook(bookId: Long): Flow<List<Long>> =
        collectionDao.observeCollectionIdsForBook(bookId)

    suspend fun create(name: String): Long =
        collectionDao.insert(CollectionEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = collectionDao.rename(id, name.trim())

    suspend fun delete(id: Long) = collectionDao.delete(id)

    suspend fun addBook(bookId: Long, collectionId: Long) =
        collectionDao.addLink(BookCollectionCrossRef(bookId, collectionId))

    suspend fun removeBook(bookId: Long, collectionId: Long) =
        collectionDao.removeLink(bookId, collectionId)
}
