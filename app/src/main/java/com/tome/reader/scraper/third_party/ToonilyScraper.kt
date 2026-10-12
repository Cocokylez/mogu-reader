package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import com.tome.reader.scraper.generic.HtmlScraper
import org.jsoup.nodes.Document

class ToonilyScraper : HtmlScraper(
    id = "toonily",
    name = "Toonily",
    baseUrl = "https://toonily.com",
    category = ScraperCategory.MANGA,
) {

    // Adult-oriented catalogue; hidden entirely while the 18+ setting is off.
    override val isAdultSource = true
    override suspend fun fetchLatest(): List<ScrapedSeries> {
        val doc = fetchDocSafe(baseUrl, tag = "-latest") ?: return emptyList()
        return parseMadaraResults(doc)
    }

    /** Catalogue paging: Madara serves further pages at /page/N/. */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> {
        if (page < 1) return emptyList()
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        val doc = fetchPastCloudflare(url, tag = "-latest$page", minContent = 800) ?: return emptyList()
        return parseMadaraResults(doc)
    }

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage {
        if (query.isBlank()) return ScraperSearchPage(emptyList(), hasMore = false)
        val p = page.coerceAtLeast(1)
        val doc = fetchDocSafe("$baseUrl/page/$p/?s=${encode(query)}&post_type=wp-manga")
            ?: return ScraperSearchPage(emptyList(), false)
        val items = parseMadaraResults(doc)
        if (items.isNotEmpty()) {
            val hasNext = doc.select("a.next.page-numbers, a:contains(Next)").isNotEmpty()
            return ScraperSearchPage(items, hasNext)
        }
        val hasNextPage = doc.select("a.next.page-numbers").isNotEmpty()
        return ScraperSearchPage(items, hasNextPage)
    }

    private fun parseMadaraResults(doc: Document): List<ScrapedSeries> {
        val items = doc.select(".c-tabs-item__content, .tab-content-wrap .item, .page-item-detail")
        if (items.isNotEmpty()) {
            return items.mapNotNull { div ->
                val link = div.selectFirst("a[href*=/serie/], a[href*=/manga/]") ?: return@mapNotNull null
                val imgEl = div.selectFirst("img")
                val authorEl = div.selectFirst(".author, .mg_author")
                ScrapedSeries(
                    title = link.attr("title").ifBlank { link.text() }.trim(),
                    author = authorEl?.text()?.trim()?.removePrefix("by")?.trim(),
                    coverUrl = imgUrl(imgEl),
                    url = resolveUrl(link.attr("href")),
    category = ScraperCategory.MANHWA,
                )
            }
        }
        return doc.select("a[href*=/serie/], a[href*=/manga/]").mapNotNull { link ->
            val imgEl = link.selectFirst("img")
            ScrapedSeries(
                title = link.attr("title").ifBlank { link.text() }.trim(),
                author = null,
                coverUrl = imgUrl(imgEl),
                url = resolveUrl(link.attr("href")),
                category = ScraperCategory.MANHWA,
            )
        }
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> {
        // Toonily answers roughly every other request with a Cloudflare
        // interstitial, so one failed attempt is not evidence the page is empty.
        val doc = fetchPastCloudflare(seriesUrl, tag = "-chapters", minContent = 2000) ?: return emptyList()
        val links = doc.select("li.wp-manga-chapter a, ul.main li a, ul.chapter-list a, li.chapter a")
        if (links.isEmpty()) return emptyList()
        return links.mapNotNull { link ->
            val href = link.attr("href").ifBlank { return@mapNotNull null }
            ScrapedChapter(
                title = link.text(),
                url = resolveUrl(href),
                number = extractChapterNumber(link.text()),
            )
        }
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent {
        val doc = fetchPastCloudflare(chapterUrl, tag = "-page", minContent = 1000)
            ?: return ScrapedChapterContent.Images(emptyList())
        val imgs = doc.select(".reading-content img, #reader img, img[src*=.jpg], img[src*=.png], img[src*=.webp]")
        val urls = imgs.mapNotNull { el -> imgUrl(el) }
        if (urls.isNotEmpty()) return ScrapedChapterContent.Images(urls)
        val contentEl = doc.selectFirst("#chaptercontent, .chapter-content, .content, article, .entry-content")
        if (contentEl != null) {
            return ScrapedChapterContent.Text(
                title = doc.selectFirst("h1, h2")?.text(),
                body = extractText(contentEl.outerHtml()),
            )
        }
        return ScrapedChapterContent.Images(emptyList())
    }
}
