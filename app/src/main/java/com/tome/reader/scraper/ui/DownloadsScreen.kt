package com.tome.reader.scraper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tome.reader.data.local.BookEntity
import com.tome.reader.ui.components.TomeCoverArt
import com.tome.reader.ui.components.tomeDisplayTitle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    vm: DownloadsViewModel,
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val downloads by vm.downloads.collectAsState()
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<DownloadsViewModel.DownloadedSeries?>(null) }

    val totalBytes = downloads.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloads")
                        if (downloads.isNotEmpty()) {
                            Text(
                                "${downloads.size} series · ${formatSize(totalBytes)} on device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No downloaded series yet.\nDownload chapters from a series to manage them here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(downloads, key = { it.info.series.id }) { item ->
                    DownloadsSeriesCard(
                        item = item,
                        vm = vm,
                        expanded = expandedId == item.info.series.id,
                        onToggle = {
                            expandedId = if (expandedId == item.info.series.id) null else item.info.series.id
                        },
                        onOpen = { onOpenSeries(item.info.series.id) },
                        onDeleteAll = { pendingDelete = item },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        val title = tomeDisplayTitle(target.info.series.title)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = {
                Text(
                    "Remove all ${target.info.chapterCount} downloaded chapters of “$title”? " +
                        "This frees ${formatSize(target.sizeBytes)} and can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.info.series.id
                    if (expandedId == id) expandedId = null
                    vm.deleteSeries(id)
                    pendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DownloadsSeriesCard(
    item: DownloadsViewModel.DownloadedSeries,
    vm: DownloadsViewModel,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val series = item.info
    val displayTitle = tomeDisplayTitle(series.series.title)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val coverFile = series.series.coverPath?.let { File(it) }?.takeIf { it.exists() }
                TomeCoverArt(
                    title = displayTitle,
                    model = coverFile,
                    compact = true,
                    badge = "Downloaded",
                    modifier = Modifier.size(width = 56.dp, height = 78.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${series.chapterCount} chapters · ${formatSize(item.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    val remaining = (series.chapterCount - series.readCount).coerceAtLeast(0)
                    Text(
                        text = if (remaining == 0) "All read" else "$remaining unread",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = onDeleteAll) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete all downloads",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                val chapters by remember(series.series.id) { vm.chapters(series.series.id) }
                    .collectAsState(initial = emptyList())
                if (chapters.isEmpty()) {
                    Text(
                        "No downloaded chapters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    chapters.forEach { chapter ->
                        ChapterManageRow(
                            chapter = chapter,
                            onDelete = { vm.deleteChapter(chapter.id) },
                        )
                    }
                }
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                ) {
                    Text("Open series")
                }
            }
        }
    }
}

@Composable
private fun ChapterManageRow(
    chapter: BookEntity,
    onDelete: () -> Unit,
) {
    val read = chapter.progressPercent >= 0.95f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (read) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = chapter.title.ifBlank { "Chapter ${chapter.seriesOrder + 1}" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (read) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete chapter",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
