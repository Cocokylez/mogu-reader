package com.tome.reader.scraper.ui

import com.tome.reader.scraper.ScraperSearchPage
import com.tome.reader.scraper.ContentFilter
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tome.reader.data.repository.OnlineHistoryRepository
import com.tome.reader.scraper.BookmarkedSeries
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperDownloader
import com.tome.reader.scraper.ScraperFailure
import com.tome.reader.scraper.ScraperRegistry
import com.tome.reader.scraper.ScraperRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

private const val SEARCH_TIMEOUT_MS = 45_000L

data class SourceSearchState(
    val query: String = "",
    val items: List<ScrapedSeries> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingLatest: Boolean = false,
    val error: String? = null,
)

data class ScraperSeriesState(
    val series: ScrapedSeries? = null,
    val chapters: List<ScrapedChapter> = emptyList(),
    val downloadingIds: Set<String> = emptySet(),
    val downloadedIds: Set<String> = emptySet(),
    val readIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class BatchDownloadState(
    val total: Int = 0,
    val completed: Int = 0,
    val isRunning: Boolean = false,
)

enum class BrowseFeedMode(val label: String) {
    LATEST("Latest"),
    POPULAR("Popular"),
    SEARCH("Search"),
}

data class AggregatedItem(
    val series: ScrapedSeries,
    val sourceName: String,
    val sourceId: String,
)

data class AggregatedBrowseState(
    val items: List<AggregatedItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadingCount: Int = 0,
    val selectedCategory: ScraperCategory? = null,
    val searchQuery: String = "",
    val feedMode: BrowseFeedMode = BrowseFeedMode.LATEST,
    val sourceErrors: Map<String, String> = emptyMap(),
    // Catalogue paging, so browsing continues until every source is exhausted.
    val feedPage: Int = 1,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    // Sources that have finished this request, successfully or not. Without
    // this a source that returned nothing kept showing "Loading…" until every
    // other source had also finished.
    val completedSources: Set<String> = emptySet(),
)

data class ScraperDownloadEvent(
    val chapterTitle: String,
    val success: Boolean,
    val bookId: Long? = null,
    val message: String? = null,
)

class ScraperViewModel(
    application: Application,
    private val registry: ScraperRegistry,
    private val repository: ScraperRepository,
    private val downloader: ScraperDownloader,
    private val onlineHistoryRepository: OnlineHistoryRepository,
) : AndroidViewModel(application) {

    private val _sourceSearchState = MutableStateFlow(SourceSearchState())
    val sourceSearchState: StateFlow<SourceSearchState> = _sourceSearchState

    private val _seriesState = MutableStateFlow(ScraperSeriesState())
    val seriesState: StateFlow<ScraperSeriesState> = _seriesState

    private val _batchProgress = MutableStateFlow(BatchDownloadState())
    val batchProgress: StateFlow<BatchDownloadState> = _batchProgress

    private val _events = MutableSharedFlow<ScraperDownloadEvent>()
    val events = _events.asSharedFlow()

    private val feedLoader = BrowseFeedLoader(viewModelScope)
    val aggregatedBrowse: StateFlow<AggregatedBrowseState> = feedLoader.state

    val bookmarkedSeries: StateFlow<List<BookmarkedSeries>> = repository.bookmarkedSeries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null
    private var latestGeneration = 0L
    private var chapterJob: Job? = null
    private var batchJob: Job? = null
    private var chapterGeneration = 0L
    private val activeDownloads = mutableSetOf<String>()

    /**
     * Every enabled source participates in browsing. The four that are usually
     * fastest and most reliable are ordered first so the feed fills in from the
     * top while slower sources are still loading.
     */
    private fun featuredSources(): List<Scraper> {
        val preferred = listOf("mangadex", "weebcentral", "asurascans", "flamescans")
        return registry.available().filter(ContentFilter::isSourceAllowed).sortedBy { source ->
            val rank = preferred.indexOf(source.id)
            if (rank >= 0) rank else preferred.size
        }
    }

    fun loadAllLatest(category: ScraperCategory? = null) {
        feedLoader.load(
            featuredSources(),
            category,
            BrowseFeedMode.LATEST,
            pageLoader = { source, page -> source.fetchLatestPage(page) },
        ) { it.fetchLatest() }
    }

    /** Pull the next catalogue page from every source (infinite browsing). */
    fun loadMoreFeed() = feedLoader.loadMore()

    fun loadPopular(category: ScraperCategory? = null) {
        feedLoader.load(
            featuredSources(),
            category,
            BrowseFeedMode.POPULAR,
            pageLoader = { source, page ->
                if (page <= 1) source.fetchPopular() else source.fetchLatestPage(page)
            },
        ) { it.fetchPopular() }
    }

    fun searchAll(query: String) {
        val cleaned = query.trim()
        val category = aggregatedBrowse.value.selectedCategory
        if (cleaned.isBlank()) {
            loadAllLatest(category)
        } else {
            feedLoader.load(
                registry.available().filter(ContentFilter::isSourceAllowed),
                category,
                BrowseFeedMode.SEARCH,
                cleaned,
                pageLoader = { source, page -> source.searchPage(cleaned, page).items },
            ) { it.searchPage(cleaned, 1).items }
        }
    }

    fun filterAggregated(category: ScraperCategory?) = feedLoader.selectCategory(category)

    fun setAggregatedCategory(category: ScraperCategory?) = feedLoader.selectCategory(category)

    fun searchSource(scraperId: String, query: String) {
        val cleanedQuery = query.trim()
        searchJob?.cancel()
        val generation = ++latestGeneration // invalidate pending source requests
        if (cleanedQuery.isBlank()) {
            _sourceSearchState.value = SourceSearchState()
            loadLatest(scraperId)
            return
        }

        val scraper = registry.get(scraperId) ?: return

        _sourceSearchState.value = SourceSearchState(
            query = cleanedQuery,
            isSearching = true,
        )

        searchJob = viewModelScope.launch {
            val result = runCatching {
                withTimeout(SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { scraper.searchPage(cleanedQuery, 1) }
                }
            }

            ensureActive()
            if (generation != latestGeneration) return@launch
            val state = result.fold(
                onSuccess = { page ->
                    Log.d("MoguScraper", "searchSource [${scraper.name}] items=${page.items.size}")
                    SourceSearchState(
                        query = cleanedQuery,
                        items = page.items,
                        page = 1,
                        hasMore = page.hasMore,
                        isSearching = false,
                        error = if (page.items.isEmpty()) "No results" else null,
                    )
                },
                onFailure = { error ->
                    val type = error.javaClass.simpleName
                    val msg = if (error is TimeoutCancellationException) "Search timed out"
                    else "$type: ${error.message?.take(100) ?: "Search failed"}"
                    Log.w("MoguScraper", "searchSource fail [${scraper.name}] $msg")
                    SourceSearchState(
                        query = cleanedQuery,
                        isSearching = false,
                        error = msg,
                    )
                },
            )
            _sourceSearchState.value = state
        }
    }

    fun loadMoreSource(scraperId: String) {
        val current = _sourceSearchState.value
        // A blank query means the screen is browsing the catalogue, not searching.
        // Bailing out on that is what stopped paging when a source was opened
        // through "See all".
        if (current.isSearching || current.isLoadingMore || !current.hasMore) return

        val scraper = registry.get(scraperId) ?: return
        val nextPage = current.page + 1
        val generation = latestGeneration

        _sourceSearchState.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            val result = runCatching {
                withTimeout(SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        if (current.query.isBlank()) {
                            val page = scraper.fetchLatestPage(nextPage)
                            ScraperSearchPage(page, hasMore = page.isNotEmpty())
                        } else {
                            scraper.searchPage(current.query, nextPage)
                        }
                    }
                }
            }

            ensureActive()
            if (generation != latestGeneration) return@launch
            result.fold(
                onSuccess = { page ->
                    val combined = (current.items + page.items).distinctBy { it.url }
                    _sourceSearchState.value = current.copy(
                        items = combined,
                        page = nextPage,
                        hasMore = page.hasMore && page.items.isNotEmpty(),
                        isLoadingMore = false,
                        error = null,
                    )
                },
                onFailure = { error ->
                    val type = error.javaClass.simpleName
                    val msg = if (error is TimeoutCancellationException) "Timed out"
                    else "$type: ${error.message?.take(100) ?: "Load failed"}"
                    _sourceSearchState.value = current.copy(
                        isLoadingMore = false,
                        hasMore = true,
                        error = msg,
                    )
                },
            )
        }
    }

    fun resetSourceSearch() {
        latestGeneration++
        searchJob?.cancel()
        _sourceSearchState.value = SourceSearchState()
    }

    fun loadLatest(scraperId: String) {
        searchJob?.cancel()
        val scraper = registry.get(scraperId) ?: return
        val generation = ++latestGeneration
        _sourceSearchState.value = SourceSearchState(isLoadingLatest = true)
        searchJob = viewModelScope.launch {
            var errorMsg: String? = null
            val items = try {
                withTimeout(SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { scraper.fetchLatest() }
                }
            } catch (e: TimeoutCancellationException) {
                errorMsg = ScraperFailure.humanize(e, scraper.name)
                emptyList()
            } catch (e: Exception) {
                errorMsg = ScraperFailure.humanize(e, scraper.name)
                emptyList()
            }
            ensureActive()
            if (generation != latestGeneration) return@launch
            if (items.isEmpty() && errorMsg == null) {
                val lastErr = scraper.lastErrorText?.takeIf { it.isNotBlank() }
                val diag = scraper.diagnosticsText.takeIf { it.isNotBlank() }
                errorMsg = if (lastErr != null && diag != null) {
                    "$lastErr\n\n$diag"
                } else lastErr ?: diag ?: "No content available"
            }
            _sourceSearchState.value = SourceSearchState(
                items = items,
                isLoadingLatest = false,
                error = errorMsg,
                // Browsing a source paged through fetchLatestPage, but hasMore
                // defaulted to false here, so "load more" never appeared and the
                // grid stopped at the first page.
                hasMore = items.isNotEmpty(),
            )
        }
    }

    fun selectSeries(series: ScrapedSeries) {
        chapterJob?.cancel()
        chapterGeneration++
        _seriesState.value = ScraperSeriesState(series = series)
    }

    /** Explicitly add an online series to the Library (followed list). */
    fun follow(sourceId: String, series: ScrapedSeries) {
        viewModelScope.launch {
            repository.addFollow(
                BookmarkedSeries(
                    scraperId = sourceId,
                    title = series.title,
                    author = series.author,
                    coverUrl = series.coverUrl,
                    url = series.url,
                    category = series.category,
                )
            )
        }
    }

    fun loadChapters(scraper: Scraper, series: ScrapedSeries) {
        chapterJob?.cancel()
        val generation = ++chapterGeneration
        _seriesState.value = ScraperSeriesState(series = series, isLoading = true)
        chapterJob = viewModelScope.launch {
            try {
                val chapters = withTimeout(SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { scraper.fetchChapters(series.url) }
                }
                ensureActive()
                if (generation != chapterGeneration) return@launch
                repository.addBookmark(BookmarkedSeries(
                    scraperId = scraper.id, title = series.title, author = series.author,
                    coverUrl = series.coverUrl, url = series.url, category = series.category,
                ))
                _seriesState.value = _seriesState.value.copy(
                    chapters = chapters, isLoading = false,
                    error = if (chapters.isEmpty()) "No readable chapters found. Try the source website." else null,
                )
                loadReadStatus(scraper, series, chapters)
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                if (generation == chapterGeneration) _seriesState.value = _seriesState.value.copy(
                    isLoading = false, error = "The source timed out. Please retry.",
                )
            } catch (error: Exception) {
                if (generation == chapterGeneration) _seriesState.value = _seriesState.value.copy(
                    isLoading = false, error = ScraperFailure.humanize(error, scraper.name),
                )
            }
        }
    }

    private suspend fun loadReadStatus(scraper: Scraper, series: ScrapedSeries, chapters: List<ScrapedChapter>) {
        val downloadedIndices = runCatching {
            downloader.getDownloadedChapterIndices(scraper, series)
        }.getOrDefault(emptySet())
        val downloadedReadIndices = runCatching {
            downloader.getReadChapterIndices(scraper, series)
        }.getOrDefault(emptySet())
        val streamedReadIndices = runCatching {
            onlineHistoryRepository.getReadChapterIndices(scraper.id, series.url)
        }.getOrDefault(emptySet())

        val readIndices = downloadedReadIndices + streamedReadIndices
        val readUrls = chapters.filterIndexed { index, _ -> index in readIndices }.map { it.url }.toSet()
        val downloadedUrls = chapters.filterIndexed { index, _ -> index in downloadedIndices }.map { it.url }.toSet()
        if (_seriesState.value.series?.url != series.url) return
        _seriesState.value = _seriesState.value.copy(
            readIds = readUrls,
            downloadedIds = downloadedUrls,
        )
    }

    private fun updateDownloadState(series: ScrapedSeries, url: String, busy: Boolean, downloaded: Boolean = false) {
        val current = _seriesState.value
        if (current.series?.url != series.url) return
        _seriesState.value = current.copy(
            downloadingIds = if (busy) current.downloadingIds + url else current.downloadingIds - url,
            downloadedIds = if (downloaded) current.downloadedIds + url else current.downloadedIds,
        )
    }

    private suspend fun fetchAndDownload(
        scraper: Scraper, series: ScrapedSeries, chapter: ScrapedChapter, order: Int,
    ): Long {
        val key = "${scraper.id}:${chapter.url}"
        check(activeDownloads.add(key)) { "This chapter is already downloading" }
        updateDownloadState(series, chapter.url, busy = true)
        try {
            val content = withTimeout(SEARCH_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { scraper.fetchChapter(chapter.url) }
            }
            val result = downloader.downloadChapter(scraper, series, chapter, content, order)
            check(result.bookId > 0) { "The chapter could not be saved" }
            updateDownloadState(series, chapter.url, busy = false, downloaded = true)
            return result.bookId
        } finally {
            activeDownloads.remove(key)
            updateDownloadState(series, chapter.url, busy = false)
        }
    }

    fun downloadChapter(
        scraper: Scraper, series: ScrapedSeries, chapter: ScrapedChapter, order: Int,
        onDownloaded: ((Long) -> Unit)? = null,
    ) {
        if ("${scraper.id}:${chapter.url}" in activeDownloads) return
        viewModelScope.launch {
            try {
                val bookId = fetchAndDownload(scraper, series, chapter, order)
                _events.emit(ScraperDownloadEvent(chapter.title, true, bookId))
                onDownloaded?.invoke(bookId)
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                _events.emit(ScraperDownloadEvent(chapter.title, false, message = "Source timed out. Please retry."))
            } catch (error: Exception) {
                _events.emit(ScraperDownloadEvent(chapter.title, false,
                    message = "Download failed: ${error.message?.take(150) ?: chapter.title}"))
            }
        }
    }

    fun downloadAll(scraper: Scraper, series: ScrapedSeries) = startBatch(scraper, series, unreadOnly = false)

    fun downloadUnread(scraper: Scraper, series: ScrapedSeries) = startBatch(scraper, series, unreadOnly = true)

    private fun startBatch(scraper: Scraper, series: ScrapedSeries, unreadOnly: Boolean) {
        if (batchJob?.isActive == true) return
        val chapters = _seriesState.value.chapters
        if (chapters.isEmpty()) return
        _batchProgress.value = BatchDownloadState(isRunning = true)
        batchJob = viewModelScope.launch {
            var succeeded = 0
            var failed = 0
            try {
                loadReadStatus(scraper, series, chapters)
                val current = _seriesState.value
                if (current.series?.url != series.url) return@launch
                val targets = chapters.withIndex().filter { (_, chapter) ->
                    chapter.url !in current.downloadedIds && (!unreadOnly || chapter.url !in current.readIds)
                }
                _batchProgress.value = BatchDownloadState(total = targets.size, isRunning = true)
                for ((index, chapter) in targets) {
                    ensureActive()
                    try {
                        fetchAndDownload(scraper, series, chapter, index)
                        succeeded++
                    } catch (cancelled: CancellationException) {
                        if (cancelled !is TimeoutCancellationException) throw cancelled
                        failed++
                    } catch (_: Exception) {
                        failed++
                    }
                    _batchProgress.value = _batchProgress.value.copy(completed = succeeded + failed)
                }
                val message = when {
                    targets.isEmpty() -> "No chapters need downloading"
                    failed > 0 -> "Downloaded $succeeded chapters; $failed failed. Retry to download missing chapters."
                    else -> "Downloaded $succeeded chapter${if (succeeded == 1) "" else "s"}"
                }
                _events.emit(ScraperDownloadEvent("Chapters", failed == 0, message = message))
                loadReadStatus(scraper, series, chapters)
            } finally {
                _batchProgress.value = BatchDownloadState()
            }
        }
    }

    fun cancelBatchDownload() {
        batchJob?.cancel()
        _batchProgress.value = BatchDownloadState()
    }

    fun removeBookmark(scraperId: String, url: String) {
        viewModelScope.launch { repository.removeBookmark(scraperId, url) }
    }

    fun availableSources(): List<Scraper> = registry.available()

    class Factory(
        private val application: Application,
        private val registry: ScraperRegistry,
        private val repository: ScraperRepository,
        private val downloader: ScraperDownloader,
        private val onlineHistoryRepository: OnlineHistoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScraperViewModel(
                application,
                registry,
                repository,
                downloader,
                onlineHistoryRepository,
            ) as T
        }
    }
}
