package com.tome.reader.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tome.reader.data.local.BookDao
import com.tome.reader.data.local.BookEntity
import com.tome.reader.data.local.SeriesDao
import com.tome.reader.data.local.SeriesEntity
import com.tome.reader.data.source.BookSourceFactory
import com.tome.reader.data.source.ChapterOrdering
import com.tome.reader.data.source.CoverColor
import com.tome.reader.data.source.FormatDetector
import com.tome.reader.data.source.ImageBookSource
import com.tome.reader.data.source.ImageExtensions
import com.tome.reader.data.source.ImageScaling
import com.tome.reader.data.source.Keys
import com.tome.reader.data.source.NaturalOrder
import com.tome.reader.data.source.PageImageProvider
import com.tome.reader.data.source.Saf
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.domain.model.ContentKind
import com.tome.reader.domain.model.HistoryEntry
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.domain.model.ReadingProgress
import com.tome.reader.domain.model.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Entry point for the library: importing files/folders (grouping folders into
 * series), observing books + series, opening a book, navigating chapters, and
 * persisting progress. File work runs on background dispatchers in the sources;
 * this class only orchestrates.
 */
class LibraryRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val seriesDao: SeriesDao,
    private val factory: BookSourceFactory,
) {
    private val storage = factory.storage

    // --- Observation -------------------------------------------------------

    fun observeStandaloneBooks(): Flow<List<Book>> =
        bookDao.observeStandalone().map { list -> list.filter { BookFormat.isSupported(it.format) }.map { it.toDomain() } }

    fun observeSeries(): Flow<List<Series>> =
        seriesDao.observeSeries().map { list ->
            list.filter { it.chapterCount > 0 }.map { it.toDomain().copy(kind = ContentKind.IMAGES) }
        }

    fun observeSeriesDetail(id: Long): Flow<Series?> =
        seriesDao.observeSeriesById(id).map { it?.takeIf { row -> row.chapterCount > 0 }?.toDomain()?.copy(kind = ContentKind.IMAGES) }

    fun observeChapters(seriesId: Long): Flow<List<Book>> =
        bookDao.observeChapters(seriesId).map { list -> list.filter { BookFormat.isSupported(it.format) }.map { it.toDomain() } }

    fun observeBook(id: Long): Flow<Book?> = bookDao.observeById(id).map { it?.takeIf { row -> BookFormat.isSupported(row.format) }?.toDomain() }

    /** Recently read books and chapters, newest first, for the history screen. */
    fun observeHistory(limit: Int = 100): Flow<List<HistoryEntry>> =
        bookDao.observeHistory(limit).map { rows ->
            rows.map { row ->
                HistoryEntry(
                    bookId = row.id,
                    title = row.title,
                    subtitle = row.seriesTitle,
                    coverPath = row.bookCover ?: row.seriesCover,
                    accentColor = row.accentColor,
                    lastReadAt = row.lastReadAt,
                    percent = row.progressPercent,
                )
            }
        }

    suspend fun getBook(id: Long): Book? = bookDao.getById(id)?.takeIf { BookFormat.isSupported(it.format) }?.toDomain()

    suspend fun getChapters(seriesId: Long): List<Book> =
        bookDao.getChapters(seriesId).filter { BookFormat.isSupported(it.format) }.map { it.toDomain() }

    /** The chapter [delta] positions away from [book] within its series, or null. */
    suspend fun adjacentChapter(book: Book, delta: Int): Book? {
        val seriesId = book.seriesId ?: return null
        val chapters = bookDao.getChapters(seriesId)
            .filter { BookFormat.isSupported(it.format) }
            .sortedWith(
                compareBy<BookEntity> { ChapterOrdering.numberFromTitle(it.title) ?: Double.MAX_VALUE }
                    .thenBy(NaturalOrder) { it.title }
                    .thenBy { it.seriesOrder },
            )
        val index = chapters.indexOfFirst { it.id == book.id }
        if (index < 0) return null
        return chapters.getOrNull(index + delta)?.toDomain()
    }

    // --- Import: single file ----------------------------------------------

    suspend fun importDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val name = Saf.displayName(context, uri) ?: "Untitled"
        val format = FormatDetector.detect(context, uri, name) ?: return@withContext false
        val imported = importBook(uri, isTree = false, format, seriesId = null, order = 0, titleOverride = null)
        if (imported) persistPermission(uri)
        imported
    }

    // --- Import: folder (may become a series) ------------------------------

    private data class ChapterRef(
        val uri: Uri,
        val isTree: Boolean,
        val name: String,
        val format: BookFormat,
    )

    /**
     * Import a picked folder. A folder containing 2+ comic archives/PDFs
     * or image subfolders becomes a single **series** whose chapters are those
     * entries. Otherwise it falls back to a standalone book. Returns books added.
     */
    suspend fun importTree(treeUri: Uri): Int {
        persistPermission(treeUri)
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        val children = tree.listFiles()

        val chapters = ArrayList<ChapterRef>()
        for (child in children) {
            when {
                child.isFile -> {
                    val name = child.name ?: continue
                    if (ImageExtensions.isImage(name)) continue
                    val format = FormatDetector.detect(context, child.uri, name) ?: continue
                    chapters += ChapterRef(child.uri, false, name, format)
                }
                child.isDirectory -> {
                    val hasImages = child.listFiles().any {
                        it.isFile && ImageExtensions.isImage(it.name ?: "")
                    }
                    if (hasImages) {
                        chapters += ChapterRef(
                            child.uri, true, child.name ?: "Chapter", BookFormat.IMAGE_FOLDER,
                        )
                    }
                }
            }
        }

        if (chapters.size >= 2) {
            chapters.sortWith(compareBy(NaturalOrder) { it.name })
            return importAsSeries(treeUri, Saf.folderName(context, treeUri), chapters)
        }

        // Single chapter -> standalone book.
        if (chapters.size == 1) {
            val c = chapters.first()
            return if (importBook(c.uri, c.isTree, c.format, null, 0, null)) 1 else 0
        }
        // Loose images directly in the folder -> one image book.
        if (children.any { it.isFile && ImageExtensions.isImage(it.name ?: "") }) {
            return if (importBook(treeUri, true, BookFormat.IMAGE_FOLDER, null, 0, null)) 1 else 0
        }
        return 0
    }

    private suspend fun importAsSeries(
        sourceUri: Uri,
        title: String,
        chapters: List<ChapterRef>,
    ): Int {
        val existing = seriesDao.getBySourceUri(sourceUri.toString())
        val kind = chapters.first().format.kind

        val seriesId: Long
        if (existing == null) {
            val cover = extractCover(chapters.first(), keyPrefix = "series:")
            val newId = seriesDao.insert(
                SeriesEntity(
                    title = title.ifBlank { "Series" },
                    coverPath = cover?.path,
                    kind = kind.name,
                    sourceUri = sourceUri.toString(),
                    createdAt = System.currentTimeMillis(),
                    accentColor = cover?.accent,
                )
            )
            seriesId = if (newId > 0) newId else (seriesDao.getBySourceUri(sourceUri.toString())?.id ?: return 0)
        } else {
            seriesId = existing.id
            if (existing.coverPath == null) {
                extractCover(chapters.first(), keyPrefix = "series:")?.let {
                    seriesDao.updateMeta(seriesId, existing.title, it.path)
                    it.accent?.let { color -> seriesDao.updateAccent(seriesId, color) }
                }
            }
        }

        var added = 0
        chapters.forEachIndexed { index, ch ->
            if (bookDao.getByUri(ch.uri.toString()) != null) return@forEachIndexed
            val entity = BookEntity(
                uri = ch.uri.toString(),
                isTreeUri = ch.isTree,
                title = chapterTitle(ch),
                author = null,
                format = ch.format.name,
                coverPath = null,        // chapter rows are textual; no per-chapter cover
                entryCount = 0,          // resolved lazily on first open
                addedAt = System.currentTimeMillis(),
                lastReadAt = null,
                seriesId = seriesId,
                seriesOrder = index,
            )
            if (bookDao.insert(entity) > 0) added++
        }

        renumberSeries(seriesId)
        return added
    }

    /** Keep chapters ordered naturally by title after an import/refresh. */
    private suspend fun renumberSeries(seriesId: Long) {
        val ordered = bookDao.getChapters(seriesId).sortedWith(
            compareBy<BookEntity> { ChapterOrdering.numberFromTitle(it.title) ?: Double.MAX_VALUE }
                .thenBy(NaturalOrder) { it.title }
        )
        ordered.forEachIndexed { index, ch ->
            if (ch.seriesOrder != index) bookDao.updateSeriesOrder(ch.id, index)
        }
    }

    private fun chapterTitle(ch: ChapterRef): String =
        if (ch.isTree) ch.name else ch.name.substringBeforeLast('.', ch.name)

    // --- Shared import helper ---------------------------------------------

    private suspend fun importBook(
        uri: Uri,
        isTree: Boolean,
        format: BookFormat,
        seriesId: Long?,
        order: Int,
        titleOverride: String?,
    ): Boolean {
        if (!format.isImages) return false
        if (bookDao.getByUri(uri.toString()) != null) return false
        val source = factory.create(uri, format)
        val probe = runCatching { source.probe() }.getOrNull() ?: return false
        if (probe.entryCount <= 0) return false

        var coverPath: String? = null
        var accent: Int? = null
        probe.coverBytes?.let { bytes ->
            val coverFile = storage.coverFile(Keys.forUri(uri.toString()))
            if (ImageScaling.saveScaledJpeg(bytes, coverFile)) {
                coverPath = coverFile.absolutePath
                accent = CoverColor.fromBytes(bytes)
            }
        }

        val entity = BookEntity(
            uri = uri.toString(),
            isTreeUri = isTree,
            title = (titleOverride ?: probe.title).ifBlank { "Untitled" },
            author = probe.author,
            format = format.name,
            coverPath = coverPath,
            entryCount = probe.entryCount,
            addedAt = System.currentTimeMillis(),
            lastReadAt = null,
            seriesId = seriesId,
            seriesOrder = order,
            accentColor = accent,
        )
        return bookDao.insert(entity) > 0
    }

    /** A generated cover: its file path plus the accent colour pulled from it. */
    private data class CoverResult(val path: String, val accent: Int?)

    private suspend fun extractCover(ch: ChapterRef, keyPrefix: String): CoverResult? {
        val source = factory.create(ch.uri, ch.format)
        val probe = runCatching { source.probe() }.getOrNull() ?: return null
        val bytes = probe.coverBytes ?: return null
        val coverFile = storage.coverFile(Keys.forUri(keyPrefix + ch.uri.toString()))
        return if (ImageScaling.saveScaledJpeg(bytes, coverFile)) {
            CoverResult(coverFile.absolutePath, CoverColor.fromBytes(bytes))
        } else {
            null
        }
    }

    // --- Delete ------------------------------------------------------------

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        bookDao.getById(book.id)?.let { bookDao.delete(it) }
        cleanupFiles(book.uri)
        releaseSingleFilePermission(book)
    }

    suspend fun deleteSeries(seriesId: Long) = withContext(Dispatchers.IO) {
        val chapters = bookDao.getChapters(seriesId)
        chapters.forEach { cleanupFiles(it.uri) }
        bookDao.deleteChapters(seriesId)
        seriesDao.getById(seriesId)?.let { series ->
            series.coverPath?.let { path ->
                runCatching {
                    val target = File(path).canonicalFile
                    val coverRoot = File(context.filesDir, "covers").canonicalFile
                    if (target.path.startsWith(coverRoot.path + File.separator)) target.delete()
                }
            }
            seriesDao.delete(seriesId)
        }
    }

    /**
     * Delete a single downloaded chapter (its DB row + cached files). Used by the
     * downloads manager to prune individual chapters without dropping the series.
     */
    suspend fun deleteChapter(bookId: Long) = withContext(Dispatchers.IO) {
        val entity = bookDao.getById(bookId) ?: return@withContext
        bookDao.delete(entity)
        cleanupFiles(entity.uri)
    }

    private fun cleanupFiles(bookUri: String) {
        storage.deleteFor(Keys.forUri(bookUri))
        runCatching {
            val uri = Uri.parse(bookUri)
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val target = File(path).canonicalFile
                    val scraperRoot = File(context.filesDir, "scraper").canonicalFile
                    val downloadRoot = File(context.filesDir, "downloads").canonicalFile
                    // Never delete external originals just because their path
                    // happens to contain a folder named downloads or scraper.
                    if (target.path.startsWith(scraperRoot.path + File.separator)) target.deleteRecursively()
                    if (target.path.startsWith(downloadRoot.path + File.separator)) target.delete()
                }
            }
        }
    }

    private fun releaseSingleFilePermission(book: Book) {
        if (book.isTreeUri) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(book.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    // --- Reading -----------------------------------------------------------

    suspend fun openImages(book: Book): PageImageProvider {
        check(book.format.isImages) { "Only manga and manhwa image formats are supported" }
        val source = factory.create(Uri.parse(book.uri), book.format)
        check(source is ImageBookSource) { "${book.format} is not an image source" }
        return source.openImages()
    }

    // --- Progress & per-book overrides ------------------------------------

    suspend fun saveProgress(bookId: Long, progress: ReadingProgress) {
        bookDao.updateProgress(
            id = bookId,
            primary = progress.primary,
            secondary = progress.secondary,
            percent = progress.percent,
            timestamp = System.currentTimeMillis(),
        )
    }

    suspend fun markRead(bookId: Long) {
        bookDao.markRead(bookId, System.currentTimeMillis())
    }

    suspend fun markUnread(bookId: Long) {
        bookDao.markUnread(bookId)
    }

    suspend fun setDirectionOverride(bookId: Long, direction: ReadingDirection?) {
        bookDao.updateDirectionOverride(bookId, direction?.name)
    }

    // --- helpers -----------------------------------------------------------

    private fun persistPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
