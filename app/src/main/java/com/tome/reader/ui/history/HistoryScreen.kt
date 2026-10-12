package com.tome.reader.ui.history

import com.tome.reader.ui.theme.GlassSpec
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tome.reader.appContainer
import com.tome.reader.domain.model.HistoryEntry
import com.tome.reader.domain.model.OnlineHistoryEntry
import com.tome.reader.ui.components.CenteredMessage
import com.tome.reader.ui.components.TomeCoverArt
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.components.TomeHeader
import com.tome.reader.ui.components.TomeStatsRow
import com.tome.reader.ui.components.tomeDisplayTitle
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenOnline: (OnlineHistoryEntry) -> Unit,
) {
    val context = LocalContext.current
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(context.appContainer))
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TomeHeader("History") },
    ) { padding ->
        when {
            state.loading -> CenteredMessage("Loading")
            else -> {
                val sections = remember(state.items) { historySections(state.items) }
                val stats = remember(state.items) { historyStats(state.items) }
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.tome.reader.ui.theme.GlassSpec.NavClearance),
                ) {
                    item(key = "stats") {
                        TomeGlassSurface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                            raised = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            TomeStatsRow(stats = stats)
                        }
                    }
                    if (state.items.isEmpty()) {
                        item(key = "empty") {
                            TomeGlassSurface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "Your manga and manhwa reading history will appear here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                    sections.forEach { section ->
                        item(key = "section-${section.first}") {
                            Text(
                                text = section.first.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 5.dp),
                            )
                        }
                        items(section.second, key = ::historyKey) { item ->
                            TomeGlassSurface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                when (item) {
                                    is HistoryListItem.Local -> LocalHistoryRow(
                                        entry = item.entry,
                                        onClick = { onOpenBook(item.entry.bookId) },
                                    )
                                    is HistoryListItem.Online -> OnlineHistoryRow(
                                        entry = item.entry,
                                        onClick = { onOpenOnline(item.entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun historyStats(items: List<HistoryListItem>): List<Pair<String, String>> {
    val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    val thisWeek = items.count { it.lastReadAt >= weekAgo }
    val chapters = items.sumOf { item ->
        when (item) {
            is HistoryListItem.Local -> 1
            is HistoryListItem.Online -> item.entry.readChapterIndices.size.coerceAtLeast(1)
        }
    }
    val sources = buildSet {
        items.forEach { item ->
            when (item) {
                is HistoryListItem.Local -> add("Local")
                is HistoryListItem.Online -> add(item.entry.sourceName)
            }
        }
    }.size
    return listOf(
        "This week" to thisWeek.toString(),
        "Chapters" to chapters.toString(),
        "Sources" to sources.toString(),
    )
}

private fun historySections(items: List<HistoryListItem>): List<Pair<String, List<HistoryListItem>>> {
    val startToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startYesterday = startToday - TimeUnit.DAYS.toMillis(1)
    val startWeek = startToday - TimeUnit.DAYS.toMillis(7)
    val labels = listOf("Today", "Yesterday", "This week", "Earlier")
    return items
        .groupBy { item ->
            when {
                item.lastReadAt >= startToday -> "Today"
                item.lastReadAt >= startYesterday -> "Yesterday"
                item.lastReadAt >= startWeek -> "This week"
                else -> "Earlier"
            }
        }
        .let { grouped -> labels.mapNotNull { label -> grouped[label]?.let { label to it } } }
}

private fun historyKey(item: HistoryListItem): String = when (item) {
    is HistoryListItem.Local -> "local-${item.entry.bookId}"
    is HistoryListItem.Online -> "online-${item.entry.scraperId}:${item.entry.seriesUrl}"
}

@Composable
private fun LocalHistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val cover = entry.coverPath?.let { File(it) }?.takeIf { it.exists() }
    val displayTitle = tomeDisplayTitle(entry.title)
    HistoryRowScaffold(
        coverTitle = displayTitle,
        coverModel = cover,
        title = displayTitle,
        subtitle = entry.subtitle,
        meta = "${relativeTime(entry.lastReadAt)} - ${progressLabel(entry.percent)}",
        onClick = onClick,
    )
}

@Composable
private fun OnlineHistoryRow(entry: OnlineHistoryEntry, onClick: () -> Unit) {
    val displayTitle = tomeDisplayTitle(entry.title, entry.seriesUrl)
    HistoryRowScaffold(
        coverTitle = displayTitle,
        coverModel = entry.coverUrl,
        title = displayTitle,
        subtitle = tomeDisplayTitle(
            entry.chapterTitle,
            fallback = "Chapter ${entry.chapterIndex + 1}",
        ),
        meta = "${entry.sourceName} - online - ${relativeTime(entry.lastReadAt)}",
        onClick = onClick,
    )
}

@Composable
private fun HistoryRowScaffold(
    coverTitle: String,
    coverModel: Any?,
    title: String,
    subtitle: String?,
    meta: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TomeCoverArt(
            title = coverTitle,
            model = coverModel,
            compact = true,
            modifier = Modifier.size(width = 42.dp, height = 63.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = GlassSpec.ListTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = GlassSpec.HistoryChapter,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                text = meta,
                style = GlassSpec.HistoryMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

private fun progressLabel(percent: Float): String = when {
    percent >= 0.95f -> "Finished"
    percent > 0.0001f -> "${(percent * 100).roundToInt()}%"
    else -> "Started"
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "Just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
