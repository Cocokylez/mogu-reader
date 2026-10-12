package com.tome.reader.scraper

import android.content.Context
import android.net.Uri
import com.tome.reader.data.local.BookDao
import com.tome.reader.data.local.BookEntity
import com.tome.reader.data.local.SeriesDao
import com.tome.reader.data.local.SeriesEntity
import com.tome.reader.data.source.CoverColor
import com.tome.reader.data.source.ImageScaling
import com.tome.reader.data.source.Keys
import com.tome.reader.data.repository.LibraryRepository
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.online.WebDownloader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ScraperDownloader(
    private val context: Context,
    private val bookDao: BookDao,
    private val seriesDao: SeriesDao,
    private val libraryRepository: LibraryRepository,
) {

    // Downloads live in filesDir (persistent) so Android can't reclaim them under
    // storage pressure the way it can with cacheDir. The path still contains
    // "/scraper/" so BookSourceFactory keeps recognising these as scraped books.
    private val baseCacheDir = File(context.filesDir, "scraper").apply { mkdirs() }

    // Older builds wrote downloads under cacheDir; migrateLegacyDownloads() moves
    // them here and rewrites the stored book uris.
    private val legacyDir = File(context.cacheDir, "scraper")
    private val downloadLock = Mutex()

    data class DownloadResult(val bookId: Long, val isNew: Boolean)

    /**
     * One-time move of pre-persistent downloads from cacheDir to filesDir. Safe to
     * call on every launch: it no-ops once the legacy folder is gone. Each affected
     * book's file uri is rewritten so the library keeps pointing at the real files.
     */
    suspend fun migrateLegacyDownloads() = withContext(Dispatchers.IO) {
        if (!legacyDir.exists()) return@withContext
        // Keep the old cache if copying fails. Never rewrite a URI to missing data.
        downloadLock.withLock {
            legacyDir.listFiles()?.forEach { child ->
                val dest = File(baseCacheDir, child.name)
                if (!dest.exists()) child.copyRecursively(dest, overwrite = false)
            }
            val cachePrefix = context.cacheDir.absolutePath
            val filesPrefix = context.filesDir.absolutePath
            val needle = "$cachePrefix/scraper/"
            bookDao.getAllOnce().filter { it.uri.contains(needle) }.forEach { book ->
                val moved = book.uri.replace(cachePrefix, filesPrefix)
                val target = File(Uri.parse(moved).path.orEmpty())
                if (target.exists()) bookDao.updateUri(book.id, moved)
            }
            // Originals remain recoverable until Android/user clears the old cache.
        }
    }

    suspend fun downloadChapter(
        scraper: Scraper,
        series: ScrapedSeries,
        chapter: ScrapedChapter,
        content: ScrapedChapterContent,
        seriesOrder: Int,
    ): DownloadResult = downloadLock.withLock { withContext(Dispatchers.IO) {
        require(series.category.isSupported) { "Mogu Reader supports manga and manhwa only" }
        val seriesKey = Keys.forUri(series.url)
        val chapterKey = Keys.forUri(chapter.url)
        val scraperDir = File(baseCacheDir, scraper.id)
        val seriesDir = File(scraperDir, seriesKey)
        val chapterDir = File(seriesDir, chapterKey)
        chapterDir.mkdirs()

        if (content is ScrapedChapterContent.FileDownload) {
            val file = WebDownloader.download(
                context = context,
                url = content.url,
                userAgent = null,
                contentDisposition = null,
                mimeType = content.mimeType,
            ).getOrThrow()
            val uri = Uri.fromFile(file)
            val existing = bookDao.getByUri(uri.toString())
            if (existing != null) return@withContext DownloadResult(existing.id, false)

            val imported = libraryRepository.importDocument(uri)
            val bookId = bookDao.getByUri(uri.toString())?.id ?: 0L
            return@withContext DownloadResult(bookId, imported)
        }

        val uri: String
        val format: BookFormat
        val isTree: Boolean
        val entryCount: Int

        when (content) {
            is ScrapedChapterContent.Images -> {
                val pageFiles = downloadImages(content.pageUrls, chapterDir, chapter.url.takeIf { it.startsWith("https://") } ?: scraper.baseUrl)
                uri = chapterDir.toURI().toString()
                format = BookFormat.IMAGE_FOLDER
                isTree = true
                entryCount = pageFiles.size
            }
            is ScrapedChapterContent.Text -> error("Text novels are no longer supported")
            is ScrapedChapterContent.FileDownload -> error("File downloads are imported before chapter caching")
        }

        val existing = bookDao.getByUri(uri)
        if (existing != null) {
            return@withContext DownloadResult(existing.id, false)
        }

        val seriesId = resolveOrCreateSeries(scraper, series, seriesKey, format)

        val coverPath = resolveCover(series, seriesKey)
        val accent = coverPath?.let { CoverColor.fromFile(File(it)) }

        val entity = BookEntity(
            uri = uri,
            isTreeUri = isTree,
            title = chapter.title.ifBlank { "Chapter ${seriesOrder + 1}" },
            author = series.author,
            format = format.name,
            coverPath = coverPath,
            entryCount = entryCount,
            addedAt = System.currentTimeMillis(),
            lastReadAt = null,
            seriesId = seriesId,
            seriesOrder = seriesOrder,
            accentColor = accent,
        )

        val newId = bookDao.insert(entity)
        check(newId > 0) { "Could not save downloaded chapter" }
        DownloadResult(newId, true)
    } }

    private suspend fun resolveOrCreateSeries(
        scraper: Scraper,
        series: ScrapedSeries,
        seriesKey: String,
        format: BookFormat,
    ): Long {
        val sourceUri = "scraper:${scraper.id}:${seriesKey}"
        val existing = seriesDao.getBySourceUri(sourceUri)
        if (existing != null) return existing.id

        val coverPath = resolveCover(series, seriesKey)
        val accent = coverPath?.let { CoverColor.fromFile(File(it)) }
        val newId = seriesDao.insert(
            SeriesEntity(
                title = series.title,
                coverPath = coverPath,
                kind = format.kind.name,
                sourceUri = sourceUri,
                createdAt = System.currentTimeMillis(),
                accentColor = accent,
            )
        )
        return if (newId > 0) newId else (seriesDao.getBySourceUri(sourceUri)?.id ?: 0)
    }

    private suspend fun resolveCover(series: ScrapedSeries, seriesKey: String): String? {
        val coverUrl = series.coverUrl ?: return null
        val existingCover = File(context.filesDir, "covers/scraper_${seriesKey}.jpg")
        if (existingCover.exists()) return existingCover.absolutePath

        return runCatching {
            val bytes = HttpClient.fetchBytes(coverUrl)
            if (ImageScaling.saveScaledJpeg(bytes, existingCover)) existingCover.absolutePath else null
        }.getOrNull()
    }

    private suspend fun downloadImages(urls: List<String>, targetDir: File, referer: String): List<File> {
        require(urls.isNotEmpty()) { "The chapter contains no readable pages" }
        return urls.mapIndexed { index, url ->
            currentCoroutineContext().ensureActive()
            val ext = url.substringBefore('?').substringAfterLast('.').lowercase()
                .takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif", "avif") } ?: "jpg"
            val file = File(targetDir, "page${"%03d".format(index + 1)}.$ext")
            if (!ImageDownloads.isValid(file)) {
                HttpClient.downloadImage(url, file, SourceReferers.forUrl(url) ?: referer)
            }
            currentCoroutineContext().ensureActive()
            file
        }
    }

    fun chapterCacheDir(scraperId: String, seriesUrl: String, chapterUrl: String): File {
        val seriesKey = Keys.forUri(seriesUrl)
        val chapterKey = Keys.forUri(chapterUrl)
        return File(baseCacheDir, "$scraperId/$seriesKey/$chapterKey")
    }

    /**
     * Total on-disk size (bytes) of a downloaded series, addressed by the series'
     * stored sourceUri ("scraper:{scraperId}:{seriesKey}"). Used by the downloads
     * manager so the user can see what each download is costing before deleting it.
     */
    fun downloadSize(sourceUri: String): Long {
        val rest = sourceUri.removePrefix("scraper:")
        val sep = rest.indexOf(':')
        if (sep <= 0) return 0L
        val scraperId = rest.substring(0, sep)
        val seriesKey = rest.substring(sep + 1)
        val dir = File(baseCacheDir, "$scraperId/$seriesKey")
        if (!dir.isDirectory) return 0L
        return dir.walkBottomUp().filter { it.isFile }.fold(0L) { acc, f -> acc + f.length() }
    }

    fun sourceUriFor(scraper: Scraper, series: ScrapedSeries): String {
        val seriesKey = Keys.forUri(series.url)
        return "scraper:${scraper.id}:${seriesKey}"
    }

    suspend fun seriesIdFor(scraper: Scraper, series: ScrapedSeries): Long? {
        return seriesDao.getBySourceUri(sourceUriFor(scraper, series))?.id
    }

    suspend fun getReadChapterIndices(scraper: Scraper, series: ScrapedSeries): Set<Int> {
        val sId = seriesIdFor(scraper, series) ?: return emptySet()
        val chapters = bookDao.getChapters(sId)
        return chapters.filter { it.progressPercent >= 0.95f }.map { it.seriesOrder }.toSet()
    }

    suspend fun getDownloadedChapterIndices(scraper: Scraper, series: ScrapedSeries): Set<Int> {
        val sId = seriesIdFor(scraper, series) ?: return emptySet()
        return bookDao.getChapters(sId).map { it.seriesOrder }.toSet()
    }
}
