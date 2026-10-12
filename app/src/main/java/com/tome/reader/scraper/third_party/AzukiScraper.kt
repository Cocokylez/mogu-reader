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
import org.jsoup.nodes.Element
import java.io.IOException

/**
 * Azuki's catalogue.
 *
 * /series is server-rendered and paged (?page=N), 30 titles a page. Each card is
 * an `a.a-card-link[href^=/series/]` inside an `h3`, and the cover sits further up
 * the card in a `picture > source[srcset]` (responsive webp; the widest entry is
 * last, which is what [imgUrl] picks out of a srcset).
 *
 * Azuki is a subscription service. Its catalogue is public, so browsing works
 * in-app; chapters are served through Azuki's own reader, so [fetchChapter] sends
 * the reader to the site rather than pretending an import is possible.
 */
class AzukiScraper : HtmlScraper(
    id = "azuki",
    name = "Azuki",
    baseUrl = "https://www.azuki.co",
    category = ScraperCategory.MANGA,
) {

    override suspend fun fetchLatest(): List<ScrapedSeries> = fetchLatestPage(1)

    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        if (page < 1) return@withContext emptyList()
        diagnostics.clear()
        val suffix = if (page <= 1) "" else "?page=$page"
        val doc = fetchDocSafe("$baseUrl/series$suffix", tag = "-series$page") ?: return@withContext emptyList()
        val items = parseCards(doc)
        diag("  /series page $page returned items=${items.size}")
        items
    }

    /**
     * Azuki has no query endpoint an HTML client can use -- /search?q=, ?text= and
     * /series?search= all return the same unfiltered Discover page -- so searching
     * filters the catalogue pages instead.
     */
    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        val terms = query.trim().lowercase().split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return@withContext ScraperSearchPage(emptyList(), hasMore = false)
        val hits = mutableListOf<ScrapedSeries>()
        for (catalogPage in 1..MAX_SEARCH_PAGES) {
            val batch = fetchLatestPage(catalogPage)
            if (batch.isEmpty()) break
            hits += batch.filter { series -> terms.all { series.title.contains(it, ignoreCase = true) } }
        }
        ScraperSearchPage(hits.distinctBy { it.url.lowercase() }.take(30), hasMore = false)
    }

    private fun parseCards(doc: Document): List<ScrapedSeries> =
        doc.select("a.a-card-link[href*=/series/], a[href*=/series/]").mapNotNull { link ->
            val href = link.attr("href").takeIf { it.contains("/series/") } ?: return@mapNotNull null
            val title = link.text().trim().ifBlank { link.attr("data-ga-item-name").trim() }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = null,
                coverUrl = coverFor(link),
                url = resolveUrl(href),
                category = ScraperCategory.MANGA,
            )
        }.distinctBy { it.url.lowercase() }

    /** The cover is a sibling of the title anchor, so walk up to the card first. */
    private fun coverFor(link: Element): String? {
        val card = generateSequence(link.parent()) { it.parent() }
            .take(6)
            .firstOrNull { it.selectFirst("picture, img") != null }
            ?: return null
        // Pass the element, not the raw srcset: only the Element overload splits a
        // srcset into candidates and picks the widest. Handing the whole
        // "url 200w, url 400w, ..." string to the String overload yields a value
        // that is not a URL, which is why every Azuki cover came up blank.
        return imgUrl(card.selectFirst("picture source"))
            ?: imgUrl(card.selectFirst("img"))
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        val doc: Document = fetchDocSafe(seriesUrl, tag = "-chapters") ?: return@withContext emptyList()
        doc.select("a[href*=/read/]").mapNotNull { link ->
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = link.text().trim().ifBlank { link.attr("title").trim() }
            if (label.isBlank()) return@mapNotNull null
            ScrapedChapter(title = label, url = resolveUrl(href), number = extractChapterNumber(label))
        }.distinctBy { it.url.lowercase() }
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent =
        throw IOException(
            "Azuki serves chapters through its own reader, so they cannot be imported. " +
                "Open azuki.co to read this chapter."
        )

    private companion object {
        /** 30 titles a page; enough to cover the catalogue without hammering it. */
        const val MAX_SEARCH_PAGES = 6
    }
}
