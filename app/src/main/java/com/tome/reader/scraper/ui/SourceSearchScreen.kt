package com.tome.reader.scraper.ui

import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.ui.components.TomeCoverArt
import com.tome.reader.ui.components.tomeCoverFallbackModels
import com.tome.reader.ui.components.tomeDisplayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSearchScreen(
    vm: ScraperViewModel,
    scraper: Scraper,
    onBack: () -> Unit,
    onOpenSeries: (Scraper, ScrapedSeries) -> Unit,
) {
    val ctx = LocalContext.current
    val state by vm.sourceSearchState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(scraper.id) {
        vm.loadLatest(scraper.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(scraper.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            copySourceReport(
                                context = ctx,
                                scraper = scraper,
                                error = state.error,
                                extraLines = listOf(
                                    "Visible items: ${state.items.size}",
                                    "Has more: ${state.hasMore}",
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy source report")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchSurface(
                value = query,
                isSearching = state.isSearching,
                onValueChange = { query = it },
                onSearch = {
                    if (query.isNotBlank() && !state.isSearching) {
                        vm.searchSource(scraper.id, query)
                    }
                },
            )

            when {
                state.isLoadingLatest -> CenterMessage("Loading ${scraper.name}...")
                state.isSearching && state.items.isEmpty() -> CenterMessage("Searching ${scraper.name}...")
                state.error != null && state.items.isEmpty() -> ErrorWithCopy(scraper.name, state.error!!)
                state.items.isEmpty() -> CenterMessage("Search ${scraper.name} for manga or manhwa")
                else -> {
                    val isPopular = query.isBlank() && !state.isSearching
                    SourceResultsGrid(
                        items = state.items,
                        scraper = scraper,
                        hasMore = state.hasMore,
                        isLoadingMore = state.isLoadingMore,
                        error = state.error,
                        popularTitle = if (isPopular) "Latest on ${scraper.name}" else null,
                        onOpenSeries = onOpenSeries,
                        onLoadMore = { vm.loadMoreSource(scraper.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSurface(
    value: String,
    isSearching: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search manga and manhwa") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (value.isNotBlank() && !isSearching) onSearch()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSearch,
                enabled = value.isNotBlank() && !isSearching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSearching) "Searching..." else "Search")
            }
        }
    }
}

@Composable
private fun SourceResultsGrid(
    items: List<ScrapedSeries>,
    scraper: Scraper,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    popularTitle: String?,
    onOpenSeries: (Scraper, ScrapedSeries) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    // Real infinite scroll: reaching the end pulls the next page instead of
    // waiting for the reader to find and tap a "load more" row.
    LaunchedEffect(gridState, hasMore, isLoadingMore, items.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - PREFETCH_DISTANCE
        }.distinctUntilChanged().collect { atEnd ->
            if (atEnd && hasMore && !isLoadingMore && error == null && items.isNotEmpty()) {
                onLoadMore()
            }
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(132.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        popularTitle?.let { title ->
            item(key = "popular-header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        items(items, key = { it.url }) { series ->
            SeriesPosterCard(
                scraper = scraper,
                series = series,
                onClick = { onOpenSeries(scraper, series) },
            )
        }
        if (hasMore || isLoadingMore || error != null) {
            item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                LoadMoreRow(
                    sourceName = scraper.name,
                    isLoading = isLoadingMore,
                    error = error,
                    onClick = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun SeriesPosterCard(
    scraper: Scraper,
    series: ScrapedSeries,
    onClick: () -> Unit,
) {
    val displayTitle = tomeDisplayTitle(series.title, series.url)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
        ) {
            TomeCoverArt(
                title = displayTitle,
                model = series.coverUrl,
                fallbackModels = tomeCoverFallbackModels(series.coverUrl, series.url),
                compact = true,
                badge = series.category.shortLabel(),
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    text = if (series.externalUrl != null) "Open in Browser" else scraper.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        series.author?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        series.status?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GeneratedCover(title: String, source: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title.take(2).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoadMoreRow(
    sourceName: String,
    isLoading: Boolean,
    error: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = when {
                    isLoading -> "Loading..."
                    error != null -> "Try again from $sourceName"
                    else -> "Load more from $sourceName"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorWithCopy(sourceName: String, error: String) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                val clip = ClipData.newPlainText("Mogu Reader Diagnostics", error)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE)?.let { mgr ->
                    (mgr as ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(ctx, "Copied diagnostics for $sourceName", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Copy Diagnostics")
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

private fun ScraperCategory.shortLabel(): String = when (this) {
    ScraperCategory.MANGA -> "Manga"
    ScraperCategory.MANHWA -> "Manhwa"
    ScraperCategory.COMICS -> "Comics"
    ScraperCategory.NOVEL -> "Novel"
}

/** Start the next page a few rows before the very bottom so scrolling stays smooth. */
private const val PREFETCH_DISTANCE = 6
