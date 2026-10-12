package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.ContentFilter
import com.tome.reader.scraper.HttpClient
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/** Public MangaDex API. Retained UUID keys keep existing library entries working. */
class MangaDexScraper(
    private val fetchJson: (String) -> JSONObject = { HttpClient.fetchJson(it) },
) : Scraper {
    override val id = "mangadex"
    override val name = "MangaDex"
    override val baseUrl = "https://mangadex.org"
    override val category = ScraperCategory.MANGA
    override val isAvailable = true
    private val requestMutex = Mutex()
    private var lastRequestAt = 0L
    private val api = "https://api.mangadex.org"
    /**
     * MangaDex is the one source that reports a per-title content rating, so it
     * can filter precisely: the 18+ ratings are only requested when the reader
     * has turned them on.
     */
    private val ratings: String
        get() = if (ContentFilter.allowAdult) {
            "&contentRating[]=safe&contentRating[]=suggestive" +
                "&contentRating[]=erotica&contentRating[]=pornographic"
        } else {
            "&contentRating[]=safe&contentRating[]=suggestive"
        }

    override suspend fun fetchLatest(): List<ScrapedSeries> = catalogue("", 1, "latestUploadedChapter").items

    /** Catalogue paging: MangaDex pages by offset, 24 at a time. */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> =
        if (page < 1) emptyList() else catalogue("", page, "latestUploadedChapter").items
    override suspend fun fetchPopular(): List<ScrapedSeries> = catalogue("", 1, "followedCount").items
    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage =
        catalogue(query.trim(), page, if (query.isBlank()) "latestUploadedChapter" else "relevance")

    private suspend fun request(url: String): JSONObject = requestMutex.withLock {
        val elapsed = (System.nanoTime() - lastRequestAt) / 1_000_000
        if (elapsed < 250) delay(250 - elapsed)
        lastRequestAt = System.nanoTime()
        withContext(Dispatchers.IO) {
            val json = fetchJson(url)
            if (json.optString("result") == "error") {
                throw IOException("MangaDex could not complete this request. Please try again later.")
            }
            json
        }
    }

    private suspend fun catalogue(query: String, page: Int, order: String): ScraperSearchPage {
        if (page > 417) return ScraperSearchPage(emptyList(), false)
        val offset = (page.coerceAtLeast(1) - 1) * 24
        val limit = minOf(24, 10_000 - offset)
        val url = "$api/manga?limit=$limit&offset=$offset$ratings" +
            "&originalLanguage[]=ja&originalLanguage[]=ko" +
            "&availableTranslatedLanguage[]=en&hasAvailableChapters=true" +
            "&order[$order]=desc&includes[]=cover_art&includes[]=author" +
            if (query.isBlank()) "" else "&title=${URLEncoder.encode(query.take(200), "UTF-8")}" 
        val json = request(url)
        val data = json.optJSONArray("data") ?: throw IOException("MangaDex returned an invalid catalogue.")
        val items = (0 until data.length()).mapNotNull { index ->
            val manga = data.optJSONObject(index) ?: return@mapNotNull null
            val attrs = manga.optJSONObject("attributes") ?: return@mapNotNull null
            val language = attrs.optString("originalLanguage")
            if (language !in setOf("ja", "ko")) return@mapNotNull null
            val mangaId = manga.text("id") ?: return@mapNotNull null
            if (!UUID.matches(mangaId)) return@mapNotNull null
            val cover = relationship(manga, "cover_art")?.text("fileName")
            ScrapedSeries(
                title = resolveTitle(attrs),
                author = relationship(manga, "author")?.text("name"),
                coverUrl = cover?.let { "https://uploads.mangadex.org/covers/$mangaId/$it.256.jpg" },
                url = mangaId,
                description = attrs.optJSONObject("description")?.text("en"),
                status = attrs.text("status")?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
                category = if (language == "ko") ScraperCategory.MANHWA else ScraperCategory.MANGA,
            )
        }
        val end = offset + data.length()
        return ScraperSearchPage(items, data.length() > 0 && end < json.optInt("total") && end < 10_000)
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> {
        val mangaId = identifier(seriesUrl, "title")
        val chapters = mutableListOf<ScrapedChapter>()
        var offset = 0
        while (offset < 10_000) {
            val json = request("$api/manga/$mangaId/feed?limit=500&offset=$offset$ratings" +
                "&translatedLanguage[]=en&order[volume]=asc&order[chapter]=asc" +
                "&includeExternalUrl=0&includeFuturePublishAt=0&includes[]=scanlation_group")
            val data = json.optJSONArray("data") ?: throw IOException("MangaDex returned an invalid chapter list.")
            for (i in 0 until data.length()) {
                val chapter = data.optJSONObject(i) ?: continue
                val attrs = chapter.optJSONObject("attributes") ?: continue
                if (attrs.optString("translatedLanguage") != "en" || attrs.optInt("pages") <= 0 || attrs.text("externalUrl") != null) continue
                val chapterId = chapter.text("id")?.takeIf(UUID::matches) ?: continue
                val number = attrs.text("chapter")
                val title = buildString {
                    attrs.text("volume")?.let { append("Vol. $it · ") }
                    append("Chapter ${number ?: "?"}")
                    attrs.text("title")?.let { append(" · $it") }
                    relationship(chapter, "scanlation_group")?.text("name")?.let { append(" ($it)") }
                }
                chapters += ScrapedChapter(title, chapterId, number?.toDoubleOrNull(), attrs.text("publishAt")?.take(10))
            }
            offset += data.length()
            if (data.length() == 0 || offset >= json.optInt("total")) break
            if (offset >= 10_000) throw IOException("This MangaDex title has too many releases to load completely. Open it on MangaDex.")
        }
        return chapters.distinctBy { it.url }
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent {
        val chapterId = identifier(chapterUrl, "chapter")
        val json = request("$api/at-home/server/$chapterId")
        val chapter = json.optJSONObject("chapter")
        val server = json.text("baseUrl")?.trimEnd('/')
        val hash = chapter?.text("hash")
        val pages = chapter?.optJSONArray("data")
        if (server == null || !server.startsWith("https://") || hash == null || pages == null || pages.length() == 0) {
            throw IOException("This chapter is unavailable on MangaDex. Choose another chapter or open the website.")
        }
        val urls = (0 until pages.length()).map { i ->
            val filename = pages.optString(i).takeIf { it.isNotBlank() && it != "null" }
                ?: throw IOException("MangaDex returned an invalid chapter page.")
            "$server/data/${pathSegment(hash)}/${pathSegment(filename)}"
        }
        return ScrapedChapterContent.Images(urls)
    }

    internal fun identifier(value: String, path: String): String {
        val cleaned = value.trim()
        if (UUID.matches(cleaned)) return cleaned.lowercase()
        val uri = runCatching { URI(cleaned) }.getOrNull()
        val parts = uri?.path?.trim('/')?.split('/').orEmpty()
        if (uri?.scheme !in setOf("http", "https") || uri?.host?.lowercase() !in setOf("mangadex.org", "www.mangadex.org") ||
            parts.size < 2 || parts[0] != path || !UUID.matches(parts[1])) {
            throw IOException("Invalid MangaDex $path link.")
        }
        return parts[1].lowercase()
    }

    private fun relationship(item: JSONObject, type: String): JSONObject? {
        val relationships = item.optJSONArray("relationships") ?: return null
        for (i in 0 until relationships.length()) {
            val relation = relationships.optJSONObject(i) ?: continue
            if (relation.optString("type") == type) return relation.optJSONObject("attributes")
        }
        return null
    }

    private fun resolveTitle(attrs: JSONObject): String {
        val title = attrs.optJSONObject("title")
        title?.text("en")?.let { return it }
        val alternatives = attrs.optJSONArray("altTitles")
        if (alternatives != null) for (i in 0 until alternatives.length()) alternatives.optJSONObject(i)?.text("en")?.let { return it }
        for (key in listOf("ja-ro", "ko-ro")) title?.text(key)?.let { return it }
        title?.keys()?.forEach { key -> title.text(key)?.let { return it } }
        return "Untitled"
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).trim().takeIf { it.isNotBlank() }
    private fun pathSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
    }
}
