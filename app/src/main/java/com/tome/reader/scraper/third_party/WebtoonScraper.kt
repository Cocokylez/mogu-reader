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

class WebtoonScraper : HtmlScraper(
    id = "webtoon",
    name = "WEBTOON",
    baseUrl = "https://www.webtoons.com",
    category = ScraperCategory.MANHWA,
) {
    /**
     * Catalogue paging. /en/genre returns the entire listing (~600 titles) in one
     * response, so pages are slices of it and scrolling keeps producing new
     * series instead of stopping after the first batch.
     */
    override suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        if (page < 1) return@withContext emptyList()
        val all = cachedGenreListing()
        all.drop((page - 1) * CATALOGUE_PAGE).take(CATALOGUE_PAGE)
    }

    private var genreCache: List<ScrapedSeries> = emptyList()
    private var genreCachedAt = 0L

    private suspend fun cachedGenreListing(): List<ScrapedSeries> {
        val fresh = genreCache.isNotEmpty() &&
            (System.nanoTime() - genreCachedAt) < 600_000_000_000L // 10 minutes
        if (fresh) return genreCache
        val doc = fetchDocSafe("$baseUrl/en/genre", tag = "-genre") ?: return genreCache
        val items = parseSeriesCards(doc).distinctBy { it.url.lowercase() }
        if (items.isNotEmpty()) {
            genreCache = items
            genreCachedAt = System.nanoTime()
        }
        return genreCache
    }

    override suspend fun fetchLatest(): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        diagnostics.clear()
        // /en/genre is server-rendered and lists every series. The previous
        // approach fired five throwaway searches first ("the", "a", "love"…),
        // all of which now return nothing — five wasted round trips per load.
        val genreDoc = fetchDocSafe("$baseUrl/en/genre", tag = "-genre")
        if (genreDoc != null) {
            val genreItems = parseSeriesCards(genreDoc)
            diag("  /en/genre returned items=${genreItems.size}")
            if (genreItems.isNotEmpty()) {
                return@withContext genreItems.distinctBy { it.url.lowercase() }.take(30)
            }
        }
        diag("  Fallback: scraping homepage trending/popular series")
        val doc = fetchDocSafe("$baseUrl/en", tag = "-homepage")
            ?: return@withContext emptyList()
        val hrefSelectors = "a._trending_title_a, a._popular_title_a, a[href*=/en/] img"
        var links = doc.select(hrefSelectors)
        diag("  homepage selector($hrefSelectors): ${links.size} matches")
        if (links.isEmpty()) {
            links = doc.select("a[href]").take(30).let { org.jsoup.select.Elements(it) }
            diag("  Top links on page:")
            for (a in links) {
                val h = a.attr("href").take(60)
                val t = a.text().take(40).ifBlank { a.attr("title").take(40) }
                val hasImg = a.selectFirst("img") != null
                diag("    href=$h text=\"$t\" img=$hasImg class=\"${a.className().take(30)}\"")
            }
            return@withContext emptyList()
        }
        val results = links.mapNotNull { link ->
            val a = if (link.tagName() == "img") {
                val p = link.parent() ?: return@mapNotNull null
                if (p.tagName() == "a") p else return@mapNotNull null
            } else link
            val href = a.attr("href")
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:")) return@mapNotNull null
            val img = a.selectFirst("img")
            ScrapedSeries(
                title = a.attr("title").ifBlank { a.text() }.trim(),
                author = null,
                coverUrl = imgUrl(img),
                url = resolveUrl(href),
                category = category,
            )
        }
        diag("  extracted ${results.size} series from homepage")
        results.distinctBy { it.url.lowercase() }.take(30)
    }

    override suspend fun searchPage(query: String, page: Int): ScraperSearchPage {
        if (query.isBlank()) return ScraperSearchPage(emptyList(), hasMore = false)
        val p = page.coerceAtLeast(1)
        val doc = fetchDocSafe("$baseUrl/en/search?keyword=${encode(query)}&page=$p")
            ?: return ScraperSearchPage(emptyList(), false)
        val items = parseSeriesCards(doc)
        return ScraperSearchPage(items, false)
    }

    private fun parseSeriesCards(doc: Document): List<ScrapedSeries> {
        // Current markup (verified against the live site):
        //   li.item > a.link._titleItem[href*=title_no] > img + strong.title
        val titleLinks = doc.select("a[href*=title_no]")
        if (titleLinks.isNotEmpty()) {
            val parsed = titleLinks.mapNotNull { link ->
                val href = link.attr("href")
                if (href.isBlank()) return@mapNotNull null
                val card = link.parent() ?: link
                val title = (
                    link.selectFirst("strong.title, .title")
                        ?: card.selectFirst("strong.title, .title")
                    )?.text()?.trim().orEmpty()
                if (title.isBlank()) return@mapNotNull null
                ScrapedSeries(
                    title = title,
                    author = card.selectFirst(".author, .author_info")?.text()?.trim(),
                    coverUrl = imgUrl(link.selectFirst("img") ?: card.selectFirst("img")),
                    url = resolveUrl(href),
                    category = ScraperCategory.MANHWA,
                )
            }.distinctBy { it.url.lowercase() }
            if (parsed.isNotEmpty()) return parsed
        }
        val cards = doc.select(".card_item, .challenge_card_item, ul#_searchResult li, a._trending_title_a, a._popular_title_a")
        if (cards.isNotEmpty()) {
            return cards.mapNotNull { card ->
                val link = when {
                    card.tagName() == "a" -> card
                    card.selectFirst("a[href]") != null -> card.selectFirst("a[href]")!!
                    else -> return@mapNotNull null
                }
                val titleEl = card.selectFirst(".subj span, .subj, .title, h3, .info .title")
                val authorEl = card.selectFirst(".author, .author_info, .info .author")
                val imgEl = card.selectFirst("img")
                ScrapedSeries(
                    title = titleEl?.text()?.trim() ?: link.attr("title").ifBlank { link.text() }.trim(),
                    author = authorEl?.text()?.trim(),
                    coverUrl = imgUrl(imgEl),
                    url = resolveUrl(link.attr("href")),
                    category = ScraperCategory.MANHWA,
                )
            }
        }
        return doc.select("a[href] img").mapNotNull { img ->
            val parent = img.parent() ?: return@mapNotNull null
            val link = if (parent.tagName() == "a") parent else parent.selectFirst("a[href]") ?: return@mapNotNull null
            val href = link.attr("href")
            if (href.isBlank() || href.startsWith("#")) return@mapNotNull null
            ScrapedSeries(
                title = link.attr("title").ifBlank { link.text() }.trim(),
                author = null,
                coverUrl = imgUrl(img),
                url = resolveUrl(href),
                category = ScraperCategory.MANHWA,
            )
        }
    }

    /**
     * The episode list only exists on the desktop document. On the app's mobile UA
     * the same URL returns a 30KB shell with no episodes, which is why every
     * WEBTOON series opened with an empty chapter list.
     */
    override val requestUserAgent: String = HttpClient.DESKTOP_USER_AGENT

    override suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter> {
        val chapters = mutableListOf<ScrapedChapter>()
        val seen = mutableSetOf<String>()
        // The list is paged 10 at a time; walk until a page adds nothing new.
        for (page in 1..MAX_EPISODE_PAGES) {
            val url = if (page == 1) seriesUrl else appendPage(seriesUrl, page)
            val doc = fetchDocSafe(url, tag = "-episodes$page") ?: break
            val rows = doc.select("ul#_listUl li a, li._episodeItem a, a.detail_list_link")
            if (rows.isEmpty()) break
            var added = 0
            for (link in rows) {
                val href = link.attr("href")
                if (href.isBlank() || !href.contains("viewer")) continue
                val resolved = resolveUrl(href)
                if (!seen.add(resolved.lowercase())) continue
                val label = link.selectFirst("span.subj")?.text()?.trim()
                    .orEmpty()
                    .ifBlank { link.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
                    .ifBlank { link.text().trim() }
                if (label.isBlank()) continue
                chapters += ScrapedChapter(
                    title = label,
                    url = resolved,
                    number = extractChapterNumber(label),
                    releaseDate = link.selectFirst("span.date")?.text()?.trim(),
                )
                added++
            }
            diag("  episodes page $page added=$added total=${chapters.size}")
            if (added == 0) break
        }
        // Newest-first on the site; the reader expects oldest-first.
        return chapters.reversed()
    }

    private fun appendPage(seriesUrl: String, page: Int): String =
        if (seriesUrl.contains("?")) "$seriesUrl&page=$page" else "$seriesUrl?page=$page"

    override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent {
        val doc = fetchDocSafe(chapterUrl) ?: return ScrapedChapterContent.Images(emptyList())
        // Viewer pages carry a transparent 1x1 in src and the real page in data-url.
        val imgs = doc.select("#_imageList img._images, #_imageList img, .viewer_img img[data-url], img[data-url]")
        val urls = imgs.mapNotNull { el -> imgUrl(el) }
        if (urls.isNotEmpty()) return ScrapedChapterContent.Images(urls)
        val contentEl = doc.selectFirst("#_episodeContentView, .viewer_content, .content, article, #content")
        if (contentEl != null) {
            return ScrapedChapterContent.Text(
                title = doc.selectFirst("h1, h2")?.text(),
                body = extractText(contentEl.outerHtml()),
            )
        }
        return ScrapedChapterContent.Images(emptyList())
    }

    private companion object {
        /** 10 episodes a page; enough for the longest series without hammering. */
        const val MAX_EPISODE_PAGES = 40

        /** Slice size for browsing the single-shot genre listing. */
        const val CATALOGUE_PAGE = 30
    }
}
