package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.HttpClient
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/** Reads the current public Astro comic catalogue; novel paths are never selected. */
class AsuraScansScraper(
    private val fetchDocument: (String) -> Document = { HttpClient.fetchDocument(it) },
) : Scraper {
    override val id = "asurascans"
    override val name = "Asura Scans"
    override val baseUrl = "https://asurascans.com"
    override val category = ScraperCategory.MANHWA
    override val isAvailable = true

    override suspend fun fetchLatest(): List<ScrapedSeries> = searchPage("", 1).items

    /** Catalogue paging: the blank-query search is this source's browse listing. */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> =
        if (page < 1) emptyList() else searchPage("", page).items

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        val currentPage = page.coerceIn(1, 1000)
        val url = "$baseUrl/browse/comics?type=manhwa&page=$currentPage" +
            if (query.isBlank()) "" else "&q=${URLEncoder.encode(query.trim().take(200), "UTF-8")}" 
        val doc = fetchDocument(url)
        val props = SourceParsing.astroProps(doc, "BrowseFilters")
        val metadata = props.optJSONArray("initialSeries")
        val items = doc.select("a[href*=/comics/]").mapNotNull { link ->
            val image = link.selectFirst("img") ?: return@mapNotNull null
            val seriesUrl = runCatching { SourceParsing.sourceUrl(link.attr("href"), baseUrl, Regex("/comics/[^/]+/?")) }.getOrNull() ?: return@mapNotNull null
            val title = image.attr("alt").trim().ifBlank { link.attr("title").trim() }.ifBlank { link.selectFirst("h3, h4")?.text().orEmpty() }
            if (title.isBlank()) return@mapNotNull null
            val details = metadata?.let { values -> (0 until values.length()).mapNotNull(values::optJSONObject).firstOrNull { it.optString("title") == title } }
            val type = details?.optString("type").orEmpty()
            if (type.isNotBlank() && type.lowercase() !in setOf("manga", "manhwa")) return@mapNotNull null
            ScrapedSeries(title, null, SourceParsing.image(image, url), seriesUrl,
                description = details?.optString("description")?.let { Jsoup.parse(it).text() }?.takeIf(String::isNotBlank),
                status = details?.optString("status")?.takeIf(String::isNotBlank), category = category)
        }.distinctBy { it.url }
        if (items.isEmpty() && !props.has("initialSeries") && !Regex("no (?:series|results)|nothing found", RegexOption.IGNORE_CASE).containsMatchIn(doc.text())) {
            throw IOException("Asura changed its catalogue page. Open the website or try another source.")
        }
        val totalPages = props.optInt("initialTotalPages")
        val more = if (totalPages > 0) currentPage < totalPages else doc.select("button[aria-label=Next page]:not([disabled])").isNotEmpty()
        ScraperSearchPage(items, more)
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        val valid = SourceParsing.sourceUrl(seriesUrl, baseUrl, Regex("/comics/[^/]+/?"))
        val doc = fetchDocument(valid)
        doc.select("a[href*=/chapter/]").mapNotNull { link ->
            val url = runCatching { SourceParsing.sourceUrl(link.attr("href"), baseUrl, Regex("/comics/[^/]+/chapter/[0-9.]+/?")) }.getOrNull() ?: return@mapNotNull null
            if (!url.startsWith("$valid/chapter/")) return@mapNotNull null
            val number = URI(url).path.substringAfterLast('/')
            ScrapedChapter("Chapter $number", url, number.toDoubleOrNull())
        }.distinctBy { it.url }.sortedWith(compareBy(nullsLast()) { it.number })
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val valid = SourceParsing.sourceUrl(chapterUrl, baseUrl, Regex("/comics/[^/]+/chapter/[0-9.]+/?"))
        val doc = fetchDocument(valid)
        val urls = doc.select("img").mapNotNull { SourceParsing.image(it, valid) }
            .filter { runCatching { URI(it).path.contains("/asura-images/chapters/") }.getOrDefault(false) }
        ScrapedChapterContent.Images(SourceParsing.requireImages(urls, doc, name))
    }
}
