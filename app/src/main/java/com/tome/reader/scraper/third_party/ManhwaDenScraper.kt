package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.HttpClient
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import com.tome.reader.scraper.generic.HtmlScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

/**
 * ManhwaDen, a Madara/WordPress site.
 *
 * Verified against the live site before writing:
 *  - /manga/ is server-rendered, with ?m_orderby=views and =new-manga for the
 *    popular and latest orderings, and /manga/page/N/ for paging.
 *  - The chapter list is NOT in the series page HTML; Madara loads it from
 *    POST <series>/ajax/chapters/, which returns li.wp-manga-chapter rows.
 *  - Chapter pages carry their images directly in .reading-content img[src],
 *    hosted on remanhwa.me with no hotlink check.
 */
class ManhwaDenScraper : HtmlScraper(
    id = "manhwaden",
    name = "ManhwaDen",
    baseUrl = "https://www.manhwaden.com",
    category = ScraperCategory.MANHWA,
) {

    // Adult-oriented catalogue; hidden entirely while the 18+ setting is off.
    override val isAdultSource = true

    override suspend fun fetchLatest(): List<ScrapedSeries> = fetchLatestPage(1)

    override suspend fun fetchPopular(): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        val doc = fetchDocSafe("$baseUrl/manga/?m_orderby=views", tag = "-popular")
            ?: return@withContext emptyList()
        parseCards(doc).also { diag("  popular returned items=${it.size}") }
    }

    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        if (page < 1) return@withContext emptyList()
        diagnostics.clear()
        val url = if (page == 1) {
            "$baseUrl/manga/?m_orderby=new-manga"
        } else {
            "$baseUrl/manga/page/$page/?m_orderby=new-manga"
        }
        val doc = fetchDocSafe(url, tag = "-latest$page") ?: return@withContext emptyList()
        parseCards(doc).also { diag("  /manga/ page $page returned items=${it.size}") }
    }

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ScraperSearchPage(emptyList(), hasMore = false)
        val p = page.coerceAtLeast(1)
        val url = if (p == 1) {
            "$baseUrl/?s=${encode(query)}&post_type=wp-manga"
        } else {
            "$baseUrl/page/$p/?s=${encode(query)}&post_type=wp-manga"
        }
        val doc = fetchDocSafe(url, tag = "-search")
            ?: return@withContext ScraperSearchPage(emptyList(), false)
        val items = parseCards(doc)
        ScraperSearchPage(items, hasMore = doc.select("a.next.page-numbers").isNotEmpty())
    }

    private fun parseCards(doc: Document): List<ScrapedSeries> {
        val cards = doc.select(".page-item-detail, .c-tabs-item__content, .manga-item")
        val parsed = cards.mapNotNull { card ->
            val link = card.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val href = link.attr("href")
            if (!isSeriesUrl(href)) return@mapNotNull null
            val title = link.attr("title").trim()
                .ifBlank { card.selectFirst(".post-title, h3, h4")?.text()?.trim().orEmpty() }
                .ifBlank { link.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = card.selectFirst(".author, .mg_author")?.text()?.trim()
                    ?.removePrefix("by")?.trim()?.takeIf(String::isNotBlank),
                coverUrl = imgUrl(card.selectFirst("img")),
                url = resolveUrl(href),
                category = ScraperCategory.MANHWA,
            )
        }
        // The card selector alone finds ~13 of the ~24 series a listing page
        // actually links, so the href sweep is merged in rather than used only as
        // a fallback. Cards come first because they carry cover and author.
        val swept = sweepLinks(doc)
        return (parsed + swept).distinctBy { it.url.lowercase() }
    }

    private fun sweepLinks(doc: Document): List<ScrapedSeries> {
        return doc.select("a[href*=/manga/]").mapNotNull { link ->
            val href = link.attr("href")
            if (!isSeriesUrl(href)) return@mapNotNull null
            val title = link.attr("title").trim().ifBlank { link.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = null,
                coverUrl = imgUrl(link.selectFirst("img")),
                url = resolveUrl(href),
                category = ScraperCategory.MANHWA,
            )
        }.distinctBy { it.url.lowercase() }
    }

    /** /manga/<slug>/ only -- not /manga/<slug>/chapter-N/, feeds or paging. */
    private fun isSeriesUrl(href: String): Boolean {
        val path = href.substringAfter("/manga/", "").substringBefore('?').trim('/')
        if (path.isBlank() || path.contains('/')) return false
        // WordPress hangs its own routes off the same prefix; /manga/feed/ is an
        // RSS feed and sorted first in the listing, so it became the first
        // "series" every catalogue call returned.
        return path.lowercase() !in NON_SERIES_PATHS
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> = withContext(Dispatchers.IO) {
        val base = seriesUrl.trimEnd('/')
        // Madara serves the chapter list from its own endpoint; the series page
        // itself contains no wp-manga-chapter rows at all.
        val doc = try {
            HttpClient.postForm("$base/ajax/chapters/", emptyMap(), referer = seriesUrl)
        } catch (e: Exception) {
            diag("  ajax/chapters failed: ${e.javaClass.simpleName} ${e.message?.take(70)}")
            fetchDocSafe(seriesUrl, tag = "-chapters") ?: return@withContext emptyList()
        }
        val chapters = doc.select("li.wp-manga-chapter a, .wp-manga-chapter a").mapNotNull { link ->
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // The sidebar links other series; keep only this one's chapters.
            if (!href.startsWith(base)) return@mapNotNull null
            val label = link.text().trim()
            if (label.isBlank()) return@mapNotNull null
            ScrapedChapter(
                title = label,
                url = resolveUrl(href),
                number = extractChapterNumber(label),
                releaseDate = link.parent()?.selectFirst(".chapter-release-date")?.text()?.trim(),
            )
        }.distinctBy { it.url.lowercase() }
        diag("  chapters=${chapters.size}")
        // Newest first on the site; the reader expects oldest first.
        chapters.reversed()
    }

    /**
     * Chapter art lives on the image host; the reading area also carries site
     * furniture (logo, lazy placeholder) and sidebar covers, which are WordPress
     * uploads and carry a -WxH size suffix. Those are not pages.
     */
    private fun isPageImage(url: String): Boolean {
        if (url.contains("/themes/", ignoreCase = true)) return false
        if (url.contains("dflazy", ignoreCase = true)) return false
        if (url.contains("logo", ignoreCase = true)) return false
        // Analytics beacons sit just outside the reading area today, so jsoup's
        // scoping already excludes them; this keeps them out if the markup shifts.
        if (TRACKER_HOSTS.any { url.contains(it, ignoreCase = true) }) return false
        return !hasThumbnailSizeSuffix(url)
    }

    /** WordPress thumbnails end "-<width>x<height>.<ext>", e.g. "-125x180.webp". */
    private fun hasThumbnailSizeSuffix(url: String): Boolean {
        val name = url.substringAfterLast('/').substringBefore('?').substringBeforeLast('.')
        val suffix = name.substringAfterLast('-', missingDelimiterValue = "")
        val parts = suffix.split('x')
        return parts.size == 2 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent = withContext(Dispatchers.IO) {
        val doc = fetchDocSafe(chapterUrl, tag = "-page")
            ?: return@withContext ScrapedChapterContent.Images(emptyList())
        val pages = doc.select(".reading-content img, .read-container img")
            .mapNotNull { imgUrl(it) }
            .filter(::isPageImage)
            .distinct()
        diag("  pages=${pages.size}")
        ScrapedChapterContent.Images(pages)
    }

    private companion object {
        /** WordPress routes that share the /manga/ prefix but are not series. */
        val NON_SERIES_PATHS = setOf("feed", "page", "amp", "embed")

        /** Analytics beacons that render as <img> on these pages. */
        val TRACKER_HOSTS = listOf("histats.com", "mc.yandex.ru", "google-analytics.com")
    }
}
