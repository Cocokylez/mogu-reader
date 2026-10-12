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
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

class WeebCentralScraper(
    private val fetchDocument: (String) -> Document = { HttpClient.fetchDocument(it) },
) : Scraper {
    override val id = "weebcentral"
    override val name = "WeebCentral"
    override val baseUrl = "https://weebcentral.com"
    override val category = ScraperCategory.MANHWA
    override val isAvailable = true

    override suspend fun fetchLatest(): List<ScrapedSeries> = searchPage("", 1).items

    /** Catalogue paging: the blank-query search is this source's browse listing. */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> =
        if (page < 1) emptyList() else searchPage("", page).items

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        // The public fragment endpoint currently uses 32-item pages regardless of its limit parameter.
        val offset = (page.coerceIn(1, 1000) - 1) * 32
        val url = "$baseUrl/search/data?limit=32&offset=$offset&text=${URLEncoder.encode(query.trim().take(200), "UTF-8")}" +
            "&sort=${if (query.isBlank()) "Latest%20Updates" else "Best%20Match"}" +
            "&order=${if (query.isBlank()) "Descending" else "Ascending"}" +
            "&official=Any&included_type=Manga&included_type=Manhwa&display_mode=Full%20Display"
        val doc = fetchDocument(url)
        val items = doc.select("a[href*=/series/]").mapNotNull { link ->
            val image = link.select("img").firstOrNull { it.attr("alt").endsWith(" cover", true) } ?: return@mapNotNull null
            val seriesUrl = runCatching { SourceParsing.sourceUrl(link.attr("href"), baseUrl, SERIES_PATH) }.getOrNull() ?: return@mapNotNull null
            val title = image.attr("alt").replace(Regex("\\s+cover$", RegexOption.IGNORE_CASE), "").trim()
            if (title.isBlank()) return@mapNotNull null
            val inner = link.closest("article")
            val outer = inner?.parent()?.closest("article") ?: inner
            val metadata = outer?.text().orEmpty()
            val type = Regex("Type:\\s*(Manga|Manhwa|Manhua)", RegexOption.IGNORE_CASE).find(metadata)?.groupValues?.get(1)
            if (type.equals("Manhua", true)) return@mapNotNull null
            ScrapedSeries(title, null, SourceParsing.image(image, url), seriesUrl,
                status = Regex("\\b(Ongoing|Complete|Hiatus|Canceled)\\b", RegexOption.IGNORE_CASE).find(metadata)?.value,
                category = if (type.equals("Manga", true)) ScraperCategory.MANGA else ScraperCategory.MANHWA)
        }.distinctBy { it.url }
        if (items.isEmpty() && doc.select("a[href*=/series/]").isNotEmpty() && !Regex("no (?:series|results)|nothing found", RegexOption.IGNORE_CASE).containsMatchIn(doc.text())) {
            throw IOException("WeebCentral changed its search page. Open the website or try another source.")
        }
        val more = doc.select("[hx-get]").any { it.attr("hx-get").contains("offset=") && it.text().contains("More", true) }
        ScraperSearchPage(items, more || items.size >= 32)
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        val valid = SourceParsing.sourceUrl(seriesUrl, baseUrl, SERIES_PATH)
        val seriesId = URI(valid).path.split('/')[2]
        val doc = fetchDocument("$baseUrl/series/$seriesId/full-chapter-list")
        doc.select("a[href*=/chapters/]").mapNotNull { link ->
            val url = runCatching { SourceParsing.sourceUrl(link.attr("href"), baseUrl, CHAPTER_PATH) }.getOrNull() ?: return@mapNotNull null
            val title = link.text().replace(Regex("\\s*Last Read.*$", RegexOption.IGNORE_CASE), "").trim()
            val number = Regex("(?:chapter|episode)\\s+([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toDoubleOrNull()
            ScrapedChapter(title.ifBlank { "Chapter" }, url, number)
        }.distinctBy { it.url }.sortedWith(compareBy(nullsLast()) { it.number })
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val valid = SourceParsing.sourceUrl(chapterUrl, baseUrl, CHAPTER_PATH)
        val imagePage = "$valid/images?reading_style=long_strip"
        val doc = fetchDocument(imagePage)
        val urls = doc.select("img").asSequence()
            .filter { it.attr("alt").startsWith("Page", true) }
            .mapNotNull { SourceParsing.image(it, imagePage) }
            .toList()
        ScrapedChapterContent.Images(SourceParsing.requireImages(urls, doc, name))
    }

    companion object {
        private val SERIES_PATH = Regex("/series/[A-Za-z0-9]+(?:/[^/]+)?/?")
        private val CHAPTER_PATH = Regex("/chapters/[A-Za-z0-9]+/?")
    }
}
