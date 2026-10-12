package com.tome.reader.scraper.third_party

import android.util.Log
import com.tome.reader.scraper.HttpClient
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class MangaPlusScraper : Scraper {

    override val id = "mangaplus"
    override val name = "MANGA Plus"
    override val baseUrl = "https://mangaplus.shueisha.co.jp"
    override val category = ScraperCategory.MANGA
    override val isAvailable = true

    private val apiBase = "https://jumpg-api.tokyo-cdn.com"

    private val authParams = "app_ver=97&os=ios&os_ver=18.1&secret=f40080bcb01a9a963912f46688d411a3"

    private val _diagnostics = StringBuilder()
    private val _lastError = mutableListOf<String>()

    override val diagnosticsText: String get() = _diagnostics.toString().trim()
    override val lastErrorText: String? get() = _lastError.lastOrNull()

    private fun diag(msg: String) {
        _diagnostics.appendLine(msg)
        Log.d("MoguScraper", "DIAG [${name}] $msg")
    }

    private fun fetchProto(path: String): Result<ByteArray> {
        val sep = if (path.contains("?")) "&" else "?"
        val url = "$apiBase$path$sep$authParams"
        diag("  GET $url")
        return try {
            val bytes = HttpClient.fetchProtobuf(url, referer = baseUrl)
            diag("  OK (${bytes.size} bytes)")
            Result.success(bytes)
        } catch (e: IOException) {
            diag("  FAIL: ${e.message?.take(80)}")
            Result.failure(e)
        }
    }

    override suspend fun fetchLatest(): List<ScrapedSeries> = fetchLatestPage(1)

    /**
     * Catalogue paging. allV2 returns every title in a single request, so pages
     * are slices of the cached list rather than extra round trips.
     */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> {
        if (page < 1) return emptyList()
        val all = cachedCatalogue()
        return all.drop((page - 1) * CATALOGUE_PAGE).take(CATALOGUE_PAGE)
    }

    private suspend fun fetchLatestAll(): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        _diagnostics.clear()
        _lastError.clear()
        val result = fetchProto("/api/title_list/allV2")
        if (result.isFailure) return@withContext emptyList()
        val titles = MangaPlusProtobuf.parseAllTitles(result.getOrThrow())
        diag("  parsed ${titles.size} titles")
        titles.map { mp ->
            ScrapedSeries(
                title = mp.name,
                author = mp.author.takeIf { it.isNotBlank() },
                coverUrl = mp.portraitImageUrl.takeIf { it.isNotBlank() },
                url = mp.titleId.toString(),
                category = ScraperCategory.MANGA,
            )
        }.filter { it.title.isNotBlank() }
    }

    override suspend fun search(query: String): List<ScrapedSeries> = searchPage(query, 1).items

    /**
     * Searches the full catalogue locally.
     *
     * /api/title_list/searchV2 now answers 404, so every in-app search returned
     * nothing for MANGA Plus. The allV2 catalogue is a single request that already
     * carries all ~1000 titles with names and authors, so filtering it is both
     * accurate and cheaper than a per-query round trip.
     */
    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        val terms = query.trim().lowercase().split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return@withContext ScraperSearchPage(emptyList(), false)
        val catalogue = cachedCatalogue()
        val hits = catalogue.filter { series ->
            val haystack = "${series.title} ${series.author.orEmpty()}".lowercase()
            terms.all { haystack.contains(it) }
        }
        val start = (page.coerceAtLeast(1) - 1) * 30
        ScraperSearchPage(hits.drop(start).take(30), hasMore = start + 30 < hits.size)
    }

    private var catalogue: List<ScrapedSeries> = emptyList()
    private var catalogueAt = 0L

    private suspend fun cachedCatalogue(): List<ScrapedSeries> {
        val fresh = catalogue.isNotEmpty() &&
            (System.nanoTime() - catalogueAt) < 600_000_000_000L // 10 minutes
        if (fresh) return catalogue
        val loaded = fetchLatestAll()
        if (loaded.isNotEmpty()) {
            catalogue = loaded
            catalogueAt = System.nanoTime()
        }
        return catalogue
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        // Only V3 exists now -- /api/title_detail and title_detailV2 both 404, so
        // trying them just added a wasted round trip to every series open.
        val attempts = listOf("/api/title_detailV3?title_id=$seriesUrl")
        for (path in attempts) {
            val result = fetchProto(path)
            if (result.isFailure) continue
            val (_, chs, _) = MangaPlusProtobuf.parseTitleDetail(result.getOrThrow())
            if (chs.isNotEmpty()) {
                return@withContext chs.map { ch ->
                    val name = ch.chapterName.ifBlank { "" }
                    val num = if (ch.chapterNumber > 0) ch.chapterNumber.toDouble() else null
                    val display = when {
                        name.isNotBlank() -> if (num != null) "#${num.toInt()} - $name" else name
                        num != null -> "Chapter ${num.toInt()}"
                        else -> "Chapter ${ch.chapterId}"
                    }
                    ScrapedChapter(
                        title = display,
                        url = ch.chapterId.toString(),
                        number = num,
                        releaseDate = null,
                    )
                }.reversed()
            }
        }
        emptyList()
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val result = fetchProto("/api/manga_viewer?chapter_id=$chapterUrl&split=yes&img_quality=super_high")
        if (result.isFailure) return@withContext ScrapedChapterContent.Images(emptyList())
        val (pages, _) = MangaPlusProtobuf.parseMangaViewer(result.getOrThrow())
        if (pages.isEmpty()) return@withContext ScrapedChapterContent.Images(emptyList())
        ScrapedChapterContent.Images(pages.map { it.imageUrl })
    }

    fun fetchDecryptedImageBytes(url: String, encryptionKey: String): ByteArray {
        val raw = HttpClient.fetchBytes(url, referer = baseUrl)
        return MangaPlusProtobuf.xorDecrypt(raw, encryptionKey)
    }

    private companion object {
        /** Slice size for browsing the single-shot catalogue. */
        const val CATALOGUE_PAGE = 30
    }
}
