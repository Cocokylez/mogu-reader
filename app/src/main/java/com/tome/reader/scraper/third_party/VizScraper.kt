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
 * VIZ's free Shonen Jump catalogue.
 *
 * The listing at /read/shonenjump/section/free-chapters is fully server-rendered:
 * ~280 series as `div.o_sortable` tiles, each an `a[href^=/shonenjump/chapters/]`
 * whose cover sits in `img.lazy[data-original]` (the plain `src` is a placeholder,
 * which is why an earlier selector pass here returned nothing).
 *
 * Reading happens on viz.com: chapters are served through VIZ's own authenticated
 * viewer, so [fetchChapter] points the reader at the site instead of pretending
 * an import is possible.
 */
class VizScraper : HtmlScraper(
    id = "viz",
    name = "VIZ",
    baseUrl = "https://www.viz.com",
    category = ScraperCategory.MANGA,
) {

    private val freeChapters = "$baseUrl/read/shonenjump/section/free-chapters"

    override suspend fun fetchLatest(): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        diagnostics.clear()
        val doc = fetchDocSafe(freeChapters, tag = "-free")
            ?: fetchDocSafe("$baseUrl/shonenjump", tag = "-sj")
            ?: return@withContext emptyList()
        val items = parseTiles(doc)
        diag("  free-chapters returned items=${items.size}")
        items.take(30)
    }

    /** The listing is one page of ~280 titles, so paging just slices it. */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        if (page < 1) return@withContext emptyList()
        val doc = fetchDocSafe(freeChapters, tag = "-free$page") ?: return@withContext emptyList()
        parseTiles(doc).drop((page - 1) * 30).take(30)
    }

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ScraperSearchPage(emptyList(), hasMore = false)
        // VIZ has no usable search endpoint for an HTML client; filtering the
        // catalogue we can already read is both accurate and one request.
        val doc = fetchDocSafe(freeChapters, tag = "-search") ?: return@withContext ScraperSearchPage(emptyList(), false)
        val terms = query.trim().lowercase().split(' ').filter(String::isNotBlank)
        val hits = parseTiles(doc).filter { series -> terms.all { series.title.contains(it, ignoreCase = true) } }
        val start = (page.coerceAtLeast(1) - 1) * 30
        ScraperSearchPage(hits.drop(start).take(30), hasMore = start + 30 < hits.size)
    }

    private fun parseTiles(doc: Document): List<ScrapedSeries> {
        val tiles = doc.select("div.o_sortable, div.p-cs-tile")
        val fromTiles = tiles.mapNotNull { tile ->
            val link = tile.selectFirst("a[href*=/chapters/]") ?: return@mapNotNull null
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = link.ownText().trim()
                .ifBlank { link.selectFirst("div.line-solid, div.type-center")?.text()?.trim().orEmpty() }
                .ifBlank { tile.attr("data-sort-alpha-label").replace('-', ' ').trim() }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = null,
                coverUrl = imgUrl(link.selectFirst("img")),
                url = resolveUrl(href),
                category = ScraperCategory.MANGA,
            )
        }
        if (fromTiles.isNotEmpty()) return fromTiles.distinctBy { it.url.lowercase() }
        // Fallback if VIZ reshuffles its tile classes: the hrefs are distinctive.
        return doc.select("a[href*=/shonenjump/chapters/]").mapNotNull { link ->
            val title = link.ownText().trim().ifBlank { link.text().trim() }
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

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        val doc = fetchDocSafe(seriesUrl, tag = "-chapters") ?: return@withContext emptyList()
        doc.select("a[href*=/shonenjump/][href*=chapter]").mapNotNull { link ->
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = link.text().trim().ifBlank { link.attr("title").trim() }
            if (label.isBlank()) return@mapNotNull null
            ScrapedChapter(
                title = label,
                url = resolveUrl(href),
                number = extractChapterNumber(label),
            )
        }.distinctBy { it.url.lowercase() }
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent =
        throw IOException(
            "VIZ serves chapters through its own viewer, so they cannot be imported. " +
                "Open viz.com to read this chapter."
        )
}
