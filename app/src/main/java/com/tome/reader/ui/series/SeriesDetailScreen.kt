package com.tome.reader.ui.series

import com.tome.reader.ui.theme.GlassSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tome.reader.appContainer
import com.tome.reader.data.source.ChapterOrdering
import com.tome.reader.data.source.NaturalOrder
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.ChapterListOrder
import com.tome.reader.domain.model.Series
import com.tome.reader.ui.components.CenteredMessage
import com.tome.reader.ui.components.TomeCoverArt
import com.tome.reader.ui.components.TomeGlassCircleButton
import com.tome.reader.ui.components.TomeGlassBackdrop
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.components.TomePill
import com.tome.reader.ui.components.TomePrimaryButton
import com.tome.reader.ui.components.TomeSectionHeader
import com.tome.reader.ui.components.TomeStatsRow
import com.tome.reader.ui.components.tomeGradient
import com.tome.reader.ui.components.tomeDisplayTitle
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit,
) {
    val context = LocalContext.current
    val vm: SeriesViewModel = viewModel(factory = SeriesViewModel.factory(context.appContainer, seriesId))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settingsRepository = context.appContainer.settingsRepository
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val displayChapters = remember(state.chapters, settings.chapterListOrder) {
        orderedLocalChapters(state.chapters, settings.chapterListOrder)
    }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedChapter by remember { mutableStateOf<Book?>(null) }

    TomeGlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            when {
                state.loading -> CenteredMessage("Loading")
                state.series == null -> CenteredMessage("Series not found")
                else -> LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                val series = state.series!!
                item {
                    SeriesHero(
                        series = series,
                        onBack = onBack,
                        menuOpen = menuOpen,
                        onMenuOpen = { menuOpen = true },
                        onMenuDismiss = { menuOpen = false },
                        onRemove = {
                            menuOpen = false
                            confirmDelete = true
                        },
                        chapterListOrder = settings.chapterListOrder,
                        onToggleChapterOrder = {
                            scope.launch {
                                settingsRepository.setChapterListOrder(settings.chapterListOrder.toggled())
                            }
                        },
                        onContinue = { resumeChapterId(state.chapters)?.let(onOpenChapter) },
                    )
                }
                item {
                    TomeSectionHeader(
                        title = "Chapters",
                        action = "${series.chapterCount} total",
                    )
                }
                items(displayChapters, key = { it.id }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        onClick = { onOpenChapter(chapter.id) },
                        onLongClick = { selectedChapter = chapter },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove series") },
            text = { Text("Remove this series and all its chapters from your library? The original files are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSeries()
                    onBack()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    selectedChapter?.let { chapter ->
        ChapterActionsSheet(
            chapter = chapter,
            onOpen = {
                val id = chapter.id
                selectedChapter = null
                onOpenChapter(id)
            },
            onMarkRead = {
                vm.markRead(chapter.id)
                selectedChapter = null
            },
            onMarkUnread = {
                vm.markUnread(chapter.id)
                selectedChapter = null
            },
            onDismiss = { selectedChapter = null },
        )
    }
}

private fun orderedLocalChapters(
    chapters: List<Book>,
    order: ChapterListOrder,
): List<Book> {
    val sorted = chapters.sortedWith(
        compareBy<Book> { ChapterOrdering.numberFromTitle(it.title) ?: Double.MAX_VALUE }
            .thenBy(NaturalOrder) { it.title }
            .thenBy { it.seriesOrder },
    )
    return if (order == ChapterListOrder.ASCENDING) sorted else sorted.asReversed()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterActionsSheet(
    chapter: Book,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isRead = chapter.progress.percent >= 0.95f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                tomeDisplayTitle(chapter.title, fallback = "Chapter ${chapter.seriesOrder + 1}"),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            SheetAction(text = "Open", onClick = onOpen)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (isRead) {
                SheetAction(text = "Mark as unread", onClick = onMarkUnread)
            } else {
                SheetAction(text = "Mark as read", onClick = onMarkRead)
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun SeriesHero(
    series: Series,
    onBack: () -> Unit,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRemove: () -> Unit,
    chapterListOrder: ChapterListOrder,
    onToggleChapterOrder: () -> Unit,
    onContinue: () -> Unit,
) {
    val cover = series.coverPath?.let { File(it) }?.takeIf { it.exists() }
    val displayTitle = tomeDisplayTitle(series.title, fallback = "Untitled")
    val gradient = tomeGradient(displayTitle)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.68f),
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
            TomeGlassCircleButton(
                icon = Icons.Default.MoreVert,
                contentDescription = "More",
                onClick = onMenuOpen,
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text("Chapter order: ${chapterListOrder.label}") },
                    onClick = {
                        onToggleChapterOrder()
                        onMenuDismiss()
                    },
                )
                DropdownMenuItem(text = { Text("Remove series") }, onClick = onRemove)
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
                model = cover,
                progress = series.progress,
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(10.dp)),
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
                    text = kindLabel(series),
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
                    TomePill(label = kindLabel(series).uppercase())
                    TomePill(
                        label = if (series.unreadCount > 0) "Ongoing" else "Complete",
                        color = Color(0xFF4ADE80).copy(alpha = 0.2f),
                        contentColor = Color(0xFF4ADE80),
                    )
                }
            }
        }
    }

    TomeStatsRow(
        stats = listOf(
            "Chapters" to series.chapterCount.toString(),
            "Read" to series.readCount.toString(),
            "Progress" to "${(series.progress * 100).roundToInt()}%",
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
                .fillMaxWidth(series.progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
        )
    }
    TomePrimaryButton(
        text = if (series.readCount == 0) "Start Reading" else "Continue Reading",
        onClick = onContinue,
        enabled = series.chapterCount > 0,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        leading = {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(chapter: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    val displayTitle = tomeDisplayTitle(chapter.title, fallback = "Chapter ${chapter.seriesOrder + 1}")
    TomeGlassSurface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (chapter.seriesOrder + 1).toString(),
                style = GlassSpec.ChapterNumber,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp),
            )
            Text(
                text = displayTitle,
                style = GlassSpec.ListTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = chapterStatus(chapter),
                style = GlassSpec.ChapterStatus,
                color = when {
                    chapter.progress.percent >= 0.95f -> MaterialTheme.colorScheme.onSurfaceVariant
                    chapter.progress.percent > 0.0001f -> MaterialTheme.colorScheme.primary
                    else -> Color(0xFF4ADE80)
                },
            )
        }
    }
}

private fun kindLabel(series: Series): String =
    series.kind.name.lowercase().replaceFirstChar { it.uppercase() }

private fun chapterStatus(book: Book): String {
    val percent = book.progress.percent
    return when {
        percent >= 0.95f -> "Read"
        percent > 0.0001f -> "${(percent * 100).roundToInt()}%"
        else -> "New"
    }
}
