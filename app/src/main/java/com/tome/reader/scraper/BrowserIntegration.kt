package com.tome.reader.scraper

import java.net.URI

object BrowserIntegration {
    private val scraperHosts = mapOf(
        "mangadex.org" to "mangadex",
        "weebcentral.com" to "weebcentral",
        "asurascans.com" to "asurascans",
        "asuracomic.net" to "asurascans",
        "toonily.com" to "toonily",
        "flamecomics.xyz" to "flamescans",
        "flamecomics.com" to "flamescans",
        "webtoons.com" to "webtoon",
        "mangaplus.shueisha.co.jp" to "mangaplus",
        "manhwaden.com" to "manhwaden",
    )

    private fun sourceId(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        return scraperHosts[host]
    }

    fun detectScraper(url: String, registry: ScraperRegistry): Scraper? =
        sourceId(url)?.let(registry::get)?.takeIf { it.isAvailable }

    fun actionLabel(scraper: Scraper): String =
        if (scraper.isBrowserOnly) "Open ${scraper.name}" else "Search ${scraper.name}"

    fun isScraperSupported(url: String): Boolean = sourceId(url) != null
}
