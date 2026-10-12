package com.tome.reader.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A library item. Enums are stored as their [Enum.name] strings so the schema
 * stays readable and resilient. Progress is three generic columns whose meaning
 * depends on the reader (see [com.tome.reader.domain.model.ReadingProgress]).
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["seriesId"])],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val isTreeUri: Boolean,
    val title: String,
    val author: String?,
    val format: String,
    val coverPath: String?,
    val entryCount: Int,
    val addedAt: Long,
    val lastReadAt: Long?,
    val progressPrimary: Int = 0,
    val progressSecondary: Int = 0,
    val progressPercent: Float = 0f,
    val directionOverride: String? = null,
    val textLayoutOverride: String? = null,
    // Series link: null = standalone book; otherwise this row is a chapter.
    val seriesId: Long? = null,
    @ColumnInfo(defaultValue = "0") val seriesOrder: Int = 0,
    // Accent colour derived from the cover (ARGB), used to tint the UI. Null = none.
    val accentColor: Int? = null,
)

/**
 * A series groups multiple chapter files (manga volumes/chapters, or novel
 * volumes) under one library entry. [sourceUri] is the imported folder's tree
 * uri and is unique, so re-importing the folder refreshes the same series.
 */
@Entity(
    tableName = "series",
    indices = [Index(value = ["sourceUri"], unique = true)],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val coverPath: String?,
    val kind: String,
    val sourceUri: String,
    val createdAt: Long,
    // Accent colour derived from the series cover (ARGB). Null = none.
    val accentColor: Int? = null,
)

/** Projection: a series plus aggregate reading info computed from its chapters. */
data class SeriesWithProgress(
    @Embedded val series: SeriesEntity,
    val chapterCount: Int,
    val readCount: Int,
    val lastChapterReadAt: Long?,
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** Many-to-many link between books and collections. */
@Entity(
    tableName = "book_collection",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId")],
)
data class BookCollectionCrossRef(
    val bookId: Long,
    val collectionId: Long,
)

/** Projection: a collection together with how many books it holds. */
data class CollectionWithCount(
    @Embedded val collection: CollectionEntity,
    val bookCount: Int,
)

/**
 * Reading position for *online* (streamed, not downloaded) content, so it shows up
 * in History and resumes at the right chapter and scroll spot. One row per
 * (source, series); updated as the reader scrolls.
 */
@Entity(
    tableName = "online_history",
    indices = [Index(value = ["scraperId", "seriesUrl"], unique = true)],
)
data class OnlineHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scraperId: String,
    val seriesUrl: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val category: String,
    val sourceName: String,
    val chapterIndex: Int,
    val chapterTitle: String?,
    // Position within the chapter: the LazyColumn item index and pixel offset.
    val scrollItem: Int,
    val scrollOffset: Int,
    val readChapterIndices: String = "",
    val lastReadAt: Long,
)
