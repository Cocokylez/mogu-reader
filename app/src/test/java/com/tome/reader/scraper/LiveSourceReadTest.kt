package com.tome.reader.scraper

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Walks the whole read path against the live sites: catalogue -> series ->
 * chapter list -> chapter pages, for every registered source.
 *
 * This exists because "the catalogue loads" was repeatedly mistaken for "the
 * source works". Flame Comics browsed perfectly while every chapter failed, and
 * WEBTOON listed series that all had zero chapters. Only opening a chapter proves
 * a source is readable, which is the promise Browse makes.
 *
 * Skipped unless run deliberately, so ordinary builds never depend on the network:
 *   gradlew testDebugUnitTest --tests '*LiveSourceReadTest*' -DliveSources=true
 */
class LiveSourceReadTest {

    private data class Outcome(
        val source: String,
        val series: Int = 0,
        val chapters: Int = 0,
        val pages: Int = 0,
        val failedAt: String? = null,
        val detail: String? = null,
    ) {
        val readable: Boolean get() = pages > 0
        override fun toString(): String = buildString {
            append(if (readable) "  OK   " else "  FAIL ")
            append(source.padEnd(16))
            append("series=$series chapters=$chapters pages=$pages")
            if (failedAt != null) append("  <- stopped at $failedAt: ${detail?.take(110)}")
        }
    }

    @Test
    fun everySourceCanOpenAChapter() = runBlocking {
        assumeTrue(
            "set -DliveSources=true to run the live source check",
            System.getProperty("liveSources") == "true",
        )

        val registry = ScraperRegistry()
        // Pace the run. An earlier version fired every source's catalogue, chapter
        // list and chapter pages back to back on each invocation and got this
        // machine's IP throttled by MANGA Plus, which then reported as a source
        // failure. Being slow here is cheaper than a false red.
        val results = registry.available().map { source ->
            check(source).also { kotlinx.coroutines.delay(POLITE_DELAY_MS) }
        }

        println("\n=== live source read check ===")
        results.forEach { println(it) }
        val broken = results.filterNot { it.readable }
        println("readable: ${results.size - broken.size}/${results.size}\n")

        assert(broken.isEmpty()) {
            "sources that cannot open a chapter: " + broken.joinToString("\n", "\n") { it.toString() }
        }
    }

    /**
     * Infinite scroll only works if page 2 brings titles page 1 did not.
     *
     * Every source except ManhwaDen inherited the default fetchLatestPage, which
     * returns nothing past page 1, so scrolling a source stopped after the first
     * batch. This guards that.
     */
    @Test
    fun everySourcePagesItsCatalogue() = runBlocking {
        assumeTrue(
            "set -DliveSources=true to run the live source check",
            System.getProperty("liveSources") == "true",
        )
        val registry = ScraperRegistry()
        val lines = mutableListOf<String>()
        val broken = mutableListOf<String>()
        for (source in registry.available()) {
            val first = step("page1") { source.fetchLatestPage(1) }.orEmpty()
            kotlinx.coroutines.delay(POLITE_DELAY_MS)
            val second = step("page2") { source.fetchLatestPage(2) }.orEmpty()
            kotlinx.coroutines.delay(POLITE_DELAY_MS)
            val firstUrls = first.map { it.url.lowercase() }.toSet()
            val fresh = second.count { it.url.lowercase() !in firstUrls }
            lines += "  ${if (fresh > 0) "OK  " else "FAIL"} ${source.name.padEnd(16)}" +
                "page1=${first.size} page2=${second.size} new=$fresh"
            if (fresh == 0) broken += source.name
        }
        println("=== catalogue paging check ===")
        lines.forEach { println(it) }
        println("paging: ${registry.available().size - broken.size}/${registry.available().size}")
        assert(broken.isEmpty()) { "sources whose page 2 adds nothing: $broken" }
    }

    /** One source, end to end, with a per-step budget so a hung site can't stall the run. */
    private suspend fun check(source: Scraper): Outcome {
        val name = source.name
        var lastDetail: String? = null
        val catalogue = step("catalogue") { source.fetchLatest() }
        if (catalogue == null) {
            return Outcome(name, failedAt = "catalogue", detail = failure)
        }
        if (catalogue.isEmpty()) {
            return Outcome(name, failedAt = "catalogue", detail = "no series returned")
        }

        // Some entries genuinely have no chapters (one-shots, upcoming titles), so
        // try a few before calling the source broken.
        var chapterCount = 0
        for (candidate in catalogue.take(4)) {
            val chapters = step("chapters") { source.fetchChapters(candidate.url) }
            if (chapters == null) {
                lastDetail = failure
                continue
            }
            if (chapters.isEmpty()) {
                lastDetail = "no chapters for \"${candidate.title.take(40)}\""
                continue
            }
            chapterCount = chapters.size
            for (chapter in chapters.take(2)) {
                val content = step("chapter") { source.fetchChapter(chapter.url) }
                if (content == null) {
                    lastDetail = failure
                    continue
                }
                val pages = (content as? ScrapedChapterContent.Images)?.pageUrls?.size ?: 0
                if (pages > 0) {
                    return Outcome(name, catalogue.size, chapterCount, pages)
                }
                lastDetail = "chapter returned 0 pages"
            }
            return Outcome(name, catalogue.size, chapterCount, 0, "chapter", lastDetail)
        }
        return Outcome(name, catalogue.size, chapterCount, 0, "chapters", lastDetail)
    }

    /** Reason the last [step] returned null. */
    private var failure: String? = null

    /** Runs one step under a budget; null means it failed, with [failure] set. */
    private suspend fun <T> step(label: String, block: suspend () -> T): T? =
        try {
            failure = null
            withTimeoutOrNull(STEP_TIMEOUT_MS) { block() }
                .also { if (it == null) failure = "$label timed out" }
        } catch (e: Exception) {
            failure = "${e.javaClass.simpleName}: ${e.message}"
            null
        }

    private companion object {
        const val STEP_TIMEOUT_MS = 45_000L

        /** Gap between sources so a full run does not look like an attack. */
        const val POLITE_DELAY_MS = 3_000L
    }
}
