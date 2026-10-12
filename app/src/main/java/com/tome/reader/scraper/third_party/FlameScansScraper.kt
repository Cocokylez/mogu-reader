package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.HttpClient
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

class FlameScansScraper(
    private val fetchDocument: (String) -> Document = { HttpClient.fetchDocument(it) },
) : Scraper {
    override val id = "flamescans"
    override val name = "Flame Comics"
    override val baseUrl = "https://flamecomics.xyz"
    override val category = ScraperCategory.MANHWA
    override val isAvailable = true

    // Was browser-only for a Cloudflare interstitial. Re-checked against the live
    // site: /browse now answers a plain client with the full 1.5MB catalogue page,
    // __NEXT_DATA__ and all, so the parser below works and this reads in-app again.
    private var cachedCatalogue: List<JSONObject>? = null
    private var cachedAt = 0L
    private val catalogueMutex = Mutex()

    override suspend fun fetchLatest(): List<ScrapedSeries> = searchPage("", 1).items

    /** Catalogue paging: searchPage slices the cached catalogue 24 at a time. */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> =
        if (page < 1) emptyList() else searchPage("", page).items

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        val catalogue = catalogueMutex.withLock {
            val cached = cachedCatalogue
            if (cached != null && (System.nanoTime() - cachedAt) < 600_000_000_000L) cached else {
                val data = SourceParsing.nextProps(fetchDocument("$baseUrl/browse")).optJSONArray("series")
                    ?: throw IOException("Flame Comics changed its catalogue format.")
                (0 until data.length()).mapNotNull(data::optJSONObject).also {
                    cachedCatalogue = it
                    cachedAt = System.nanoTime()
                }
            }
        }
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        val filtered = catalogue.filter { item ->
            item.optString("type").lowercase() in setOf("manga", "manhwa") &&
                terms.all { term -> "${item.optString("title")} ${item.optJSONArray("altTitles") ?: ""}".contains(term, true) }
        }.sortedByDescending { it.optLong("last_edit", it.optLong("time")) }
        val start = (page.coerceIn(1, 1000) - 1) * 24
        ScraperSearchPage(filtered.drop(start).take(24).mapNotNull(::series), start + 24 < filtered.size)
    }

    private fun series(item: JSONObject): ScrapedSeries? {
        val seriesId = item.optString("series_id").takeIf { it.matches(Regex("[0-9]+")) } ?: return null
        val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: return null
        val cover = item.optString("cover").takeIf { it.isNotBlank() && it != "null" } ?: "thumbnail.webp"
        return ScrapedSeries(title, null,
            "https://cdn.flamecomics.xyz/uploads/images/series/$seriesId/${URLEncoder.encode(cover, "UTF-8")}?${item.optLong("last_edit")}",
            "$baseUrl/series/$seriesId", description = Jsoup.parse(item.optString("description")).text().takeIf(String::isNotBlank),
            status = item.optString("status").takeIf(String::isNotBlank),
            category = if (item.optString("type").equals("manga", true)) ScraperCategory.MANGA else ScraperCategory.MANHWA)
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        val valid = SourceParsing.sourceUrl(seriesUrl, baseUrl, Regex("/series/[0-9]+/?"))
        val props = SourceParsing.nextProps(fetchDocument(valid))
        if (props.optJSONObject("series")?.optString("type")?.lowercase() !in setOf("manga", "manhwa")) {
            throw IOException("This source title is not a manga or manhwa.")
        }
        val chapters = props.optJSONArray("chapters") ?: throw IOException("Flame Comics returned an invalid chapter list.")
        (0 until chapters.length()).mapNotNull { index ->
            val chapter = chapters.optJSONObject(index) ?: return@mapNotNull null
            val token = chapter.optString("token").takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) && it != "null" } ?: return@mapNotNull null
            if (chapter.optBoolean("notice") || chapter.optInt("notice") == 1) return@mapNotNull null
            val rawNumber = chapter.optString("chapter").takeIf { it.isNotBlank() && it != "null" }
            val label = chapter.optString("title").takeIf { it.isNotBlank() && it != "null" }
            ScrapedChapter("Chapter ${rawNumber ?: "?"}${label?.let { " · $it" }.orEmpty()}", "$valid/$token", rawNumber?.toDoubleOrNull())
        }.distinctBy { it.url }.sortedWith(compareBy(nullsLast()) { it.number })
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val valid = SourceParsing.sourceUrl(chapterUrl, baseUrl, Regex("/series/[0-9]+/[A-Za-z0-9_-]+/?"))
        val doc = fetchDocument(valid)
        val expectedPath = URI(valid).path.replace("/series/", "/uploads/images/series/") + "/"
        val urls = doc.select("img").mapNotNull { SourceParsing.image(it, valid) }
            .filter { runCatching { URI(it).path.startsWith(expectedPath) }.getOrDefault(false) }
        ScrapedChapterContent.Images(SourceParsing.requireImages(urls, doc, name))
    }
}
