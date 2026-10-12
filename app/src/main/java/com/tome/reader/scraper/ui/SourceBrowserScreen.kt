package com.tome.reader.scraper.ui

import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import com.tome.reader.ui.theme.GlassSpec
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tome.reader.notifications.NotificationPermissionEffect
import com.tome.reader.notifications.ReaderNotifications
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.ui.components.TomeChip
import com.tome.reader.ui.components.TomeCoverArt
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.components.TomeHeader
import com.tome.reader.ui.components.TomeIconButton
import com.tome.reader.ui.components.tomeCoverFallbackModels
import com.tome.reader.ui.components.tomeDisplayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceBrowserScreen(
    vm: ScraperViewModel,
    onBack: (() -> Unit)? = null,
    onOpenSource: (sourceId: String, series: ScrapedSeries) -> Unit,
    onOpenSourceBrowse: (sourceId: String) -> Unit = {},
    /** Opens a URL in the in-app browser -- where a Cloudflare check can be cleared. */
    onOpenInBrowser: (url: String) -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onDownloads: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by vm.aggregatedBrowse.collectAsStateWithLifecycle()
    NotificationPermissionEffect()

    val categories = listOf(null, ScraperCategory.MANHWA, ScraperCategory.MANGA)
    val labels = listOf("All", "Manhwa", "Manga")

    LaunchedEffect(Unit) {
        if (state.items.isEmpty() && !state.isLoading) {
            vm.loadPopular()
        }
    }

    // Re-check failed sources whenever the app returns to the foreground. The
    // reader typically leaves to clear a Cloudflare check or fix their network,
    // and coming back to the same stale failures makes it look like nothing
    // changed. Only retries when something actually failed.
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestState by rememberUpdatedState(state)
    DisposableEffect(lifecycleOwner) {
        var skippedFirstResume = false
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (!skippedFirstResume) {
                skippedFirstResume = true
                return@LifecycleEventObserver
            }
            val current = latestState
            if (current.isLoading || current.searchQuery.isNotBlank()) return@LifecycleEventObserver
            if (current.sourceErrors.isEmpty()) return@LifecycleEventObserver
            if (current.feedMode == BrowseFeedMode.POPULAR) {
                vm.loadPopular(current.selectedCategory)
            } else {
                vm.loadAllLatest(current.selectedCategory)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.isLoading, state.feedMode, state.searchQuery, state.items) {
        if (!state.isLoading &&
            state.searchQuery.isBlank() &&
            state.feedMode != BrowseFeedMode.SEARCH &&
            state.items.isNotEmpty()
        ) {
            ReaderNotifications.notifyBrowseContentIfChanged(
                context = context,
                feedLabel = state.feedMode.label,
                itemKeys = state.items.map { "${it.sourceId}:${it.series.url}" },
            )
        }
    }

    val filtered = remember(state.items, state.selectedCategory) {
        val cat = state.selectedCategory
        if (cat == null) state.items
        else state.items.filter { it.series.category == cat }
    }

    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val availableSources = remember { vm.availableSources() }

    if (showSources) {
        SourcesDialog(
            sources = availableSources,
            onDismiss = { showSources = false },
            onSourceClick = { scraper ->
                showSources = false
                onOpenSourceBrowse(scraper.id)
            },
            onDiagnostics = {
                showSources = false
                onDiagnostics()
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TomeHeader("Discover") {
                TomeIconButton(Icons.Default.Language, "Sources", onClick = { showSources = true })
                TomeIconButton(Icons.Default.Download, "Downloads", onClick = onDownloads)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            TomeGlassSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = GlassSpec.SearchPlaceholder.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchText.isBlank()) {
                                Text(
                                    "Search all sources…",
                                    style = GlassSpec.SearchPlaceholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                    Text(
                        text = "Search",
                        style = GlassSpec.SeeAll.copy(fontSize = 13.sp, fontWeight = FontWeight.W700),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            isSearching = searchText.isNotBlank()
                            vm.searchAll(searchText)
                        },
                    )
                }
            }
            if (state.searchQuery.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TomeChip(
                        label = "Most viewed",
                        selected = state.feedMode == BrowseFeedMode.POPULAR,
                        onClick = {
                            searchText = ""
                            isSearching = false
                            vm.loadPopular(state.selectedCategory)
                        },
                    )
                    TomeChip(
                        label = "Latest",
                        selected = state.feedMode == BrowseFeedMode.LATEST,
                        onClick = {
                            searchText = ""
                            isSearching = false
                            vm.loadAllLatest(state.selectedCategory)
                        },
                    )
                }
            }
            if (state.searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Results for: \"${state.searchQuery}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        searchText = ""
                        isSearching = false
                        vm.loadAllLatest(state.selectedCategory)
                    }) {
                        Text("Clear")
                    }
                }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    TomeChip(
                        label = labels[index],
                        selected = state.selectedCategory == cat,
                        onClick = { vm.setAggregatedCategory(cat) },
                    )
                }
            }

            if (state.isLoading && filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading ${state.loadingCount} sources...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Say what actually went wrong. Most source failures are the
                    // network blocking them, and the reader can only fix that if we
                    // tell them which source failed and why.
                    Text(
                        text = if (state.sourceErrors.isNotEmpty()) {
                            buildString {
                                appendLine("No sources could be reached.")
                                appendLine()
                                state.sourceErrors.entries.take(3).forEach { (name, reason) ->
                                    appendLine("• " + reason)
                                }
                                val extra = state.sourceErrors.size - 3
                                if (extra > 0) appendLine("…and " + extra + " more source(s) failed the same way.")
                            }.trim()
                        } else {
                            "No manga or manhwa found.\nTry another title, category, or source."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else if (state.searchQuery.isNotBlank()) {
                // Search results: a single mixed grid across sources.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = GlassSpec.NavClearance,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { "${it.sourceId}:${it.series.url}" }) { item ->
                        AggregatedContentCard(
                            item = item,
                            onClick = { onOpenSource(item.sourceId, item.series) },
                            onLongClick = {
                                vm.follow(item.sourceId, item.series)
                                Toast.makeText(context, "Added to Library", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            } else {
                // One content grid for the whole feed.
                //
                // Browse used to be a horizontal row per source, which made the
                // screen read as a list of sources rather than something to read.
                // Now it is the same single grid as search results: content only,
                // top to bottom, strongest titles first.
                val ranked = remember(filtered) { interleaveByRank(filtered) }
                val blocked = remember(state.sourceErrors, availableSources) {
                    availableSources.filter { state.sourceErrors.containsKey(it.name) }
                }
                val gridState = rememberLazyGridState()
                // Infinite browsing: nearing the end pulls the next catalogue page
                // from every source until they are exhausted.
                LaunchedEffect(gridState, state.canLoadMore, state.isLoadingMore, state.feedPage) {
                    // Emit a single Boolean and dedupe it: collecting the raw
                    // layout info ran the body on every scroll frame.
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        val total = info.totalItemsCount
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                        // A short list satisfies "near the end" immediately, so it
                        // must be genuinely scrollable before paging.
                        total > 12 && last >= total - 6
                    }.distinctUntilChanged().collect { atEnd ->
                        if (atEnd && state.canLoadMore &&
                            !state.isLoadingMore && !state.isLoading
                        ) {
                            vm.loadMoreFeed()
                        }
                    }
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = GlassSpec.NavClearance,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (blocked.isNotEmpty()) {
                        item(key = "blocked", span = { GridItemSpan(maxLineSpan) }) {
                            BlockedSourcesNotice(
                                sources = blocked,
                                errors = state.sourceErrors,
                                onRetry = {
                                    if (state.feedMode == BrowseFeedMode.POPULAR) {
                                        vm.loadPopular(state.selectedCategory)
                                    } else {
                                        vm.loadAllLatest(state.selectedCategory)
                                    }
                                },
                                onOpenInBrowser = onOpenInBrowser,
                            )
                        }
                    }
                    items(ranked, key = { "${it.sourceId}:${it.series.url}" }) { item ->
                        AggregatedContentCard(
                            item = item,
                            onClick = { onOpenSource(item.sourceId, item.series) },
                            onLongClick = {
                                vm.follow(item.sourceId, item.series)
                                Toast.makeText(context, "Added to Library", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    if (state.isLoading || state.isLoadingMore) {
                        item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (state.isLoading) {
                                        "Loading ${state.loadingCount} more..."
                                    } else {
                                        "Loading more..."
                                    },
                                    style = GlassSpec.SettingValue,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AggregatedContentCard(
    item: AggregatedItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    // Both of these do string parsing and allocate a list. Unmemoized they ran for
    // every visible card on every recomposition, which is most of the UI-thread
    // cost while scrolling the feed.
    val displayTitle = remember(item.series.title, item.series.url) {
        tomeDisplayTitle(item.series.title, item.series.url)
    }
    val fallbacks = remember(item.series.coverUrl, item.series.url) {
        tomeCoverFallbackModels(item.series.coverUrl, item.series.url)
    }
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(GlassSpec.BrowseCoverAspect)
                    .shadow(
                        GlassSpec.ShadowBrowseCover,
                        RoundedCornerShape(GlassSpec.BrowseCoverRadius),
                        clip = false,
                    )
                    .clip(RoundedCornerShape(GlassSpec.BrowseCoverRadius)),
            ) {
                TomeCoverArt(
                    title = displayTitle,
                    model = item.series.coverUrl,
                    fallbackModels = fallbacks,
                    compact = true,
                    modifier = Modifier.fillMaxSize(),
                )
                // Source tag, top-left: 8.5sp/800, dark glass with a hairline.
                Text(
                    text = item.sourceName.take(10).uppercase(),
                    style = GlassSpec.SourceTag,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
                // Kind pill, bottom-left: 9sp/750 on a light translucent wash.
                Text(
                    text = item.series.category.label,
                    style = GlassSpec.KindPill,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(GlassSpec.ChipRadius))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Column(modifier = Modifier.padding(top = 7.dp)) {
                Text(
                    text = displayTitle,
                    style = GlassSpec.BrowseTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.series.author != null) {
                    Text(
                        text = item.series.author,
                        style = GlassSpec.BrowseAuthor,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesDialog(
    sources: List<Scraper>,
    onDismiss: () -> Unit,
    onSourceClick: (Scraper) -> Unit,
    onDiagnostics: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All Sources (${sources.size})") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(sources, key = { it.id }) { scraper ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        onClick = { onSourceClick(scraper) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = scraper.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = if (scraper.isBrowserOnly) "${scraper.category.label} · Website" else scraper.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = if (scraper.isAvailable) "Open" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDiagnostics) { Text("Source health") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * Orders the mixed feed so the strongest titles surface first.
 *
 * Each source returns its own list already ranked (popular first in Popular mode,
 * newest first in Latest), but items arrive in whatever order sources finish, so
 * a slow source's 40th title could outrank a fast source's best. Taking one from
 * each source in turn puts every source's #1 on the first row, every #2 on the
 * next, and so on.
 */
private fun interleaveByRank(items: List<AggregatedItem>): List<AggregatedItem> {
    if (items.isEmpty()) return items
    val queues = items.groupBy { it.sourceId }.values.map { it.toMutableList() }
    val ordered = ArrayList<AggregatedItem>(items.size)
    while (ordered.size < items.size) {
        var moved = false
        for (queue in queues) {
            if (queue.isNotEmpty()) {
                ordered += queue.removeAt(0)
                moved = true
            }
        }
        if (!moved) break
    }
    return ordered
}

/**
 * Sources that returned nothing, gathered into one notice.
 *
 * With per-source rows gone there is nowhere for a blocked source to explain
 * itself, and silently dropping it reads as the app losing a feature. "Open" is
 * the fix for a firewall or Cloudflare check: it loads the real site in the
 * in-app browser, where the challenge can be solved and the clearance cookie
 * issued for the scraper to reuse.
 */
@Composable
private fun BlockedSourcesNotice(
    sources: List<Scraper>,
    errors: Map<String, String>,
    onRetry: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    TomeGlassSurface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (sources.size == 1) {
                    "1 source could not be reached"
                } else {
                    "${sources.size} sources could not be reached"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            sources.forEach { source ->
                Text(
                    text = "${source.name}: ${errors[source.name].orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Open ${source.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenInBrowser(source.baseUrl) },
                    )
                }
            }
            Text(
                text = "Retry all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp).clickable(onClick = onRetry),
            )
        }
    }
}
