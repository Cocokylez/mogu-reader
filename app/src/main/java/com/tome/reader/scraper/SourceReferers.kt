package com.tome.reader.scraper

/**
 * The Referer each source's image CDN expects.
 *
 * Cover art and page images are usually served from a different host than the
 * site itself, and most of those CDNs reject a request whose Referer is not the
 * site. Two separate bugs came from getting this wrong: WEBTOON covers 403'd
 * because Coil sends no Referer at all, and Toonily chapters failed with "image
 * stream blocked by this source" because the page downloader derived the Referer
 * from the image URL's own directory rather than the site it belongs to.
 *
 * Matched on host suffix so CDN shards (i5./pic1.mangapicgallery, webtoon-phinf /
 * swebtoon-phinf, ...) are all covered by one entry.
 */
object SourceReferers {

    private val byHostSuffix: List<Pair<String, String>> = listOf(
        // WEBTOON
        "pstatic.net" to "https://www.webtoons.com/",
        "webtoons.com" to "https://www.webtoons.com/",
        // ManhwaDen serves its chapter pages from remanhwa.me
        "manhwaden.com" to "https://www.manhwaden.com/",
        "remanhwa.me" to "https://www.manhwaden.com/",
        // Toonily (pages are served from tnlycdn, which 403s without the site)
        "toonily.com" to "https://toonily.com/",
        "tnlycdn.com" to "https://toonily.com/",
        // Mangago (covers and pages both live on mangapicgallery)
        "mangapicgallery.com" to "https://www.mangago.me/",
        "mangago.me" to "https://www.mangago.me/",
        // Asura Scans
        "asuracomic.net" to "https://asuracomic.net/",
        "asurascans.com" to "https://asuracomic.net/",
        // Flame Comics
        "flamecomics.xyz" to "https://flamecomics.xyz/",
        "flamecomics.com" to "https://flamecomics.xyz/",
        // Demonic Scans serves its art from readermc.org
        "demonicscans.org" to "https://demonicscans.org/",
        "readermc.org" to "https://demonicscans.org/",
        // MangaDex
        "mangadex.org" to "https://mangadex.org/",
        "mangadex.network" to "https://mangadex.org/",
        // WeebCentral
        "weebcentral.com" to "https://weebcentral.com/",
        "lowee.us" to "https://weebcentral.com/",
        "planeptune.us" to "https://weebcentral.com/",
        // MANGA Plus
        "tokyo-cdn.com" to "https://mangaplus.shueisha.co.jp/",
        "shueisha.co.jp" to "https://mangaplus.shueisha.co.jp/",
        // Azuki / VIZ
        "azuki.co" to "https://www.azuki.co/",
        "viz.com" to "https://www.viz.com/",
        "cloudfront.net" to "https://www.viz.com/",
    )

    /**
     * Image hosts learned at runtime.
     *
     * A source's pages often live on a CDN whose name gives no hint of the site
     * (Toonily serves from data.tnlycdn.com), and those hosts 403 anything without
     * the site as Referer. Rather than needing every CDN listed up front, the
     * reader tells us which site a chapter's images came from, so any source is
     * covered the first time one of its chapters loads.
     */
    private val learned = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Associates the hosts serving [pageUrls] with the source they came from. */
    fun learn(pageUrls: List<String>, siteUrl: String) {
        if (siteUrl.isBlank()) return
        val site = if (siteUrl.endsWith("/")) siteUrl else "$siteUrl/"
        for (url in pageUrls) {
            val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: continue
            if (host.isBlank()) continue
            if (forHost(host) != null) continue
            learned[host] = site
        }
    }

    /** The Referer for a host, or null when nothing specific is known. */
    fun forHost(host: String): String? {
        val clean = host.lowercase()
        return byHostSuffix.firstOrNull { (suffix, _) ->
            clean == suffix || clean.endsWith(".$suffix")
        }?.second ?: learned[clean]
    }

    /**
     * The Referer for an image URL. Falls back to the image's own origin, which is
     * harmless for hosts that do not check and correct for the ones that only
     * reject a missing header.
     */
    fun forUrl(url: String): String? {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        return forHost(host) ?: runCatching {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}/"
        }.getOrNull()
    }
}
