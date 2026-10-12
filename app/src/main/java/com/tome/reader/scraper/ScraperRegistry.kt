package com.tome.reader.scraper

import com.tome.reader.scraper.third_party.*

/**
 * Every source here can be browsed *and read* inside the app.
 *
 * That is the whole rule. A card in Browse is a promise that tapping it leads to
 * reading, so a source that can only hand the reader off to a website does not
 * belong in the feed -- an "open the website" row is not content.
 *
 * Removed because reading in-app is impossible, verified against the live sites:
 *  - Azuki and VIZ: catalogues are public and parse fine, but chapters are drawn
 *    by a client-side, session-bound reader (Azuki's chapter page is an 8KB shell
 *    with a CSRF token and no page list). Getting pages out means defeating a
 *    licensed publisher's own viewer, which this app will not do.
 *  - Comikey: 403s this client with both full and minimal headers, from an IP
 *    where a desktop client succeeds -- TLS fingerprinting.
 *  - AquaReader: Cloudflare on every attempt.
 *  - Lezhin: its listing <img> tags carry only alt, no src/srcset, so not even
 *    covers can be read from the HTML.
 *  - Manta, Tapas, Tappytoon: client-rendered; Tappytoon only exposes a private
 *    API behind a page-embedded token.
 *  - Mangago: catalogue and chapter lists parse fine, but a chapter's page URLs
 *    are an encrypted `var imgsrcs` blob that only its own JS can decode. That is
 *    a deliberate anti-scraping measure, and writing a deobfuscator for it is not
 *    something this app does, so the source cannot deliver on the Browse promise.
 * Their scrapers are kept where already written, so re-registering is a one-liner
 * if a site changes. The in-app browser (globe button) still reaches any of them.
 *
 * Deleted entirely as dead sites: ComicK (API gone), Bato.To (all mirrors fail),
 * MangaFire (SPA shell), Manganato and MangaKakalot (domains parked, both serving
 * the same "Spin the Wheel" placeholder).
 */
class ScraperRegistry {
    private val scrapers: Map<String, Scraper> = listOf(
        MangaDexScraper(),
        WeebCentralScraper(),
        AsuraScansScraper(),
        FlameScansScraper(),
        WebtoonScraper(),
        MangaPlusScraper(),
        ToonilyScraper(),
        DemonicScansScraper(),
        ManhwaDenScraper(),
    ).associateBy { it.id }

    fun get(id: String): Scraper? = scrapers[id]
    fun all(): List<Scraper> = scrapers.values.sortedBy { it.name.lowercase() }
    fun available(): List<Scraper> = all().filter { it.isAvailable && it.category.isSupported }
}
