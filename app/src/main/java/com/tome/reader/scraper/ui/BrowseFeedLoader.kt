package com.tome.reader.scraper.ui

import com.tome.reader.scraper.ScraperFailure

import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns a single feed request; superseded requests can never publish results. */
internal class BrowseFeedLoader(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow(AggregatedBrowseState())
    val state = mutableState.asStateFlow()
    // Every enabled source is queried now, not a curated four, so the old limit
    // of 3 left working sources queued behind blocked ones for over a minute.
    // These are independent hosts and OkHttp pools per-host anyway.
    private val networkSlots = Semaphore(8)
    private var job: Job? = null
    private var generation = 0L

    // Kept so scrolling can request the next page from the same query.
    private var activeSourcesForPaging: List<Scraper> = emptyList()
    private var pageLoaderForPaging: (suspend (Scraper, Int) -> List<ScrapedSeries>)? = null

    /**
     * Append the next catalogue page from every source. Browsing keeps going
     * until a page adds nothing, at which point the sources are exhausted.
     */
    fun loadMore() {
        val pageLoader = pageLoaderForPaging ?: return
        val current = mutableState.value
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
        val sources = activeSourcesForPaging
        if (sources.isEmpty()) return

        val nextPage = current.feedPage + 1
        val request = generation
        mutableState.update { it.copy(isLoadingMore = true) }
        scope.launch {
            var added = 0
            sources.map { source ->
                launch {
                    val results = try {
                        networkSlots.withPermit {
                            withTimeout(20_000L) {
                                withContext(ioDispatcher) { pageLoader(source, nextPage) }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        if (cancelled !is TimeoutCancellationException) throw cancelled
                        emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (request != generation) return@launch
                    val mapped = results.filter { it.category.isSupported }
                        .map { AggregatedItem(it, source.name, source.id) }
                    if (mapped.isNotEmpty()) {
                        added += mapped.size
                        mutableState.update { st ->
                            st.copy(
                                items = (st.items + mapped)
                                    .distinctBy { "${it.sourceId}:${it.series.url}" },
                            )
                        }
                    }
                }
            }.forEach { it.join() }
            if (request != generation) return@launch
            mutableState.update {
                it.copy(isLoadingMore = false, feedPage = nextPage, canLoadMore = added > 0)
            }
        }
    }

    fun selectCategory(category: ScraperCategory?) {
        mutableState.update { it.copy(selectedCategory = category?.takeIf { cat -> cat.isSupported }) }
    }

    fun load(
        sources: List<Scraper>,
        category: ScraperCategory?,
        mode: BrowseFeedMode,
        query: String = "",
        pageLoader: (suspend (Scraper, Int) -> List<ScrapedSeries>)? = null,
        loader: suspend (Scraper) -> List<ScrapedSeries>,
    ) {
        job?.cancel()
        val request = ++generation
        val activeSources = sources.filter { it.isAvailable && !it.isBrowserOnly && it.category.isSupported }
        activeSourcesForPaging = activeSources
        pageLoaderForPaging = pageLoader
        mutableState.value = AggregatedBrowseState(
            isLoading = activeSources.isNotEmpty(), loadingCount = activeSources.size,
            selectedCategory = category, searchQuery = query, feedMode = mode,
            canLoadMore = pageLoader != null && activeSources.isNotEmpty(),
        )
        job = scope.launch {
            activeSources.forEach { source ->
                launch sourceRequest@ {
                    var failure: String? = null
                    var results = emptyList<AggregatedItem>()
                    try {
                        val items = networkSlots.withPermit {
                            // Sits above OkHttp's own 15s call timeout so the HTTP
                            // layer decides when a source has failed; this is only a
                            // backstop. With 8 slots a slow source no longer blocks
                            // the queue the way it did at 3.
                            withTimeout(20_000L) { withContext(ioDispatcher) { loader(source) } }
                        }
                        ensureActive()
                        results = items.filter { it.category.isSupported }
                            .distinctBy { it.url }.take(if (query.isBlank()) 20 else 40)
                            .map { AggregatedItem(it, source.name, source.id) }
                    } catch (timeout: TimeoutCancellationException) {
                        failure = ScraperFailure.humanize(timeout, source.name)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = ScraperFailure.humanize(error, source.name)
                    }
                    if (request != generation) return@sourceRequest
                    mutableState.update { current ->
                        val remaining = (current.loadingCount - 1).coerceAtLeast(0)
                        current.copy(
                            items = (current.items + results).distinctBy { "${it.sourceId}:${it.series.url}" },
                            loadingCount = remaining, isLoading = remaining > 0,
                            completedSources = current.completedSources + source.name,
                            sourceErrors = if (failure == null) current.sourceErrors
                                else current.sourceErrors + (source.name to failure!!),
                        )
                    }
                }
            }
        }
    }
}
