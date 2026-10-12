package com.tome.reader.scraper

enum class ScraperCategory(val label: String) {
    MANGA("Manga"),
    MANHWA("Manhwa & webtoon"),
    COMICS("Comics"),
    NOVEL("Web novels & books"),

    ;

    val isSupported: Boolean get() = this == MANGA || this == MANHWA
}

data class ScrapedSeries(
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val url: String,
    val description: String? = null,
    val status: String? = null,
    val category: ScraperCategory,
    val externalUrl: String? = null,
    /** True when the source itself reports this title as 18+ (MangaDex does). */
    val isAdult: Boolean = false,
)

data class ScrapedChapter(
    val title: String,
    val url: String,
    val number: Double? = null,
    val releaseDate: String? = null,
)

data class ScraperSearchPage(
    val items: List<ScrapedSeries>,
    val hasMore: Boolean,
)

sealed class ScrapedChapterContent {
    data class Images(val pageUrls: List<String>) : ScrapedChapterContent()
    data class Text(val title: String?, val body: String) : ScrapedChapterContent()
    data class FileDownload(val url: String, val mimeType: String?) : ScrapedChapterContent()
}

interface Scraper {
    val id: String
    val name: String
    val baseUrl: String
    val category: ScraperCategory
    val isAvailable: Boolean

    /** Sources read in the browser do not participate in background catalogue calls. */
    val isBrowserOnly: Boolean get() = false

    /**
     * True for sources whose catalogue is adult-oriented as a whole.
     *
     * Per-title flags would be better, but only MangaDex actually reports a
     * content rating; the Madara aggregators show an 18+ entry in their genre
     * menu and nothing on the cards themselves, so there is no honest way to
     * filter them title by title from a listing. Whole-source is the truthful
     * granularity, and it is what the 18+ setting hides.
     */
    val isAdultSource: Boolean get() = false

    suspend fun fetchPopular(): List<ScrapedSeries> = fetchLatest()

    val diagnosticsText: String get() = ""
    val lastErrorText: String? get() = null

    suspend fun search(query: String): List<ScrapedSeries> = searchPage(query, 1).items

    suspend fun searchPage(query: String, page: Int): ScraperSearchPage

    suspend fun fetchLatest(): List<ScrapedSeries> = emptyList()

    /**
     * One page of the catalogue, so browsing can keep going until a source is
     * exhausted. Sources that only expose a single listing page inherit this
     * default and simply stop contributing after page 1.
     */
    suspend fun fetchLatestPage(page: Int): List<ScrapedSeries> =
        if (page <= 1) fetchLatest() else emptyList()

    suspend fun fetchChapters(seriesUrl: String): List<ScrapedChapter>

    suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent
}
