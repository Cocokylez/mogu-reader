package com.tome.reader.scraper.ui

import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import com.tome.reader.scraper.SourceReferers
import com.tome.reader.scraper.ScraperFailure
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tome.reader.data.source.ChapterOrdering
import com.tome.reader.data.source.NaturalOrder
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.ReaderBackground
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.appContainer
import com.tome.reader.data.source.Keys
import com.tome.reader.scraper.BookmarkedSeries
import com.tome.reader.scraper.HttpClient
import com.tome.reader.scraper.OnlineStreamCache
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedChapterContent
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.ui.reader.ImmersiveMode
import com.tome.reader.ui.reader.KeepScreenOn
import com.tome.reader.ui.reader.VolumeKeyPaging
import com.tome.reader.ui.reader.ReaderAutoScroll
import com.tome.reader.ui.reader.AutoScrollControls
import com.tome.reader.ui.reader.ReaderSeekBar
import com.tome.reader.ui.reader.ReaderOverlayBar
import com.tome.reader.ui.reader.ZoomableImage
import com.tome.reader.ui.reader.ComicPageImage
import com.tome.reader.ui.components.TomeGlassCircleButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Cancellation must leave the screen immediately, rather than become a stale error/result. */
private suspend inline fun <T> readerResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) { throw e }
catch (e: Exception) { Result.failure(e) }

/**
 * Downloads page images into the on-disk stream cache and returns the cached file.
 * Two separate gates keep the device calm AND stop background prefetch from starving
 * the pages actually on screen:
 *  - visibleGate (4): pages requested on-screen — always have their own permits.
 *  - prefetchGate (2): look-ahead / look-behind chapters pulled in the background.
 * A per-url mutex guarantees each image downloads only once, even if the on-screen
 * loader and a prefetch both ask for it at the same moment.
 */
private object OnlineImageFetcher {
    private val visibleGate = Semaphore(4)
    private val prefetchGate = Semaphore(2)
    private val locks = Array(64) { Mutex() }
    private val downloads = java.util.concurrent.atomic.AtomicInteger()

    suspend fun ensureCached(context: Context, url: String, prefetch: Boolean): File? {
        val gate = if (prefetch) prefetchGate else visibleGate
        return gate.withPermit { download(context, url) }
    }

    private suspend fun download(context: Context, url: String): File? {
        val file = onlineCacheFile(OnlineStreamCache.directory(context), url)
        val mutex = locks[(url.hashCode() and Int.MAX_VALUE) % locks.size]
        mutex.withLock {
            if (!file.exists() || file.length() == 0L) {
                withContext(Dispatchers.IO) {
                    // The image CDN wants the *site* as Referer. Deriving it from
                    // the image URL's own directory is what made Toonily pages fail
                    // with "image stream blocked by this source".
                    val referer = SourceReferers.forUrl(url)
                        ?: url.substringBeforeLast('/', missingDelimiterValue = url)
                    HttpClient.downloadImage(url, file, referer)
                }
            }
        }
        OnlineStreamCache.touch(file)
        if (downloads.incrementAndGet() % 16 == 0) withContext(Dispatchers.IO) { OnlineStreamCache.prune(context) }
        return file.takeIf { it.exists() && it.length() > 0L }
    }
}

/** Stable cache-file path for a page url, shared by the on-screen loader and prefetch. */
private fun onlineCacheFile(dir: File, url: String): File {
    val ext = url.substringAfterLast('/').substringAfterLast('.').substringBefore('?')
        .takeIf { it.length in 2..5 } ?: "img"
    return File(dir, "${Keys.forUri(url)}.$ext")
}

/**
 * Tells [SourceReferers] which site this chapter's image hosts belong to, so the
 * page downloader sends a Referer those hosts accept.
 */
private fun rememberImageHosts(scraper: Scraper, content: ScrapedChapterContent?) {
    val urls = (content as? ScrapedChapterContent.Images)?.pageUrls ?: return
    SourceReferers.learn(urls, scraper.baseUrl)
}

/** Best-effort: pull every page image of a chapter into the cache ahead of time. */
private suspend fun prefetchChapterImages(context: Context, content: ScrapedChapterContent?) {
    val urls = (content as? ScrapedChapterContent.Images)?.pageUrls ?: return
    coroutineScope {
        urls.take(4).forEach { url ->
            launch { readerResult { OnlineImageFetcher.ensureCached(context, url, prefetch = true) } }
        }
    }
}

private data class StreamedChapter(
    val index: Int,
    val chapter: ScrapedChapter,
    val content: ScrapedChapterContent? = null,
    val error: String? = null,
)

private data class OnlineImageLoad(
    val file: File? = null,
    val error: String? = null,
)

/**
 * How many LazyColumn items a streamed chapter renders (one header plus its body).
 * Used to keep the scroll position stable when a previous chapter is prepended.
 */
private fun streamedItemCount(entry: StreamedChapter): Int {
    val body = when (val content = entry.content) {
        is ScrapedChapterContent.Images -> if (content.pageUrls.isEmpty()) 1 else content.pageUrls.size
        is ScrapedChapterContent.Text -> 1
        is ScrapedChapterContent.FileDownload -> 1
        null -> 1
    }
    return 1 + body
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun ScraperReadScreen(
    scraper: Scraper,
    series: ScrapedSeries,
    initialChapters: List<ScrapedChapter>,
    initialIndex: Int,
    onBack: () -> Unit,
    onOpenInBrowser: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    // A chapter that can only be read on the site (a Cloudflare check to clear, or
    // a publisher serving its own viewer) used to end at a message the reader could
    // do nothing with. Send them to the in-app browser instead -- once per chapter,
    // so a failure that repeats does not bounce them in a loop.
    val redirectedChapters = remember(series.url) { mutableSetOf<String>() }
    fun redirectIfWebsiteOnly(chapterUrl: String, error: Throwable?): Boolean {
        if (!ScraperFailure.needsWebsite(error)) return false
        if (!redirectedChapters.add(chapterUrl)) return false
        onOpenInBrowser(chapterUrl)
        return true
    }

    var rawChapters by remember(series.url) { mutableStateOf(initialChapters) }
    var chapters by remember(series.url) { mutableStateOf(orderedOnlineChapters(initialChapters)) }
    var chapterListLoading by remember(series.url) { mutableStateOf(initialChapters.isEmpty()) }
    var chapterListError by remember(series.url) { mutableStateOf<String?>(null) }
    val loaded = remember(series.url, initialIndex) { mutableStateListOf<StreamedChapter>() }
    var loadingIndex by remember(series.url, initialIndex) { mutableStateOf<Int?>(null) }
    // Chapters fetched ahead of being shown (content + images cached), so arriving at
    // the next chapter is instant. Kept apart from [loaded] until it's actually shown.
    val prefetched = remember(series.url, initialIndex) { mutableMapOf<Int, ScrapedChapterContent>() }
    val prefetching = remember(series.url, initialIndex) { mutableSetOf<Int>() }
    // Page urls that have finished (loaded, or errored after a retry). Forward advance
    // waits until the current chapter's pages have all settled, so the reader never
    // slips into the next chapter while images are still blank.
    val settledPages = remember(series.url, initialIndex) { mutableStateMapOf<String, Boolean>() }
    // Captured image aspect ratios (w/h), so each page slot keeps a STABLE height across
    // recomposition — scrolling back up no longer jumps as images re-measure.
    val pageAspect = remember(series.url, initialIndex) { mutableStateMapOf<String, Float>() }
    // How many pages of each chapter are revealed. Only grows (a settled page reveals the
    // next), so the rendered list never has content below an unloaded page to fling into.
    val revealFrontier = remember(series.url, initialIndex) { mutableStateMapOf<Int, Int>() }
    var uiVisible by remember(series.url) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val onlineHistory = remember { context.appContainer.onlineHistoryRepository }
    val scraperRepo = remember { context.appContainer.scraperRepository }
    // Position saves run on a scope that outlives this screen, so the last scroll spot
    // still persists when the user navigates straight away (a composition scope would
    // cancel mid-write and lose it — the cause of resuming at the chapter top).
    val durableScope = remember { context.appContainer.appScope }
    val requestedChapterUrl = remember(series.url, initialIndex, initialChapters) {
        initialChapters.getOrNull(initialIndex)?.url
    }

    LaunchedEffect(series.url) {
        withContext(Dispatchers.IO) { OnlineStreamCache.prune(context) }
    }

    // Resolved start chapter (initialIndex < 0 means "resume the saved position")
    // and the within-chapter scroll to restore. Null until looked up.
    var resolvedIndex by remember(series.url, initialIndex) { mutableStateOf<Int?>(null) }
    var restorePos by remember(series.url, initialIndex) { mutableStateOf<Pair<Int, Int>?>(null) }
    var didRestore by remember(series.url, initialIndex) { mutableStateOf(false) }

    // Reader options (mode / background / brightness): persisted, with a live in-session
    // override so the in-reader controls react instantly.
    val settingsRepo = remember { context.appContainer.settingsRepository }
    val appSettings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var modeOverride by remember(series.url) { mutableStateOf<ReadingDirection?>(null) }
    val mode = modeOverride ?: appSettings.onlineReadingMode
    var bgOverride by remember(series.url) { mutableStateOf<ReaderBackground?>(null) }
    val background = bgOverride ?: appSettings.readerBackground
    var brightnessOverride by remember(series.url) { mutableStateOf<Float?>(null) }
    val brightness = brightnessOverride ?: appSettings.readerBrightness
    var showReaderOptions by remember { mutableStateOf(false) }
    var autoScroll by remember(series.url) { mutableStateOf(false) }
    var pagedCurrentPage by remember(series.url) { mutableStateOf(0) }
    KeepScreenOn(appSettings.keepScreenOn)
    ReaderAutoScroll(listState, autoScroll && mode == ReadingDirection.WEBTOON && !showReaderOptions,
        appSettings.readerAutoScrollSpeed) { autoScroll = false }
    if (mode == ReadingDirection.WEBTOON) VolumeKeyPaging(appSettings.volumeKeyPaging) { forward ->
        scope.launch { listState.scrollBy(listState.layoutInfo.viewportSize.height * if (forward) 0.85f else -0.85f) }
    }

    // Paged-mode cursor: which chapter is shown, and whether to open it on its last page.
    var pagedIndex by remember(series.url, initialIndex) { mutableStateOf(-1) }
    var pagedLandOnLast by remember(series.url, initialIndex) { mutableStateOf(false) }
    var pagedStartConsumed by remember(series.url, initialIndex) { mutableStateOf(false) }

    ImmersiveMode(enabled = !uiVisible)

    // Items rendered before the first chapter's header (loading/error rows + the
    // "Load previous chapter" button). Save and restore must use the SAME count or the
    // restored page is off.
    fun leadingItemCount(): Int {
        val firstLoadedIndex = loaded.firstOrNull()?.index ?: 0
        return (if (chapterListLoading) 1 else 0) +
            (if (chapterListError != null) 1 else 0) +
            (if (loaded.isNotEmpty() && firstLoadedIndex > 0) 1 else 0)
    }

    fun renderedItemCount(entry: StreamedChapter): Int {
        val pageCount = (entry.content as? ScrapedChapterContent.Images)?.pageUrls?.size
        val initialReveal = if (entry.index == resolvedIndex && restorePos != null) restorePos!!.first else 1
        return if (pageCount != null && pageCount > 0) {
            1 + (revealFrontier[entry.index] ?: initialReveal).coerceIn(1, pageCount)
        } else 2
    }

    // Header is item 0; page N is item N+1 in both reading modes.
    fun positionFor(firstVisible: Int): Pair<Int, Int>? {
        var acc = leadingItemCount()
        for (entry in loaded) {
            val count = renderedItemCount(entry)
            if (firstVisible < acc + count) return entry.index to (firstVisible - acc).coerceAtLeast(0)
            acc += count
        }
        return loaded.lastOrNull()?.let { it.index to (streamedItemCount(it) - 1).coerceAtLeast(0) }
    }
    val visitedChapters = remember(series.url) { mutableSetOf<Int>() }
    val completedChapters = remember(series.url) { mutableSetOf<Int>() }

    fun saveProgress(chapterIdx: Int, scrollItem: Int, scrollOffset: Int) {
        visitedChapters.add(chapterIdx)
        val chapter = chapters.getOrNull(chapterIdx)
        durableScope.launch {
            onlineHistory.save(
                scraperId = scraper.id,
                seriesUrl = series.url,
                title = series.title,
                author = series.author,
                coverUrl = series.coverUrl,
                category = series.category.name,
                sourceName = scraper.name,
                chapterIndex = chapterIdx,
                chapterTitle = chapter?.title,
                scrollItem = scrollItem,
                scrollOffset = scrollOffset,
            )
            // Reading something puts it in the Library too. Previously a read was
            // only ever recorded as history, so a series you were part-way through
            // never appeared in the library unless you had explicitly followed it.
            // addFollow de-duplicates on (scraperId, url), so re-reading just moves
            // the entry back to the top rather than adding a duplicate.
            scraperRepo.addFollow(
                BookmarkedSeries(
                    scraperId = scraper.id,
                    title = series.title,
                    author = series.author,
                    coverUrl = series.coverUrl,
                    url = series.url,
                    category = series.category,
                )
            )
        }
    }

    // Maps a chapter url to its index in the *source* order (what the rest of the app
    // stores read-state against), so read marks line up with the series screen.
    val urlToSourceIndex = remember(rawChapters) {
        rawChapters.withIndex().associate { (i, c) -> c.url to i }
    }
    // Completing one chapter never marks earlier, skipped chapters read.
    fun markChapterRead(orderedIndex: Int) {
        if (orderedIndex !in chapters.indices || orderedIndex !in visitedChapters) return
        if (!completedChapters.add(orderedIndex)) return
        val chapter = chapters[orderedIndex]
        val sourceIndex = urlToSourceIndex[chapter.url] ?: return
        durableScope.launch {
            onlineHistory.markChaptersRead(
                scraperId = scraper.id, seriesUrl = series.url, title = series.title,
                author = series.author, coverUrl = series.coverUrl, category = series.category.name,
                sourceName = scraper.name, chapterIndices = listOf(sourceIndex),
                latestChapterIndex = sourceIndex, latestChapterTitle = chapter.title,
            )
        }
    }

    fun markCompletedVisibleChapters() {
        val layout = listState.layoutInfo
        var absolute = leadingItemCount()
        loaded.forEach { entry ->
            val fullCount = streamedItemCount(entry)
            val renderedCount = renderedItemCount(entry)
            val lastPageIndex = absolute + fullCount - 1
            if (entry.content is ScrapedChapterContent.Images && renderedCount == fullCount) {
                val lastPage = layout.visibleItemsInfo.firstOrNull { it.index == lastPageIndex }
                if (listState.firstVisibleItemIndex > lastPageIndex ||
                    (lastPage != null && lastPage.offset + lastPage.size <= layout.viewportEndOffset)) {
                    markChapterRead(entry.index)
                }
            }
            absolute += renderedCount
        }
    }

    // Pull a chapter's content + images into cache before it's shown, so arriving at
    // it is instant and scrolling through it never stalls.
    fun prefetchChapter(index: Int) {
        if (index !in chapters.indices) return
        if (loaded.any { it.index == index }) return
        if (prefetched.containsKey(index)) return
        if (!prefetching.add(index)) return
        scope.launch {
            val content = readerResult {
                withContext(Dispatchers.IO) { scraper.fetchChapter(chapters[index].url) }
            }.getOrNull()
            if (content != null) {
                prefetched.keys.removeAll { kotlin.math.abs(it - index) > 1 }
                prefetched[index] = content
                prefetchChapterImages(context, content)
            }
            prefetching.remove(index)
        }
    }

    fun queueChapter(index: Int) {
        if (index !in chapters.indices) return
        if (loadingIndex != null) return
        if (loaded.any { it.index == index }) return

        val chapter = chapters[index]
        prefetched.remove(index)?.let { ready ->
            loaded.add(StreamedChapter(index = index, chapter = chapter, content = ready))
            scope.launch { prefetchChapterImages(context, ready) }
            prefetchChapter(index + 1)
            return
        }

        loadingIndex = index
        scope.launch {
            val result = readerResult {
                withContext(Dispatchers.IO) { scraper.fetchChapter(chapter.url) }
            }
            loaded.add(
                result.fold(
                    onSuccess = { content ->
                        rememberImageHosts(scraper, content)
                        StreamedChapter(index = index, chapter = chapter, content = content)
                    },
                    onFailure = { error ->
                        val sentToSite = redirectIfWebsiteOnly(chapter.url, error)
                        StreamedChapter(
                            index = index,
                            chapter = chapter,
                            error = if (sentToSite) {
                                "Opening ${scraper.name} to read this chapter."
                            } else {
                                (
                                    error.message?.take(160)
                                        ?: "Could not stream this chapter"
                                    ) + ". Try Get on the chapter page to download it."
                            },
                        )
                    },
                )
            )
            loadingIndex = null
            loaded.sortBy { it.index }
            // Cache this chapter's images, then get the next one ready while reading.
            result.getOrNull()?.let { content -> launch { prefetchChapterImages(context, content) } }
            prefetchChapter(index + 1)
        }
    }

    // Load the previous chapter and prepend it, keeping the current page in view so
    // scrolling up flows straight into the end of the previous chapter (Kotatsu-style).
    fun queuePrevChapter() {
        val firstLoaded = loaded.firstOrNull() ?: return
        val prevIndex = firstLoaded.index - 1
        if (prevIndex !in chapters.indices) return
        if (loadingIndex != null) return
        if (loaded.any { it.index == prevIndex }) return

        val chapter = chapters[prevIndex]
        loadingIndex = prevIndex
        scope.launch {
            val ready = prefetched.remove(prevIndex)
            val result = if (ready != null) {
                Result.success(ready)
            } else {
                readerResult { withContext(Dispatchers.IO) { scraper.fetchChapter(chapter.url) } }
            }
            val entry = result.fold(
                onSuccess = { content ->
                    rememberImageHosts(scraper, content)
                    StreamedChapter(prevIndex, chapter, content = content)
                },
                onFailure = { error ->
                    val sentToSite = redirectIfWebsiteOnly(chapter.url, error)
                    StreamedChapter(
                        index = prevIndex,
                        chapter = chapter,
                        error = if (sentToSite) {
                            "Opening ${scraper.name} to read this chapter."
                        } else {
                            (
                                error.message?.take(160)
                                    ?: "Could not stream this chapter"
                                ) + ". Try Get on the chapter page to download it."
                        },
                    )
                },
            )
            loaded.add(0, entry)
            loadingIndex = null
            // Land on the start of the chapter we just loaded (the "Load previous
            // chapter" button, if still shown for an even earlier chapter, sits above it).
            val leading = if (prevIndex > 0) 1 else 0
            listState.scrollToItem(leading)
            entry.content?.let { content -> launch { prefetchChapterImages(context, content) } }
        }
    }

    // Step to another chapter in paged mode. Moving forward marks the chapter just left
    // (and everything before it) as read; the next chapter is already prefetched.
    fun goPagedChapter(newIndex: Int, landOnLast: Boolean) {
        if (newIndex !in chapters.indices) return
        pagedStartConsumed = true
        pagedLandOnLast = landOnLast
        pagedIndex = newIndex
    }

    LaunchedEffect(initialChapters) {
        if (initialChapters.isNotEmpty()) {
            rawChapters = initialChapters
            chapters = orderedOnlineChapters(initialChapters)
            chapterListLoading = false
            chapterListError = null
        }
    }

    LaunchedEffect(series.url) {
        if (chapters.isEmpty()) {
            chapterListLoading = true
            val result = readerResult {
                withContext(Dispatchers.IO) { scraper.fetchChapters(series.url) }
            }
            result.fold(
                onSuccess = { fetched ->
                    rawChapters = fetched
                    chapters = orderedOnlineChapters(fetched)
                    chapterListError = if (fetched.isEmpty()) "No chapters found" else null
                },
                onFailure = { error ->
                    if (redirectIfWebsiteOnly(series.url, error)) {
                        chapterListError = "Opening ${scraper.name} to read this series."
                        return@fold
                    }
                    chapterListError = error.message?.take(160) ?: "Could not load chapters"
                },
            )
            chapterListLoading = false
        }
    }

    // Resolve where to start: an explicit chapter, or the saved position when resuming.
    LaunchedEffect(series.url, initialIndex, requestedChapterUrl, chapters.size) {
        val saved = readerResult { onlineHistory.get(scraper.id, series.url) }.getOrNull()
        if (initialIndex >= 0) {
            val orderedIndex = requestedChapterUrl
                ?.let { url -> chapters.indexOfFirst { it.url == url } }
                ?.takeIf { it >= 0 }
                ?: initialIndex
            resolvedIndex = orderedIndex
            restorePos = if (saved != null && saved.chapterIndex == orderedIndex) {
                saved.scrollItem to saved.scrollOffset
            } else {
                null
            }
        } else {
            val savedChapterIndex = saved?.chapterTitle
                ?.let { title -> chapters.indexOfFirst { it.title == title } }
                ?.takeIf { it >= 0 }
                ?: saved?.chapterIndex
            resolvedIndex = (savedChapterIndex ?: 0).coerceIn(0, maxOf(0, chapters.lastIndex))
            restorePos = saved?.let { it.scrollItem to it.scrollOffset }
        }
    }

    LaunchedEffect(chapters.size, resolvedIndex, loaded.size, loadingIndex) {
        val target = resolvedIndex ?: return@LaunchedEffect
        if (chapters.isNotEmpty() && loaded.isEmpty() && loadingIndex == null) {
            queueChapter(target.coerceIn(chapters.indices))
        }
    }

    // Wait-for-images gate: the last loaded chapter must be fully revealed AND its final
    // page settled before we auto-advance — so the next chapter can't appear (and be
    // scrolled into) while pages are still loading.
    val lastEntry = loaded.lastOrNull()
    val lastChapterPages = (lastEntry?.content as? ScrapedChapterContent.Images)?.pageUrls.orEmpty()
    val lastChapterFrontier = (lastEntry?.let { revealFrontier[it.index] } ?: 0)
        .coerceAtMost(lastChapterPages.size)
    val lastChapterReady = lastChapterPages.isEmpty() ||
        (lastChapterFrontier >= lastChapterPages.size && settledPages[lastChapterPages.last()] == true)

    if (mode == ReadingDirection.WEBTOON) {
        // Previous chapters are loaded on demand via the "Load previous chapter" button
        // at the top of the list — auto-prepending on scroll caused a jump/reload loop.
        AutoLoadNextOnlineChapter(
            listState = listState,
            loadedCount = loaded.size,
            loadingIndex = loadingIndex,
            hasNext = loaded.lastOrNull()?.index?.let { it + 1 < chapters.size } == true && lastChapterReady,
            onLoadNext = {
                loaded.lastOrNull()?.let { entry ->
                    queueChapter(entry.index + 1)
                }
            },
        )

        // Restore the saved scroll once the resumed chapter has loaded (it's the first
        // loaded chapter, so its items start at index 0).
        LaunchedEffect(loaded.size, restorePos, resolvedIndex, chapterListLoading) {
            val target = resolvedIndex ?: return@LaunchedEffect
            val pos = restorePos ?: return@LaunchedEffect
            if (!didRestore && !chapterListLoading && loaded.any { it.index == target }) {
                // pos.first is the item index WITHIN the chapter; the chapter's items
                // start after the leading items. Re-apply once after layout settles so
                // lazily-measured image heights don't leave us off the saved page.
                loaded.takeWhile { it.index != target }.forEach { previous ->
                    (previous.content as? ScrapedChapterContent.Images)?.let { revealFrontier[previous.index] = it.pageUrls.size }
                }
                val absolute = leadingItemCount() + loaded.takeWhile { it.index != target }.sumOf(::streamedItemCount) +
                    pos.first.coerceIn(0, maxOf(0, streamedItemCount(loaded.first { it.index == target }) - 1))
                listState.scrollToItem(absolute, 0)
                val pages = (loaded.first { it.index == target }.content as? ScrapedChapterContent.Images)?.pageUrls
                pages?.getOrNull(pos.first - 1)?.let { url ->
                    kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        snapshotFlow { pageAspect[url] }.filterNotNull().first()
                    }
                }
                listState.scrollToItem(absolute, pos.second.coerceAtLeast(0))
                didRestore = true
            }
        }

        // Persist the resting position whenever scrolling settles.
        LaunchedEffect(series.url, loaded.size, didRestore) {
            snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress) }
                .distinctUntilChanged()
                .sample(500)
                .collect { (_, _, scrolling) ->
                    if (!scrolling && loaded.isNotEmpty() && (restorePos == null || didRestore)) {
                        positionFor(listState.firstVisibleItemIndex)?.let { (chapterIdx, within) ->
                            saveProgress(chapterIdx, within, listState.firstVisibleItemScrollOffset)
                            // Chapters above the one in view have been scrolled past — read.
                            markCompletedVisibleChapters()
                        }
                    }
                }
        }
    } else {
        // Paged mode: keep the shown chapter loaded (its images are prefetched too).
        LaunchedEffect(pagedIndex, chapters.size, loaded.size, loadingIndex) {
            val idx = pagedIndex
            if (idx in chapters.indices && loaded.none { it.index == idx } && loadingIndex == null) {
                queueChapter(idx)
            }
        }
    }

    // Initialise the paged cursor from the resolved start chapter (in any mode, so a
    // later switch to paged opens in the right place).
    LaunchedEffect(resolvedIndex) {
        if (pagedIndex < 0) resolvedIndex?.let { pagedIndex = it }
    }

    // Record the series in history as soon as its first chapter is shown, so it
    // appears even if the reader is closed without scrolling.
    LaunchedEffect(loaded.isNotEmpty(), resolvedIndex) {
        val target = resolvedIndex ?: return@LaunchedEffect
        if (loaded.any { it.index == target }) {
            saveProgress(target, restorePos?.first ?: 0, restorePos?.second ?: 0)
        }
    }

    // Final save when leaving the reader (catches navigating away mid-scroll, before the
    // settle save fires). Runs on the durable scope, so the write isn't cancelled.
    val finalSave by rememberUpdatedState {
        val restorePending = restorePos != null && !didRestore
        if (mode == ReadingDirection.WEBTOON && loaded.isNotEmpty() && !restorePending) {
            positionFor(listState.firstVisibleItemIndex)?.let { (chapterIdx, within) ->
                saveProgress(chapterIdx, within, listState.firstVisibleItemScrollOffset)
            }
        }
    }
    DisposableEffect(series.url) { onDispose { finalSave() } }

    val bgColor = readerBgColor(background)
    val dim = (1f - brightness).coerceIn(0f, 0.85f)
    val currentTitle = if (mode == ReadingDirection.WEBTOON) {
        positionFor(listState.firstVisibleItemIndex)?.first?.let { chapters.getOrNull(it)?.title } ?: series.title
    } else {
        chapters.getOrNull(pagedIndex)?.title ?: series.title
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
            if (mode == ReadingDirection.WEBTOON) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { uiVisible = !uiVisible }) },
                    // The floating overlay bar already names the current chapter. Without
                    // this inset the first in-list ChapterHeader renders underneath it and
                    // the title appears twice, one ghosted behind the other.
                    contentPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    if (chapterListLoading) {
                        item(key = "chapter-list-loading") { LoadingRow("Loading chapters...") }
                    }
                    if (chapterListError != null) {
                        item(key = "chapter-list-error") { MessageBlock(chapterListError.orEmpty()) }
                    }
                    val firstLoadedIndex = loaded.firstOrNull()?.index ?: 0
                    if (loaded.isNotEmpty() && firstLoadedIndex > 0) {
                        item(key = "load-prev") {
                            LoadPrevButton(loading = loadingIndex != null) { queuePrevChapter() }
                        }
                    }
                    loaded.forEach { entry ->
                        item(key = "chapter-${entry.index}-header") {
                            ChapterHeader(
                                title = entry.chapter.title,
                                source = scraper.name,
                                position = "${entry.index + 1}/${chapters.size}",
                            )
                        }
                        when (val content = entry.content) {
                            is ScrapedChapterContent.Images -> {
                                if (content.pageUrls.isEmpty()) {
                                    item(key = "chapter-${entry.index}-empty") {
                                        MessageBlock("No page images found for this chapter.")
                                    }
                                } else {
                                    val pages = content.pageUrls
                                    // Reveal pages strictly in order: render only as far as has loaded,
                                    // plus the one page currently loading. Nothing exists below it, so a
                                    // fast fling can't jump past an unloaded page. Each settled page
                                    // reveals the next; resuming pre-reveals up to the saved page.
                                    val initialReveal =
                                        if (entry.index == resolvedIndex && restorePos != null) {
                                            restorePos!!.first.coerceIn(1, pages.size)
                                        } else {
                                            1
                                        }
                                    val renderCount = (revealFrontier[entry.index] ?: initialReveal)
                                        .coerceIn(1, pages.size)
                                    itemsIndexed(
                                        items = pages.subList(0, renderCount),
                                        key = { pageIndex, url -> "chapter-${entry.index}-page-$pageIndex-$url" },
                                    ) { pageIndex, url ->
                                        OnlinePageImage(
                                            url = url,
                                            showAdvice = pageIndex == 0,
                                            aspect = pageAspect[url],
                                            onAspect = { pageAspect[url] = it },
                                            onSettled = {
                                                settledPages[url] = true
                                                // Reveal the next page once this one is in.
                                                val want = (pageIndex + 2).coerceAtMost(pages.size)
                                                if (want > (revealFrontier[entry.index] ?: 0)) {
                                                    revealFrontier[entry.index] = want
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            is ScrapedChapterContent.Text -> item(key = "chapter-${entry.index}-unsupported") {
                                MessageBlock("This chapter is text. Mogu Reader now focuses on manga and manhwa page images.")
                            }
                            is ScrapedChapterContent.FileDownload -> {
                                item(key = "chapter-${entry.index}-file") {
                                    FileDownloadMessage(onBack = onBack)
                                }
                            }
                            null -> {
                                item(key = "chapter-${entry.index}-error") {
                                    MessageBlock(entry.error ?: "Could not load chapter.")
                                }
                            }
                        }
                    }
                    if (loadingIndex != null) {
                        item(key = "loading-$loadingIndex") { LoadingRow("Loading chapter...") }
                    } else if (lastChapterPages.isNotEmpty() && !lastChapterReady) {
                        // Hold here until the current chapter's pages are all in — the next
                        // chapter isn't appended yet, so you can't scroll past blank pages.
                        item(key = "waiting-pages") {
                            LoadingRow("Loading pages… ($lastChapterFrontier/${lastChapterPages.size})")
                        }
                    } else if (loaded.isNotEmpty() && loaded.last().index >= chapters.lastIndex) {
                        item(key = "end") { MessageBlock("End of available chapters.") }
                    }
                }
            } else {
                // Paged mode (L→R / R→L), one chapter at a time with tap zones.
                val entry = loaded.firstOrNull { it.index == pagedIndex }
                when (val content = entry?.content) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (entry?.error != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MessageBlock(entry.error)
                            Button(onClick = { loaded.remove(entry); queueChapter(pagedIndex) }) { Text("Retry chapter") }
                        } else CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    is ScrapedChapterContent.Images -> {
                        if (content.pageUrls.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                MessageBlock("No page images found for this chapter.")
                            }
                        } else {
                            val pages = content.pageUrls
                            val startPage = when {
                                pagedLandOnLast -> pages.lastIndex
                                !pagedStartConsumed && pagedIndex == resolvedIndex ->
                                    ((restorePos?.first ?: 1) - 1).coerceIn(0, pages.lastIndex)
                                else -> 0
                            }
                            key(entry.index) {
                                PagedImageReader(
                                    pages = pages,
                                    rtl = mode == ReadingDirection.RTL,
                                    bg = bgColor,
                                    startPage = startPage,
                                    showControls = uiVisible,
                                    volumeKeys = appSettings.volumeKeyPaging,
                                    onPageSettled = { p -> pagedCurrentPage = p; saveProgress(pagedIndex, p + 1, 0) },
                                    onBeyondLast = { markChapterRead(pagedIndex); goPagedChapter(pagedIndex + 1, landOnLast = false) },
                                    onBeforeFirst = { goPagedChapter(pagedIndex - 1, landOnLast = true) },
                                    onToggleUi = { uiVisible = !uiVisible },
                                )
                            }
                            LaunchedEffect(entry.index) {
                                pagedStartConsumed = true
                                pagedLandOnLast = false
                            }
                        }
                    }
                    is ScrapedChapterContent.Text -> MessageBlock("This chapter is text. Open a manga or manhwa chapter instead.")
                    is ScrapedChapterContent.FileDownload ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            FileDownloadMessage(onBack = onBack)
                        }
                }
            }

            // Brightness dim overlay — drawn on top but has no pointer input, so taps
            // fall through to the reader beneath.
            if (dim > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            }

            if (uiVisible) {
                ReaderOverlayBar(title = currentTitle, onBack = onBack) {
                    if (mode == ReadingDirection.WEBTOON) {
                        AutoScrollControls(
                            autoScroll,
                            appSettings.readerAutoScrollSpeed,
                            onToggle = { autoScroll = !autoScroll },
                            onSpeed = { speed -> scope.launch { settingsRepo.setReaderAutoScrollSpeed(speed) } },
                        )
                    }
                    TomeGlassCircleButton(
                        icon = Icons.Default.Tune,
                        contentDescription = "Reader options",
                        onClick = { showReaderOptions = true },
                    )
                }
            }
        }

    if (showReaderOptions) {
        ModalBottomSheet(
            onDismissRequest = { showReaderOptions = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            ReaderOptionsContent(
                mode = mode,
                background = background,
                brightness = brightness,
                onMode = { m ->
                    autoScroll = false
                    if (m != mode) {
                        val position = if (mode == ReadingDirection.WEBTOON) {
                            positionFor(listState.firstVisibleItemIndex)?.let { (chapter, within) ->
                                Triple(chapter, within, listState.firstVisibleItemScrollOffset)
                            }
                        } else Triple(pagedIndex, pagedCurrentPage + 1, 0)
                        position?.let { (chapter, item, offset) ->
                            resolvedIndex = chapter
                            restorePos = item to offset
                            didRestore = false
                            pagedIndex = chapter
                            pagedCurrentPage = (item - 1).coerceAtLeast(0)
                            pagedStartConsumed = false
                            revealFrontier[chapter] = maxOf(revealFrontier[chapter] ?: 1, item)
                        }
                    }
                    modeOverride = m
                    scope.launch { settingsRepo.setOnlineReadingMode(m) }
                },
                onBackground = { b ->
                    bgOverride = b
                    scope.launch { settingsRepo.setReaderBackground(b) }
                },
                onBrightnessChange = { v -> brightnessOverride = v },
                onBrightnessCommit = { scope.launch { settingsRepo.setReaderBrightness(brightnessOverride ?: 1f) } },
            )
        }
    }
}

private fun orderedOnlineChapters(chapters: List<ScrapedChapter>): List<ScrapedChapter> =
    chapters.sortedWith(
        compareBy<ScrapedChapter> {
            ChapterOrdering.sortNumber(it.title, it.number) ?: Double.MAX_VALUE
        }.thenBy(NaturalOrder) { it.title },
    )

private fun readerBgColor(bg: ReaderBackground): Color = when (bg) {
    ReaderBackground.BLACK -> Color.Black
    ReaderBackground.GRAY -> Color(0xFF1A1A1A)
    ReaderBackground.WHITE -> Color.White
}

/**
 * One chapter shown as swipeable pages. Tap zones: outer thirds page forward/back
 * (flipped for right-to-left), centre toggles the chrome. At a chapter edge, paging
 * past it asks the caller to move to the adjacent chapter.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedImageReader(
    pages: List<String>,
    rtl: Boolean,
    bg: Color,
    startPage: Int,
    showControls: Boolean,
    volumeKeys: Boolean,
    onPageSettled: (Int) -> Unit,
    onBeyondLast: () -> Unit,
    onBeforeFirst: () -> Unit,
    onToggleUi: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    val latestSettled by rememberUpdatedState(onPageSettled)
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { zoomed = false }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (page, scrolling) -> if (!scrolling) latestSettled(page) }
    }

    fun forward() {
        if (pagerState.currentPage < pages.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else {
            onBeyondLast()
        }
    }

    fun backward() {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onBeforeFirst()
        }
    }

    VolumeKeyPaging(volumeKeys) { goForward -> if (goForward) forward() else backward() }
    Box(Modifier.fillMaxSize().background(bg)) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = rtl,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            PagedPageImage(
                url = pages[page], bg = bg,
                onZoomChanged = { zoomed = it },
                onTap = { fraction ->
                    when {
                        fraction < 0.33f -> if (rtl) forward() else backward()
                        fraction > 0.66f -> if (rtl) backward() else forward()
                        else -> onToggleUi()
                    }
                },
            )
        }
        if (showControls) ReaderSeekBar(
            current = pagerState.currentPage, total = pages.size, accent = null,
            onSeek = { scope.launch { pagerState.scrollToPage(it) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PagedPageImage(url: String, bg: Color, onZoomChanged: (Boolean) -> Unit, onTap: (Float) -> Unit) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableStateOf(0) }
    val load by produceState(initialValue = OnlineImageLoad(), url, attempt) {
        value = OnlineImageLoad()
        value = readerResult { OnlineImageFetcher.ensureCached(context, url, prefetch = false) }
            .fold(
                onSuccess = { f ->
                    if (f != null) OnlineImageLoad(file = f) else OnlineImageLoad(error = friendlyOnlineImageError(null))
                },
                onFailure = { e -> OnlineImageLoad(error = friendlyOnlineImageError(e.message)) },
            )
    }
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        when {
            load.file != null -> ZoomableImage(
                model = load.file, contentScale = ContentScale.Fit,
                onZoomChanged = onZoomChanged, onTapZone = onTap,
            )
            load.error != null -> Text(
                text = "${load.error}\n\nTap to retry.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp).clickable { attempt++ },
            )
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun ReaderOptionsContent(
    mode: ReadingDirection,
    background: ReaderBackground,
    brightness: Float,
    onMode: (ReadingDirection) -> Unit,
    onBackground: (ReaderBackground) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessCommit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Reader options",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        OptionLabel("Mode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip("Webtoon", mode == ReadingDirection.WEBTOON) { onMode(ReadingDirection.WEBTOON) }
            OptionChip("Paged L→R", mode == ReadingDirection.LTR) { onMode(ReadingDirection.LTR) }
            OptionChip("Paged R→L", mode == ReadingDirection.RTL) { onMode(ReadingDirection.RTL) }
        }

        OptionLabel("Background")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip("Black", background == ReaderBackground.BLACK) { onBackground(ReaderBackground.BLACK) }
            OptionChip("Gray", background == ReaderBackground.GRAY) { onBackground(ReaderBackground.GRAY) }
            OptionChip("White", background == ReaderBackground.WHITE) { onBackground(ReaderBackground.WHITE) }
        }

        OptionLabel("Brightness")
        // Stepped rather than dragged: the slider misbehaved here, and the
        // design uses - / + controls for brightness anyway.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OptionChip("−", false) {
                onBrightnessChange((brightness - 0.2f).coerceIn(0.2f, 1f))
                onBrightnessCommit()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(brightness.coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White),
                )
            }
            OptionChip("+", false) {
                onBrightnessChange((brightness + 0.2f).coerceIn(0.2f, 1f))
                onBrightnessCommit()
            }
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AutoLoadNextOnlineChapter(
    listState: LazyListState,
    loadedCount: Int,
    loadingIndex: Int?,
    hasNext: Boolean,
    onLoadNext: () -> Unit,
) {
    var sawUserScroll by remember(loadedCount) { mutableStateOf(false) }
    var requestedForCount by remember { mutableStateOf(-1) }

    LaunchedEffect(listState, loadedCount) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (moving) sawUserScroll = true
        }
    }

    LaunchedEffect(listState, loadedCount, loadingIndex, hasNext, sawUserScroll) {
        if (!hasNext || loadedCount <= 0 || loadingIndex != null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            val total = info.totalItemsCount
            total > 0 && lastVisible != null && lastVisible.index >= total - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 400
        }.distinctUntilChanged().collectLatest { nearBottom ->
            if (nearBottom && sawUserScroll && requestedForCount != loadedCount) {
                delay(450)
                requestedForCount = loadedCount
                onLoadNext()
            }
        }
    }
}

@Composable
private fun LoadPrevButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        } else {
            Button(onClick = onClick) { Text("↑ Load previous chapter") }
        }
    }
}

@Composable
private fun ChapterHeader(title: String, source: String, position: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$source - $position",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnlinePageImage(
    url: String,
    showAdvice: Boolean,
    aspect: Float?,
    onAspect: (Float) -> Unit,
    onSettled: () -> Unit = {},
) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableStateOf(0) }
    val loaded by produceState(initialValue = OnlineImageLoad(), url, attempt) {
        value = OnlineImageLoad() // back to the spinner while a (re)try runs
        value = readerResult { OnlineImageFetcher.ensureCached(context, url, prefetch = false) }
            .fold(
                onSuccess = { file ->
                    if (file != null) OnlineImageLoad(file = file)
                    else OnlineImageLoad(error = friendlyOnlineImageError(null))
                },
                onFailure = { error -> OnlineImageLoad(error = friendlyOnlineImageError(error.message)) },
            )
    }
    val errorText = loaded.error

    // One automatic retry for transient failures before asking the user to tap.
    LaunchedEffect(url, attempt, errorText) {
        if (errorText != null && attempt < 1) {
            delay(1500)
            attempt++
        }
    }

    // Tell the reveal logic this page has settled — loaded, or errored after its retry —
    // so the next page (and eventually the next chapter) can be revealed.
    LaunchedEffect(url, loaded.file, errorText, attempt) {
        if (loaded.file != null || (errorText != null && attempt >= 1)) onSettled()
    }

    if (loaded.file != null) {
        ComicPageImage(model = loaded.file!!, onAspect = onAspect)
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(aspect ?: 0.7f)
                .background(Color.Black)
                .then(if (errorText != null) Modifier.clickable { attempt++ } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (errorText != null) Text(
                text = if (showAdvice) "$errorText\n\nTap to retry." else "Image unavailable — tap to retry",
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(18.dp),
            ) else CircularProgressIndicator(color = Color.White)
        }
    }
}

private fun friendlyOnlineImageError(message: String?): String {
    val text = message.orEmpty()
    return when {
        text.contains("403") || text.contains("forbidden", ignoreCase = true) ->
            "Image stream blocked by this source. Try Get to download the chapter."
        text.contains("404") ->
            "This image page is missing. Try another chapter or use Get."
        text.contains("timeout", ignoreCase = true) ->
            "Image stream timed out. Check your connection or try Get."
        text.contains("dns", ignoreCase = true) || text.contains("unknownhost", ignoreCase = true) ->
            "Could not reach the image server. Check your connection or try Get."
        else -> "Image failed to load. Try Get to download the chapter."
    }
}

@Composable
private fun FileDownloadMessage(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "This source gives the chapter as a file, so it still needs Get/import before reading.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onBack) {
            Text("Back to chapters")
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageBlock(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}
