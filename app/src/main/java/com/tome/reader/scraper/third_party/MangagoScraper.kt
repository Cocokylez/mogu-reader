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
 * Mangago.
 *
 * The catalogue at /list/directory/all/ is server-rendered -- cards are
 * `div.listitem`, the link is `a[href*=/read-manga/]`, and the cover is on
 * `img[data-src]` (the `src` is a 1x1 base64 placeholder).
 *
 * Cloudflare here is *intermittent* rather than absolute: probing the directory
 * repeatedly, the first request was challenged and the second returned the real
 * 88KB page. So this is a normal source that sometimes fails, not a browser-only
 * one -- [attempts] retries a few times, and when Cloudflare does win the reader
 * gets the standard "open it once in the in-app browser" message, after which the
 * shared cf_clearance cookie makes subsequent fetches work in-app.
 */
class MangagoScraper : HtmlScraper(
    id = "mangago",
    name = "Mangago",
    baseUrl = "https://www.mangago.me",
    category = ScraperCategory.MANGA,
) {

    override suspend fun fetchLatest(): List<ScrapedSeries> = fetchLatestPage(1)

    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        if (page < 1) return@withContext emptyList()
        diagnostics.clear()
        val doc = fetchPastCloudflare("$baseUrl/list/directory/all/$page/", "-dir")
            ?: return@withContext emptyList()
        val items = parseCards(doc)
        diag("  directory page $page returned items=${items.size}")
        items
    }

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ScraperSearchPage(emptyList(), hasMore = false)
        val url = "$baseUrl/r/l_search/?name=${encode(query)}&page=$page"
        val doc = fetchPastCloudflare(url, "-search") ?: return@withContext ScraperSearchPage(emptyList(), false)
        val items = parseCards(doc)
        ScraperSearchPage(items, hasMore = items.isNotEmpty())
    }

    private fun parseCards(doc: Document): List<ScrapedSeries> {
        val cards = doc.select("div.listitem, div.updatesli, li.pic_list_item")
        val parsed = cards.mapNotNull { card ->
            val link = card.selectFirst("a[href*=/read-manga/]") ?: return@mapNotNull null
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Mangago wraps names in dashes ("-hitogatana-") in both the title
            // attribute and the visible label, so trim them either way.
            val title = card.selectFirst("span.title a")?.text()?.trim()?.trim('-')?.trim()
                .orEmpty()
                .ifBlank { link.attr("title").trim().trim('-').trim() }
                .ifBlank { link.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = card.selectFirst(".author, .auth")?.text()?.trim(),
                coverUrl = imgUrl(card.selectFirst("img")),
                url = resolveUrl(href),
                category = ScraperCategory.MANGA,
            )
        }
        if (parsed.isNotEmpty()) return parsed.distinctBy { it.url.lowercase() }
        // Layout fallback: the /read-manga/ hrefs are distinctive enough alone.
        return doc.select("a[href*=/read-manga/]").mapNotNull { link ->
            val title = link.attr("title").trim().trim('-').ifBlank { link.text().trim() }
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
        val doc = fetchPastCloudflare(seriesUrl, "-chapters")
            ?: throw IOException("Mangago is behind a Cloudflare check. Open it once in the in-app browser.")
        doc.select("table#chapter_table a[href], .chico_list a[href], a.chico[href]").mapNotNull { link ->
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!href.contains("/read-manga/")) return@mapNotNull null
            val label = link.text().trim().ifBlank { link.attr("title").trim() }
            if (label.isBlank()) return@mapNotNull null
            ScrapedChapter(title = label, url = resolveUrl(href), number = extractChapterNumber(label))
        }.distinctBy { it.url.lowercase() }.reversed()
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val doc = fetchPastCloudflare(chapterUrl, "-page")
            ?: throw IOException("Mangago is behind a Cloudflare check. Open it once in the in-app browser.")
        val pages = doc.select("img#page1, img.pic, #pic_container img, .page img").mapNotNull { imgUrl(it) }
        if (pages.isEmpty()) {
            throw IOException("Mangago builds this chapter's pages in the browser, so they cannot be imported.")
        }
        ScrapedChapterContent.Images(pages)
    }
}
