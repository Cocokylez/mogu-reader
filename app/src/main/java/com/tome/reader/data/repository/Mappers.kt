package com.tome.reader.data.repository

import com.tome.reader.data.local.BookEntity
import com.tome.reader.data.local.SeriesWithProgress
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.domain.model.ContentKind
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.domain.model.ReadingProgress
import com.tome.reader.domain.model.Series
import com.tome.reader.domain.model.TextLayoutMode

/** Translate a stored row into the domain model used by the UI. */
fun BookEntity.toDomain(): Book = Book(
    id = id,
    uri = uri,
    isTreeUri = isTreeUri,
    title = title,
    author = author,
    format = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.ZIP),
    coverPath = coverPath,
    entryCount = entryCount,
    addedAt = addedAt,
    lastReadAt = lastReadAt,
    progress = ReadingProgress(progressPrimary, progressSecondary, progressPercent),
    directionOverride = directionOverride?.let {
        runCatching { ReadingDirection.valueOf(it) }.getOrNull()
    },
    textLayoutOverride = textLayoutOverride?.let {
        runCatching { TextLayoutMode.valueOf(it) }.getOrNull()
    },
    seriesId = seriesId,
    seriesOrder = seriesOrder,
    accentColor = accentColor,
)

fun SeriesWithProgress.toDomain(): Series = Series(
    id = series.id,
    title = series.title,
    coverPath = series.coverPath,
    kind = runCatching { ContentKind.valueOf(series.kind) }.getOrDefault(ContentKind.IMAGES),
    chapterCount = chapterCount,
    readCount = readCount,
    lastReadAt = lastChapterReadAt,
    createdAt = series.createdAt,
    accentColor = series.accentColor,
)
