package com.tome.reader.scraper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tome.reader.data.source.ChapterOrdering
import com.tome.reader.data.source.NaturalOrder
import com.tome.reader.domain.model.ChapterListOrder
import com.tome.reader.notifications.NotificationPermissionEffect
import com.tome.reader.notifications.ReaderNotifications
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.ui.components.TomeChip
import com.tome.reader.ui.components.TomeCoverArt
import com.tome.reader.ui.components.TomeGlassCircleButton
import com.tome.reader.ui.components.TomeGlassBackdrop
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.components.TomePill
import com.tome.reader.ui.components.TomePrimaryButton
import com.tome.reader.ui.components.TomeSectionHeader
import com.tome.reader.ui.components.TomeStatsRow
import com.tome.reader.ui.components.tomeCoverFallbackModels
import com.tome.reader.ui.components.tomeGradient
import com.tome.reader.ui.components.tomeDisplayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScraperSeriesScreen(
    vm: ScraperViewModel,
    scraper: Scraper,
    series: ScrapedSeries,
    onBack: () -> Unit,
    onReadChapter: (Int) -> Unit = {},
    chapterListOrder: ChapterListOrder = ChapterListOrder.ASCENDING,
    onToggleChapterOrder: () -> Unit = {},
    onUnlock: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val state by vm.seriesState.collectAsStateWithLifecycle()
    val batch by vm.batchProgress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDownloadUnreadConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val displayChapters = remember(state.chapters, chapterListOrder) {
        orderedScrapedChapters(state.chapters, chapterListOrder)
    }
    NotificationPermissionEffect()

    LaunchedEffect(Unit) {
        vm.loadChapters(scraper, series)
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            val message = event.message ?: if (event.success) {
                "Downloaded: ${event.chapterTitle}"
            } else {
                "Failed: ${event.chapterTitle}"
            }
            snackbar.showSnackbar(message)
            if (event.success) {
                ReaderNotifications.showDownloadComplete(
                    context,
                    "Downloaded",
                    event.message ?: event.chapterTitle,
                )
            } else {
                ReaderNotifications.showDownloadFailed(
                    context,
                    "Download failed",
                    event.chapterTitle,
                )
            }
        }
    }

    val unreadMissing = state.chapters.count {
        it.url !in state.downloadedIds && it.url !in state.readIds
    }
    val reportSource = {
        copySourceReport(
            context = context,
            scraper = scraper,
            series = series,
            error = state.error,
            extraLines = listOf(
                "Chapters loaded: ${state.chapters.size}",
                "Read chapters: ${state.readIds.size}",
                "Downloaded chapters: ${state.downloadedIds.size}",
            ),
        )
    }

    if (showDownloadUnreadConfirm) {
        AlertDialog(
            onDismissRequest = { showDownloadUnreadConfirm = false },
            title = { Text("Download unread chapters?") },
            text = {
                Text("This will download $unreadMissing unread chapters from ${scraper.name}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadUnreadConfirm = false
                    vm.downloadUnread(scraper, series)
                }) { Text("Download Unread") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadUnreadConfirm = false }) { Text("Cancel") }
            },
        )
    }

    TomeGlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                ScraperSeriesHero(
                    scraper = scraper,
                    series = series,
                    readCount = state.readIds.size,
                    totalCount = state.chapters.size,
                    onBack = onBack,
                    onStart = { onReadChapter(-1) },
                    onReport = reportSource,
                    menuOpen = menuOpen,
                    onMenuOpen = { menuOpen = true },
                    onMenuDismiss = { menuOpen = false },
                    chapterListOrder = chapterListOrder,
                    onToggleChapterOrder = onToggleChapterOrder,
                    onOpenInBrowser = onUnlock,
                )
            }

            if (state.chapters.isNotEmpty() && unreadMissing > 0 && !batch.isRunning) {
                item(key = "chapter-tools") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TomeChip(
                            label = "Download Unread ($unreadMissing)",
                            selected = true,
                            onClick = { showDownloadUnreadConfirm = true },
                        )
                    }
                }
            }

            when {
                state.isLoading -> {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                state.error != null -> {
                    item(key = "error") {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "If this site is protected by a \"Just a moment\" / Cloudflare check, " +
                                    "open it in the browser and solve the check, then come back and retry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.loadChapters(scraper, series) }) { Text("Retry") }
                                TextButton(onClick = { onUnlock(series.url) }) { Text("Unlock in browser") }
                            }
                        }
                    }
                }
                else -> {
                    item(key = "chapters-header") {
                        TomeSectionHeader(
                            title = "Chapters",
                            action = "${state.chapters.size} total",
                        )
                    }

                    if (batch.isRunning) {
                        item(key = "batch-progress") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Downloading ${batch.completed + 1}/${batch.total}...",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { vm.cancelBatchDownload() }) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { batch.completed.toFloat() / batch.total.toFloat() },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    val downloadedIds = state.downloadedIds
                    val downloadingIds = state.downloadingIds
                    val readIds = state.readIds

                    itemsIndexed(
                        items = displayChapters,
                        key = { _, item -> item.chapter.url },
                    ) { _, item ->
                        val chapter = item.chapter
                        ChapterRow(
                            title = tomeDisplayTitle(
                                chapter.title,
                                fallbackUrl = chapter.url,
                                fallback = "Chapter ${item.sourceIndex + 1}",
                            ),
                            number = chapter.number,
                            date = chapter.releaseDate,
                            isDownloaded = chapter.url in downloadedIds,
                            isDownloading = chapter.url in downloadingIds,
                            isRead = chapter.url in readIds,
                            onDownload = { vm.downloadChapter(scraper, series, chapter, item.sourceIndex) },
                            onRead = { onReadChapter(item.sourceIndex) },
                        )
                    }
                }
            }
            }
        }
    }
}

private data class DisplayScrapedChapter(
    val chapter: ScrapedChapter,
    val sourceIndex: Int,
)

private fun orderedScrapedChapters(
    chapters: List<ScrapedChapter>,
    order: ChapterListOrder,
): List<DisplayScrapedChapter> {
    val sorted = chapters.mapIndexed { index, chapter ->
        DisplayScrapedChapter(chapter, index)
    }.sortedWith(
        compareBy<DisplayScrapedChapter> {
            ChapterOrdering.sortNumber(it.chapter.title, it.chapter.number) ?: Double.MAX_VALUE
        }.thenBy(NaturalOrder) { it.chapter.title },
    )
    return if (order == ChapterListOrder.ASCENDING) sorted else sorted.asReversed()
}

@Composable
private fun ScraperSeriesHero(
    scraper: Scraper,
    series: ScrapedSeries,
    readCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onReport: () -> Unit,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    chapterListOrder: ChapterListOrder,
    onToggleChapterOrder: () -> Unit,
    /** Opens a URL in the in-app browser. */
    onOpenInBrowser: (String) -> Unit,
) {
    val displayTitle = tomeDisplayTitle(series.title, series.url)
    val gradient = tomeGradient(displayTitle)
    val coverFallbacks = tomeCoverFallbackModels(series.coverUrl, series.url)
    val progress = if (totalCount > 0) readCount.toFloat() / totalCount else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
    ) {
        if (series.coverUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(series.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
        TomeGlassCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp),
        )
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Opening the title on its own site is a first-class action, not a
                // menu item: it is how a reader checks the source, and how a
                // Cloudflare check gets cleared so the scraper can work again.
                TomeGlassCircleButton(
                    icon = Icons.Default.Public,
                    contentDescription = "Open on ${scraper.name}",
                    onClick = { onOpenInBrowser(series.externalUrl ?: series.url) },
                )
                TomeGlassCircleButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "More",
                    onClick = onMenuOpen,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text("Chapter order: ${chapterListOrder.label}") },
                    onClick = {
                        onToggleChapterOrder()
                        onMenuDismiss()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy source report") },
                    onClick = {
                        onReport()
                        onMenuDismiss()
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TomeCoverArt(
                title = displayTitle,
                model = series.coverUrl,
                fallbackModels = coverFallbacks,
                progress = progress,
                modifier = Modifier.width(96.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = series.author ?: scraper.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TomePill(label = series.category.label.uppercase())
                    TomePill(label = scraper.name.take(12))
                    series.status?.let {
                        TomePill(
                            label = it.take(16),
                            color = Color(0xFF4ADE80).copy(alpha = 0.2f),
                            contentColor = Color(0xFF4ADE80),
                        )
                    }
                }
            }
        }
    }
    TomeStatsRow(
        stats = listOf(
            "Chapters" to totalCount.toString(),
            "Read" to readCount.toString(),
            "Progress" to "${(progress * 100).toInt()}%",
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
        )
    }
    TomePrimaryButton(
        text = if (readCount == 0) "Start Reading" else "Continue Reading",
        onClick = onStart,
        enabled = totalCount > 0,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        leading = {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
        },
    )
}

@Composable
private fun ChapterRow(
    title: String,
    number: Double?,
    date: String?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isRead: Boolean,
    onDownload: () -> Unit,
    onRead: () -> Unit = {},
) {
    TomeGlassSurface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Unread indicator: filled accent dot when unread, faint when read.
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, end = 8.dp)
                        .size(8.dp)
                        .background(
                            color = if (isRead) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(99.dp),
                        ),
                )
                Text(
                    text = number?.toInt()?.toString() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp).padding(top = 2.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isRead) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (isRead) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (date != null || number != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            if (number != null) {
                                Text(
                                    text = if (isRead) "Read" else "New",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF4ADE80),
                                )
                            }
                            if (date != null) {
                                Text(
                                    text = date.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRead,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Read", style = MaterialTheme.typography.labelSmall)
                }
                when {
                    isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    isDownloaded -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(99.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Downloaded", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Get", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
