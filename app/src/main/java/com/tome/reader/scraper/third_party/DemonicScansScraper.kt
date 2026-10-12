package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperSearchPage
import com.tome.reader.scraper.generic.HtmlScraper
import org.jsoup.nodes.Document

class DemonicScansScraper : HtmlScraper(
    id = "demonicscans",
    name = "Demonic Scans",
    baseUrl = "https://demonicscans.org",
    category = ScraperCategory.MANGA,
) {
    /**
     * Catalogue paging.
     *
     * This source has no browse API, so fetchLatest previously fell back to
     * running throwaway searches, which returned the same 20 titles forever and
     * made scrolling stop after one screen. lastupdates.php?list=N is a real
     * paged listing: 40 cards a page, different titles on each.
     */
    override suspend fun fetchLatest(): List<ScrapedSeries> = fetchLatestPage(1)

    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> {
        if (page < 1) return emptyList()
        val doc = fetchDocSafe("$baseUrl/lastupdates.php?list=$page", tag = "-latest$page")
            ?: return emptyList()
        val items = doc.select(".updates-element, .thumb").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val title = link.attr("title").trim()
                .ifBlank { link.selectFirst("img")?.attr("alt")?.trim()?.trimEnd(';').orEmpty() }
                .ifBlank { card.selectFirst("h2")?.text()?.trim().orEmpty() }
            if (title.isBlank()) return@mapNotNull null
            ScrapedSeries(
                title = title,
                author = null,
                coverUrl = imgUrl(link.selectFirst("img") ?: card.selectFirst("img")),
                url = resolveUrl(link.attr("href")),
                category = ScraperCategory.MANGA,
            )
        }.distinctBy { it.url.lowercase() }
        diag("  lastupdates page $page returned items=${items.size}")
        return items
    }

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage {
        if (query.isBlank()) return ScraperSearchPage(emptyList(), hasMore = false)
        val doc = fetchDocSafe("$baseUrl/search.php?manga=${encode(query)}")
            ?: return ScraperSearchPage(emptyList(), false)
        val items = doc.select("a[href*=/manga/]").mapNotNull { link ->
            val titleEl = link.selectFirst("div.flex.flex-col > div")
            val title = titleEl?.text()?.trim()
            if (title.isNullOrBlank()) return@mapNotNull null
            val imgEl = link.selectFirst("img.search-thumb")
            ScrapedSeries(
                title = title,
                author = null,
                coverUrl = imgUrl(imgEl),
                url = resolveUrl(link.attr("href")),
                category = ScraperCategory.MANGA,
            )
        }
        return ScraperSearchPage(items, hasMore = false)
    }

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> {
        val doc = fetchDocSafe(seriesUrl) ?: return emptyList()
        val links = doc.select("#chapters-list li a.chplinks")
        if (links.isEmpty()) return emptyList()
        return links.mapNotNull { link ->
            val href = link.attr("href").ifBlank { return@mapNotNull null }
            val dateEl = link.selectFirst("span[style*=float]")
            ScrapedChapter(
                title = link.ownText().trim(),
                url = resolveUrl(href),
                number = extractChapterNumber(link.text()),
                releaseDate = dateEl?.text()?.trim(),
            )
        }
    }

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent {
        val doc = fetchDocSafe(chapterUrl) ?: return ScrapedChapterContent.Images(emptyList())
        val imgs = doc.select("img.imgholder")
        val urls = imgs.mapNotNull { el ->
            val src = el.attr("src").ifBlank { el.attr("data-src") }
            src.takeIf { it.isNotBlank() && !it.contains("/img/") }
        }
        return ScrapedChapterContent.Images(urls)
    }
}
