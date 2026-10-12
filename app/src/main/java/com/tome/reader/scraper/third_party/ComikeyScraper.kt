package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import com.tome.reader.scraper.generic.HtmlScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import java.io.IOException

/**
 * Comikey's catalogue.
 *
 * /comics/ is server-rendered: each entry is an `li.item-full-row` holding an
 * `a.image-wrap` with the thumbnail and a `span.title > a` with the name. Series
 * hrefs are absolute and shaped /comics/<slug>/<id>/ -- the same page also links
 * /comics/?person=N filter URLs, which [seriesPath] rejects.
 *
 * This replaces an earlier version whose selectors (".series-card", "/search?q=")
 * were guessed rather than checked; it was never registered because it returned
 * nothing. Everything here was read off the live page.
 *
 * Comikey publishes officially. Its catalogue is public, so browsing works in-app,
 * but episodes are served through Comikey's own reader, so [fetchChapter] sends
 * the reader to the site instead of pretending an import is possible.
 */
class ComikeyScraper : HtmlScraper(
    id = "comikey",
    name = "Comikey",
    baseUrl = "https://comikey.com",
    category = ScraperCategory.MANGA,
) {

    // Comikey answers this app's requests with 403 -- both the full header set and
    // a minimal browser-like one, from an IP where the very same request succeeds
    // from a desktop HTTP client. That points at TLS/client fingerprinting, which
    // is not something worth defeating, so browsing happens on the site. The parser
    // below is verified against the live markup and stays ready if the block lifts.
    override val isBrowserOnly = true

    private val seriesPath = Regex("/comics/[^/?#]+/[0-9]+/?$")

    override suspend fun fetchLatest(): List<ScrapedSeries> = fetchLatestPage(1)

    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        if (page < 1) return@withContext emptyList()
        diagnostics.clear()
        val suffix = if (page <= 1) "" else "?page=$page"
        val doc = fetchDocSafe("$baseUrl/comics/$suffix", tag = "-comics$page") ?: return@withContext emptyList()
        val items = parseCards(doc)
        diag("  /comics/ page $page returned items=${items.size}")
        items
    }

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ScraperSearchPage(emptyList(), hasMore = false)
        val doc = fetchDocSafe("$baseUrl/comics/?q=${encode(query)}", tag = "-search")
            ?: return@withContext ScraperSearchPage(emptyList(), false)
        ScraperSearchPage(parseCards(doc), hasMore = false)
    }

    private fun parseCards(doc: Document): List<ScrapedSeries> {
        val cards = doc.select("li.item-full-row, li.item-mini, li.item-preview")
        val parsed = cards.mapNotNull { card ->
            val link = card.select("a[href]").firstOrNull { seriesPath.containsMatchIn(it.attr("href")) }
                ?: return@mapNotNull null
            val title = card.selectFirst("span.title a")?.text()?.trim().orEmpty()
                .ifBlank { coverAlt(card) }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = card.selectFirst(".author, .creators")?.text()?.trim(),
                coverUrl = imgUrl(card.selectFirst("img")),
                url = resolveUrl(link.attr("href")),
                category = ScraperCategory.MANGA,
            )
        }
        if (parsed.isNotEmpty()) return parsed.distinctBy { it.url.lowercase() }
        // Layout fallback: the series href shape alone is distinctive enough.
        return doc.select("a[href]").toList()
            .filter { seriesPath.containsMatchIn(it.attr("href")) }
            .mapNotNull { link ->
            val title = link.text().trim().ifBlank { coverAlt(link) }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = null,
                coverUrl = imgUrl(link.selectFirst("img")),
                url = resolveUrl(link.attr("href")),
                category = ScraperCategory.MANGA,
            )
        }.distinctBy { it.url.lowercase() }
    }

    /** Thumbnails carry alt="Thumbnail for <title>", a usable last resort. */
    private fun coverAlt(scope: org.jsoup.nodes.Element): String =
        scope.selectFirst("img")?.attr("alt").orEmpty()
            .removePrefix("Thumbnail for").trim()

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        val doc: Document = fetchDocSafe(seriesUrl, tag = "-chapters") ?: return@withContext emptyList()
        doc.select("a[href*=/read/], .episode a[href], #chapters a[href]").mapNotNull { link ->
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = link.text().trim().ifBlank { link.attr("title").trim() }
            if (label.isBlank()) return@mapNotNull null
            ScrapedChapter(title = label, url = resolveUrl(href), number = extractChapterNumber(label))
        }.distinctBy { it.url.lowercase() }
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent =
        throw IOException(
            "Comikey serves episodes through its own reader, so they cannot be imported. " +
                "Open comikey.com to read this episode."
        )
}
