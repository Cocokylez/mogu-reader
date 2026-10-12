package com.tome.reader.scraper.ui

import com.tome.reader.scraper.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseFeedLoaderTest {
    private class Source(override val id: String, override val isBrowserOnly: Boolean = false) : Scraper {
        override val name = id
        override val category = ScraperCategory.MANGA
        override val baseUrl = "https://example.org"
        override val isAvailable = true
        override suspend fun searchPage(query: String, page: Int) = ScraperSearchPage(emptyList(), false)
        override suspend fun fetchChapters(seriesUrl: String) = emptyList<ScrapedChapter>()
        override suspend fun fetchChapter(chapterUrl: String): ScrapedChapterContent = error("unused")
    }
    private fun manga(id: String) = ScrapedSeries(id, null, null, id, category = ScraperCategory.MANGA)

    @Test fun emptyAndFailedSourcesFinishLoading() = runTest {
        val loader = BrowseFeedLoader(this, StandardTestDispatcher(testScheduler))
        loader.load(listOf(Source("empty"), Source("failure")), null, BrowseFeedMode.SEARCH, "test") {
            if (it.id == "failure") error("Offline") else emptyList()
        }
        advanceUntilIdle()
        assertFalse(loader.state.value.isLoading)
        assertEquals(0, loader.state.value.loadingCount)
        // Source failures are now surfaced in words the reader can act on, so the
        // stored text names the source and preserves the underlying cause rather
        // than being the bare exception message.
        val failure = loader.state.value.sourceErrors["failure"]
        assertNotNull(failure)
        assertTrue(failure!!.contains("failure"))
        assertTrue(failure.contains("Offline"))
    }

    @Test fun staleSearchCannotOverwriteNewSearchEvenIfSourceIgnoresCancellation() = runTest {
        val release = CompletableDeferred<Unit>()
        val loader = BrowseFeedLoader(this, StandardTestDispatcher(testScheduler))
        loader.load(listOf(Source("old")), null, BrowseFeedMode.SEARCH, "old") {
            withContext(NonCancellable) { release.await() }
            listOf(manga("old"))
        }
        runCurrent()
        loader.load(listOf(Source("new")), null, BrowseFeedMode.SEARCH, "new") { listOf(manga("new")) }
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals("new", loader.state.value.searchQuery)
        assertEquals(listOf("new"), loader.state.value.items.map { it.series.title })
        assertFalse(loader.state.value.isLoading)
    }

    @Test fun browserSourcesAreNotRequestedAndLegacyNovelsAreFiltered() = runTest {
        val loader = BrowseFeedLoader(this, StandardTestDispatcher(testScheduler))
        var calls = 0
        loader.load(listOf(Source("website", true), Source("manga")), null, BrowseFeedMode.LATEST) {
            calls++
            listOf(manga("m"), manga("novel").copy(category = ScraperCategory.NOVEL))
        }
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf("m"), loader.state.value.items.map { it.series.title })
    }

    @Test fun changingCategoryDuringLoadPreservesSelection() = runTest {
        val loader = BrowseFeedLoader(this, StandardTestDispatcher(testScheduler))
        loader.load(listOf(Source("manga")), null, BrowseFeedMode.LATEST) { delay(50); listOf(manga("m")) }
        loader.selectCategory(ScraperCategory.MANHWA)
        advanceUntilIdle()
        assertEquals(ScraperCategory.MANHWA, loader.state.value.selectedCategory)
        assertFalse(loader.state.value.isLoading)
    }
}
