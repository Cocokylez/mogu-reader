package com.tome.reader.scraper.generic

import android.util.Log
import com.tome.reader.data.source.HtmlText
import com.tome.reader.scraper.HttpClient
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import java.io.IOException
import java.net.URLEncoder

abstract class HtmlScraper(
    override val id: String,
    override val name: String,
    override val baseUrl: String,
    override val category: ScraperCategory,
) : Scraper {

    override open val isAvailable: Boolean = true

    var lastError: String? = null
        private set

    val diagnostics: StringBuilder = StringBuilder()

    override val diagnosticsText: String get() = diagnostics.toString().trim()
    override val lastErrorText: String? get() = lastError

    protected fun diag(msg: String) {
        diagnostics.appendLine(msg)
        Log.d("MoguScraper", "DIAG [${name}] $msg")
    }

    override open suspend fun fetchLatest(): List<ScrapedSeries> = withContext(Dispatchers.IO) {
        diagnostics.clear()
        for (fallback in listOf("the", "a")) {
            try {
                diagnostics.appendLine("  Attempt: searchPage(\"$fallback\")")
                val page = searchPage(fallback, 1)
                diag("  searchPage($fallback) returned items=${page.items.size}")
                if (page.items.isNotEmpty()) {
                    Log.d("MoguScraper", "fetchLatest via search [${name}] q=$fallback items=${page.items.size}")
                    return@withContext page.items.distinctBy { it.url.lowercase() }.take(30)
                }
            } catch (e: Exception) {
                diag("  searchPage($fallback) EXCEPTION: ${e.javaClass.simpleName} ${e.message?.take(80)}")
                Log.w("MoguScraper", "fetchLatest q=$fallback [${name}] FAIL: ${e.javaClass.simpleName} ${e.message?.take(100)}")
            }
        }
        diag("  Attempt: homepage $baseUrl")
        val doc = fetchDocSafe(baseUrl, tag = "-latest")
        if (doc == null) {
            diag("  homepage fetch FAILED (fetchDocSafe returned null)")
            return@withContext emptyList()
        }
        diag("  homepage title=\"${doc.title().take(80)}\" bodyLen=${doc.text().length}")
        val hrefSelectors = "a[href*=/manga/], a[href*=/series/], a[href*=/comic/], " +
            "a[href*=/manhua/], a[href*=/manhwa/], a[href*=/serie/], a[href*=/title/]"
        var links = doc.select(hrefSelectors)
        diag("  selector(href): ${links.size} matches")
        if (links.isEmpty()) {
            links = doc.select("a[href] img").mapNotNull { img ->
                val parent = img.parent()?.let { p ->
                    if (p.tagName() == "a") p else p.selectFirst("a[href]")
                } ?: return@mapNotNull null
                parent
            }.let { org.jsoup.select.Elements(it) }
            diag("  selector(a>img): ${links.size} matches")
        }
        if (links.isEmpty()) {
            diag("  No links found on homepage — all selectors matched 0")
            val allLinks = doc.select("a[href]").take(30)
            diag("  Top ${allLinks.size} links found on page:")
            for (a in allLinks) {
                val h = a.attr("href").take(60)
                val t = a.text().take(40).ifBlank { a.attr("title").take(40) }.ifBlank { a.attr("alt").take(40) }
                val hasImg = a.selectFirst("img") != null
                diag("    href=$h text=\"$t\" img=$hasImg class=\"${a.className().take(30)}\"")
            }
            return@withContext emptyList()
        }
        val results = links.mapNotNull { link ->
            val href = link.attr("href")
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:")) return@mapNotNull null
            if (
                href.contains("/novel/", ignoreCase = true) ||
                href.contains("/fiction/", ignoreCase = true) ||
                href.contains("/book/", ignoreCase = true)
            ) return@mapNotNull null
            val img = link.selectFirst("img")
            ScrapedSeries(
                title = link.attr("title").ifBlank { link.text() }.trim(),
                author = null,
                coverUrl = imgUrl(img),
                url = resolveUrl(href),
                category = category,
            )
        }
        diag("  After filtering: ${results.size} series from ${links.size} links")
        if (results.isEmpty()) {
            for (a in links.take(10)) {
                val h = a.attr("href").take(60)
                val t = linkTextForDiag(a)
                diag("  filtered: href=$h text=\"$t\"")
            }
        }
        results.distinctBy { it.url.lowercase() }.take(30)
    }

    /**
     * Overridden by sources that are served a different document on a phone UA.
     * Null keeps the pinned mobile UA, which Cloudflare cookies are bound to.
     */
    protected open val requestUserAgent: String? = null

    protected suspend fun fetchDoc(url: String, referer: String? = null): Document =
        withContext(Dispatchers.IO) {
            HttpClient.fetchDocument(url, referer, requestUserAgent)
        }

    /**
     * Fetches past an *intermittent* Cloudflare challenge.
     *
     * Toonily and Mangago both answer roughly every other request with the
     * interstitial and the rest with the real page, so a single failed attempt
     * says nothing about whether the source works. Retrying a few times is the
     * difference between "no chapters" and a full listing.
     */
    protected suspend fun fetchPastCloudflare(
        url: String,
        tag: String = "",
        attempts: Int = 3,
        minContent: Int = 400,
    ): Document? {
        repeat(attempts) { attempt ->
            val doc = fetchDocSafe(url, tag = "$tag-$attempt")
            if (doc != null && isValidPage(doc, minContent)) return doc
            diag("  attempt ${attempt + 1}/$attempts blocked or too small")
        }
        return null
    }

    protected suspend fun fetchDocSafe(url: String, referer: String? = null, tag: String = ""): Document? =
        withContext(Dispatchers.IO) {
            try {
                val doc = HttpClient.fetchDocument(url, referer, requestUserAgent)
                val title = doc.title().take(80)
                val bodyLen = doc.text().length
                val htmlSample = doc.outerHtml().take(800)
                Log.d("MoguScraper", "fetchDocSafe OK [${name}]${tag} url=$url title=$title bodyLen=$bodyLen")
                diag("  fetchDocSafe[${tag}] OK baseUrl=$url title=\"$title\" bodyLen=$bodyLen")
                if (title.isBlank() || bodyLen < 500) {
                    diag("  htmlSample: ${htmlSample.take(500)}")
                }
                lastError = null
                doc
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message?.take(100)}"
                Log.w("MoguScraper", "fetchDocSafe FAIL [${name}]${tag} url=$url $lastError")
                diag("  fetchDocSafe[${tag}] FAIL $lastError")
                null
            }
        }

    protected fun resolveUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = baseUrl.trimEnd('/')
        return when {
            path.startsWith("/") -> "$base$path"
            else -> "$base/$path"
        }
    }

    protected fun extractText(html: String): String = HtmlText.extract(html)

    protected fun encode(query: String): String = URLEncoder.encode(query, "UTF-8")

    protected fun imgUrl(element: Element?): String? {
        if (element == null) return null
        val candidates = listOf(
            "data-src",
            "data-lazy-src",
            "data-original",
            "data-cfsrc",
            "data-url",
            "src",
            "href",
            "xlink:href",
            "data-srcset",
            "srcset",
        )
        return candidates.asSequence()
            .mapNotNull { attr ->
                val raw = element.attr(attr).trim()
                if (attr.endsWith("srcset", ignoreCase = true)) {
                    srcFromSet(raw)
                } else {
                    element.absUrl(attr).ifBlank { raw }
                }
            }
            .firstNotNullOfOrNull { imgUrl(it) }
    }

    protected fun imgUrl(raw: String?): String? {
        val value = raw
            ?.trim()
            ?.removePrefix("url(")
            ?.removeSuffix(")")
            ?.trim('\'', '"', ' ')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("about:", ignoreCase = true) ||
            value.contains("placeholder", ignoreCase = true) ||
            value.contains("blank.", ignoreCase = true)
        ) return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$baseUrl$value"
            value.startsWith("http://") -> value.replace("http://", "https://")
            else -> value
        }
    }

    private fun srcFromSet(value: String): String? {
        return value.split(',')
            .mapNotNull { part -> part.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } }
            .lastOrNull()
    }

    /**
     * Whether a fetched document is usable content rather than a challenge page.
     *
     * "enable javascript" is deliberately not a rejection reason: every SPA ships
     * that line in a <noscript>, and image-only chapter pages carry so little text
     * that any size-based guess rejects them too. HttpClient already refuses real
     * Cloudflare interstitials structurally, so this only guards against a page
     * that came back empty.
     */
    protected fun isValidPage(doc: Document?, minContent: Int = 50): Boolean {
        if (doc == null) return false
        val text = doc.text()
        if (doc.title().trim().lowercase().startsWith("just a moment")) return false
        // An image-only page has almost no text but plenty of markup, so accept
        // either as evidence the fetch produced something real.
        return text.length > minContent || doc.select("img").size >= 3
    }

    protected fun fetchDocWithFallback(urls: List<String>): Document? {
        for (url in urls) {
            try {
                val doc = HttpClient.fetchDocument(url)
                if (isValidPage(doc)) return doc
            } catch (_: Exception) {}
        }
        return null
    }

    private fun linkTextForDiag(a: org.jsoup.nodes.Element): String {
        return a.text().take(40).ifBlank { a.attr("title").take(40) }
            .ifBlank { a.attr("alt").take(40) }
            .ifBlank { a.selectFirst("img")?.attr("alt")?.take(40) ?: "" }
    }

    protected fun extractChapterNumber(text: String): Double? {
        return text.substringAfter("Chapter ")
            .substringAfter("Ch.")
            .substringAfter("chapter ")
            .substringAfter("Ch ")
            .substringBefore(" ").substringBefore(":").substringBefore("-")
            .trim().toDoubleOrNull()
    }
}
