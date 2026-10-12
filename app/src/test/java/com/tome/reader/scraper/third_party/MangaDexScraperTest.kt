package com.tome.reader.scraper.third_party

import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScraperCategory
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MangaDexScraperTest {
    private val mangaId = "00000000-0000-0000-0000-000000000001"
    private val chapterId = "00000000-0000-0000-0000-000000000002"

    @Test fun acceptsPublicLinksAndRetainsUuidKeys() {
        val scraper = MangaDexScraper { error("No network expected") }
        assertEquals(mangaId, scraper.identifier("https://mangadex.org/title/$mangaId/a-title?tab=chapters", "title"))
        assertEquals(chapterId, scraper.identifier(chapterId, "chapter"))
        assertThrows(IOException::class.java) { scraper.identifier("https://mangadex.org.evil.example/title/$mangaId", "title") }
        assertThrows(IOException::class.java) { scraper.identifier("https://mangadex.org/chapter/$chapterId", "title") }
    }

    @Test fun catalogueFiltersScopeUsesEnglishTitleAndSmallCover() = runBlocking {
        var requested = ""
        val attrs = JSONObject().put("originalLanguage", "ko").put("title", JSONObject().put("ko-ro", "Romanized"))
            .put("altTitles", JSONArray().put(JSONObject().put("en", "English title")))
        val manga = JSONObject().put("id", mangaId).put("attributes", attrs).put("relationships", JSONArray()
            .put(JSONObject().put("type", "cover_art").put("attributes", JSONObject().put("fileName", "cover.jpg"))))
        val novelLanguage = JSONObject().put("id", chapterId).put("attributes", JSONObject().put("originalLanguage", "en"))
        val scraper = MangaDexScraper { requested = it; JSONObject().put("data", JSONArray().put(manga).put(novelLanguage)).put("total", 30) }
        val result = scraper.searchPage("test & title", 1)
        assertEquals(1, result.items.size)
        assertEquals("English title", result.items.single().title)
        assertEquals(ScraperCategory.MANHWA, result.items.single().category)
        assertTrue(result.items.single().coverUrl!!.endsWith("cover.jpg.256.jpg"))
        assertTrue(requested.contains("originalLanguage[]=ja&originalLanguage[]=ko"))
        assertTrue(requested.contains("title=test+%26+title"))
        assertTrue(result.hasMore)
    }

    @Test fun chapterFeedContinuesBeyond500AndFiltersUnreadableReleases() = runBlocking {
        val offsets = mutableListOf<String>()
        val first = JSONArray()
        for (i in 0 until 500) first.put(release("00000000-0000-0000-0000-${i.toString().padStart(12, '0')}", i.toString()))
        val second = JSONArray().put(release(chapterId, "501"))
            .put(release(mangaId, "502").also { it.getJSONObject("attributes").put("externalUrl", "https://example.org/paid") })
            .put(release(mangaId, "503").also { it.getJSONObject("attributes").put("pages", 0) })
        val scraper = MangaDexScraper { url ->
            offsets += url
            JSONObject().put("data", if (url.contains("offset=0&")) first else second).put("total", 503)
        }
        val result = scraper.fetchChapters(mangaId)
        assertEquals(2, offsets.size)
        assertTrue(offsets[1].contains("offset=500"))
        assertTrue(offsets.all { it.contains("includeExternalUrl=0") && it.contains("translatedLanguage[]=en") })
        assertTrue(result.none { it.number == 502.0 || it.number == 503.0 })
        assertTrue(result.first().title.contains("Test Group"))
        assertTrue(result.none { it.title.contains("null") })
    }

    @Test fun sourceFailureIsNotReportedAsAnEmptyChapter() = runBlocking {
        val scraper = MangaDexScraper { throw IOException("HTTP 503") }
        val error = runCatching { scraper.fetchChapter(chapterId) }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test fun pageUrlsPreserveOrderAndEncodeFilenames() = runBlocking {
        val scraper = MangaDexScraper { JSONObject().put("baseUrl", "https://uploads.mangadex.org/")
            .put("chapter", JSONObject().put("hash", "hash").put("data", JSONArray().put("page one.jpg").put("page2.jpg"))) }
        val content = scraper.fetchChapter("https://mangadex.org/chapter/$chapterId") as ScrapedChapterContent.Images
        assertEquals(listOf("https://uploads.mangadex.org/data/hash/page%20one.jpg", "https://uploads.mangadex.org/data/hash/page2.jpg"), content.pageUrls)
    }

    private fun release(id: String, number: String) = JSONObject().put("id", id)
        .put("attributes", JSONObject().put("chapter", number).put("volume", JSONObject.NULL).put("title", JSONObject.NULL)
            .put("translatedLanguage", "en").put("pages", 10).put("externalUrl", JSONObject.NULL))
        .put("relationships", JSONArray().put(JSONObject().put("type", "scanlation_group").put("attributes", JSONObject().put("name", "Test Group"))))
}
