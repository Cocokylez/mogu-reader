package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.BrowserIntegration
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScraperRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ComicSourceTest {
    /**
     * Replaces an earlier test that asserted AquaReader was registered as an
     * "honest browser entry". Browser-only entries are gone: a card in Browse
     * promises that tapping it leads to reading, so the registry now holds only
     * sources that can be read in-app. That is the invariant worth guarding.
     */
    @Test fun everyRegisteredSourceCanBeReadInApp() = runBlocking {
        val registry = ScraperRegistry()
        assertTrue(registry.all().all { it.category.isSupported })
        listOf("novelupdates", "novelbin", "wuxiaworld", "gutenberg", "royalroad", "libgen", "annasarchive", "globalcomix").forEach { assertNull(registry.get(it)) }

        val browserOnly = registry.all().filter { it.isBrowserOnly }.map { it.id }
        assertEquals("sources that cannot be read in-app must not be registered", emptyList<String>(), browserOnly)

        // Sources dropped because reading in-app is impossible stay dropped.
        listOf("azuki", "viz", "comikey", "aquareader", "lezhin", "manta", "tapas", "tappytoon", "mangago")
            .forEach { assertNull("$it should not be registered", registry.get(it)) }

        assertEquals("mangadex", BrowserIntegration.detectScraper("https://WWW.MANGADEX.ORG/title/example", registry)?.id)
        assertFalse(BrowserIntegration.isScraperSupported("https://mangadex.org.evil.example"))
        assertFalse(BrowserIntegration.isScraperSupported("https://example.org/path/mangadex.org"))
        assertFalse(BrowserIntegration.isScraperSupported("https://novelbin.com/"))
    }

    @Test fun asuraOnlyReturnsChaptersBelongingToSelectedSeries() = runBlocking {
        val scraper = AsuraScansScraper { url -> Jsoup.parse("""
            <a href="/comics/alpha/chapter/10">Chapter 10</a>
            <a href="/comics/beta/chapter/2">Recommendation</a>
            <a href="/comics/alpha/chapter/1">Chapter 1</a>
            <a href="https://evil.example/comics/alpha/chapter/3">Wrong host</a>
            <a href="/comics/alpha/chapter/1">Duplicate</a>
        """, url) }
        val chapters = scraper.fetchChapters("https://asurascans.com/comics/alpha")
        assertEquals(listOf(1.0, 10.0), chapters.map { it.number })
    }

    @Test fun asuraUsesCurrentSearchEndpointWithoutUnrelatedFallback() = runBlocking {
        var requested = ""
        val props = JSONObject().put("initialSeries", JSONArray().put(1).put(JSONArray()))
            .put("initialTotalPages", JSONArray().put(0).put(1))
        val scraper = AsuraScansScraper { url ->
            requested = url
            Jsoup.parse("<astro-island component-url='/BrowseFilters.js' props='${props}'></astro-island><p>No results</p>", url)
        }
        assertTrue(scraper.searchPage("missing series", 1).items.isEmpty())
        assertTrue(requested.contains("/browse/comics?type=manhwa"))
        assertTrue(requested.contains("q=missing+series"))
    }

    @Test fun flameCatalogueFiltersNovelsAndManhuaAndCachesAcrossPages() = runBlocking {
        var calls = 0
        val data = JSONArray()
        for (i in 1..27) data.put(JSONObject().put("series_id", i).put("title", "Title $i").put("type", if (i == 26) "Novel" else if (i == 27) "Manhua" else "Manhwa").put("last_edit", i))
        val props = JSONObject().put("props", JSONObject().put("pageProps", JSONObject().put("series", data)))
        val scraper = FlameScansScraper { url -> calls++; Jsoup.parse("<script id='__NEXT_DATA__' type='application/json'>$props</script>", url) }
        val first = scraper.searchPage("", 1)
        val second = scraper.searchPage("", 2)
        assertEquals(24, first.items.size)
        assertEquals(1, second.items.size)
        assertEquals(1, calls)
        assertTrue(first.hasMore)
        assertFalse(second.hasMore)
        assertEquals("Title 25", first.items.first().title)
    }

    @Test fun flamePagesExcludeCoverAndOtherChapterImages() = runBlocking {
        val scraper = FlameScansScraper { url -> Jsoup.parse("""
            <img src="https://cdn.flamecomics.xyz/uploads/images/series/1/cover.webp">
            <img src="https://cdn.flamecomics.xyz/uploads/images/series/1/abc/01.webp">
            <img data-src="https://cdn.flamecomics.xyz/uploads/images/series/1/abc/02.webp">
            <img src="https://cdn.flamecomics.xyz/uploads/images/series/1/other/01.webp">
        """, url) }
        val pages = scraper.fetchChapter("https://flamecomics.xyz/series/1/abc") as ScrapedChapterContent.Images
        assertEquals(2, pages.pageUrls.size)
        assertTrue(pages.pageUrls.all { "/1/abc/" in it })
    }

    @Test fun weebPaginationUsesActualFragmentPageSize() = runBlocking {
        var requested = ""
        val scraper = WeebCentralScraper { url -> requested = url; Jsoup.parse("<p>No results</p>", url) }
        assertTrue(scraper.searchPage("test", 2).items.isEmpty())
        assertTrue(requested.contains("offset=32"))
        assertTrue(requested.contains("included_type=Manga&included_type=Manhwa"))
    }

    @Test fun readerDoesNotTreatPaywallTextAsImages() = runBlocking {
        val scraper = AsuraScansScraper { url -> Jsoup.parse("<main>Unlock this premium chapter</main>", url) }
        val error = runCatching { scraper.fetchChapter("https://asurascans.com/comics/alpha/chapter/3") }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("requires access"))
    }
}
