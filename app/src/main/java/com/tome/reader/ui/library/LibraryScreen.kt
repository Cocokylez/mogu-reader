package com.tome.reader.ui.library

import androidx.compose.ui.draw.shadow
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tome.reader.appContainer
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.domain.model.Collection
import com.tome.reader.domain.model.LibraryItem
import com.tome.reader.domain.model.LibraryLayout
import com.tome.reader.domain.model.Series
import com.tome.reader.domain.model.SortOrder
import com.tome.reader.scraper.BookmarkedSeries
import com.tome.reader.ui.components.BookCoverCard
import com.tome.reader.ui.components.CenteredMessage
import com.tome.reader.ui.components.SeriesCoverCard
import com.tome.reader.ui.components.TomeCoverArt
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.theme.GlassSpec
import com.tome.reader.ui.components.TomeHeader
import com.tome.reader.ui.components.TomeIconButton
import com.tome.reader.ui.components.TomeInlineMeta
import com.tome.reader.ui.components.TomePill
import com.tome.reader.ui.components.tomeDisplayTitle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenBrowser: (() -> Unit)? = null,
    onOpenOnlineSeries: (BookmarkedSeries) -> Unit = {},
) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(context.appContainer))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.import(files = uris, trees = emptyList()) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.import(files = emptyList(), trees = listOf(uri)) }

    var searchActive by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var showCreateCollection by remember { mutableStateOf(false) }
    var collectionPendingDelete by remember { mutableStateOf<Collection?>(null) }
    var pendingUnfollow by remember { mutableStateOf<BookmarkedSeries?>(null) }

    val openItem: (LibraryItem) -> Unit = { item ->
        when (item) {
            is LibraryItem.BookItem -> onOpenBook(item.book.id)
            is LibraryItem.SeriesItem -> onOpenSeries(item.series.id)
            is LibraryItem.FollowItem -> {
                vm.markFollowSeen(item.follow.scraperId, item.follow.url)
                onOpenOnlineSeries(item.follow)
            }
        }
    }
    val longPressItem: (LibraryItem) -> Unit = { item ->
        when (item) {
            is LibraryItem.BookItem -> selectedBook = item.book
            is LibraryItem.SeriesItem -> selectedSeries = item.series
            is LibraryItem.FollowItem -> pendingUnfollow = item.follow
        }
    }
    val resumeItem = remember(state.items) {
        state.items
            .filter { item -> item.lastReadAt != null || item.readingProgress() > 0f }
            .maxByOrNull { item -> item.lastReadAt ?: Long.MIN_VALUE }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TomeHeader(title = "Library") {
                TomeIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick = {
                    searchActive = !searchActive
                    if (!searchActive) vm.setSearch("")
                    },
                )

                SortAction(current = state.query.sort, onSelect = vm::setSort)
                FilterAction(current = state.query.formatFilter, onSelect = vm::setFormatFilter)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(GlassSpec.FabRadius),
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier
                    .padding(bottom = GlassSpec.FabBottom - 16.dp)
                    .size(GlassSpec.FabSize)
                    .shadow(
                        elevation = GlassSpec.ShadowFab,
                        shape = RoundedCornerShape(GlassSpec.FabRadius),
                        clip = false,
                        // 0.5 alpha, as the design specifies. At full strength the
                        // halo reached the nav pill and bloomed through its blur.
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add manga or manhwa")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            if (searchActive) {
                SearchField(value = state.query.search, onValueChange = vm::setSearch)
            }

            CollectionFilterRow(
                collections = state.collections,
                selectedId = state.query.collectionId,
                onSelect = vm::setCollectionFilter,
                onNew = { showCreateCollection = true },
                onLongPress = { collectionPendingDelete = it },
            )

            if (resumeItem != null) {
                ContinueReadingHero(
                    item = resumeItem,
                    onClick = { openItem(resumeItem) },
                )
            } else {
                // Nothing local in progress: fall back to the most recently
                // opened followed series so the hero still reads
                // "CONTINUE READING" as the design does.
                val recentFollow = remember(state.followed) {
                    state.followed.maxByOrNull { it.lastOpenedAt }
                }
                recentFollow?.let { follow ->
                    ContinueReadingHeroRemote(
                        title = tomeDisplayTitle(follow.title),
                        coverUrl = follow.coverUrl,
                        meta = follow.author ?: follow.category.label,
                        onClick = {
                            vm.markFollowSeen(follow.scraperId, follow.url)
                            onOpenOnlineSeries(follow)
                        },
                    )
                }
            }

            if (state.hasAnyItems) {
                LibraryLayoutSwitcher(
                    selected = layout,
                    onSelect = vm::setLayout,
                    modifier = Modifier.padding(
                        start = GlassSpec.SwitcherMarginSide,
                        end = GlassSpec.SwitcherMarginSide,
                        top = GlassSpec.SwitcherMarginTop,
                        bottom = GlassSpec.SwitcherMarginBottom,
                    ),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CenteredMessage("Loading")
                    !state.hasAnyItems -> CenteredMessage(
                        "Your manga and manhwa library starts here.\nTap + to import CBZ, CBR, ZIP, PDF, or image folders, or follow an online series."
                    )
                    state.items.isEmpty() -> CenteredMessage(
                        if (state.query.search.isNotBlank() || state.query.formatFilter != null || state.query.collectionId != null) "Nothing matches your filters."
                        else "Your followed series and imports live here together.\nTap + to import local comics too."
                    )
                    else -> {
                        // Opening and long-pressing behave the same in every layout,
                        // so each view only has to describe its own arrangement.
                        when (layout) {
                            LibraryLayout.LIST -> LibraryListView(
                                items = state.items,
                                onOpen = openItem,
                                onLongPress = longPressItem,
                            )

                            LibraryLayout.SHELVES -> LibraryShelvesView(
                                items = state.items,
                                onOpen = openItem,
                                onLongPress = longPressItem,
                            )

                            LibraryLayout.CARDS -> LibraryCardDeck(
                                items = state.items,
                                onOpen = openItem,
                                onLongPress = longPressItem,
                            )

                            LibraryLayout.GRID -> LibraryGridView(
                                items = state.items,
                                onOpen = openItem,
                                onLongPress = longPressItem,
                            )
                        }
                    }
                }
            }
        }
    }

    if (addMenu) {
        ImportComicSheet(
            onFiles = {
                addMenu = false
                pickFiles.launch(arrayOf("*/*"))
            },
            onFolder = {
                addMenu = false
                pickFolder.launch(null)
            },
            onBrowse = onOpenBrowser?.let { browse ->
                {
                    addMenu = false
                    browse()
                }
            },
            onDismiss = { addMenu = false },
        )
    }

    selectedBook?.let { book ->
        BookActionsSheet(
            book = book,
            collections = state.collections,
            membership = state.membership[book.id].orEmpty(),
            onOpen = {
                selectedBook = null
                onOpenBook(book.id)
            },
            onToggleCollection = { collectionId, inIt ->
                vm.setBookInCollection(book.id, collectionId, inIt)
            },
            onNewCollection = { showCreateCollection = true },
            onDelete = {
                vm.delete(book)
                selectedBook = null
            },
            onDismiss = { selectedBook = null },
        )
    }

    selectedSeries?.let { series ->
        SeriesActionsSheet(
            series = series,
            onOpen = {
                selectedSeries = null
                onOpenSeries(series.id)
            },
            onDelete = {
                vm.deleteSeries(series.id)
                selectedSeries = null
            },
            onDismiss = { selectedSeries = null },
        )
    }

    if (showCreateCollection) {
        TextInputDialog(
            title = "New collection",
            confirmLabel = "Create",
            onConfirm = {
                vm.createCollection(it)
                showCreateCollection = false
            },
            onDismiss = { showCreateCollection = false },
        )
    }

    collectionPendingDelete?.let { collection ->
        AlertDialog(
            onDismissRequest = { collectionPendingDelete = null },
            title = { Text("Delete collection") },
            text = { Text("Delete \"${collection.name}\"? Comics are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCollection(collection.id)
                    collectionPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { collectionPendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    pendingUnfollow?.let { series ->
        AlertDialog(
            onDismissRequest = { pendingUnfollow = null },
            title = { Text("Remove from Library") },
            text = { Text("Stop following \"${series.title}\"? Downloaded chapters are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.unfollow(series.scraperId, series.url)
                    pendingUnfollow = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnfollow = null }) { Text("Cancel") }
            },
        )
    }
}

private fun LibraryItem.readingProgress(): Float = when (this) {
    is LibraryItem.BookItem -> book.progress.percent
    is LibraryItem.SeriesItem -> series.progress
    // Chapter-level progress for an online follow lives on the source, not here.
    is LibraryItem.FollowItem -> 0f
}

@Composable
private fun ContinueReadingHero(item: LibraryItem, onClick: () -> Unit) {
    val title = tomeDisplayTitle(item.title)
    val progress = item.readingProgress().coerceIn(0f, 1f)
    // Any?: imports resolve to a File, follows to a remote URL. Coil takes either.
    val cover: Any? = item.coverPath?.let(::File)?.takeIf(File::exists)
        ?: (item as? LibraryItem.FollowItem)?.follow?.coverUrl
    val badge: String
    val meta: String
    when (item) {
        is LibraryItem.BookItem -> {
            badge = item.book.format.label
            meta = "Page ${(item.book.progress.primary + 1).coerceAtMost(item.book.entryCount.coerceAtLeast(1))} of ${item.book.entryCount.coerceAtLeast(1)}"
        }
        is LibraryItem.SeriesItem -> {
            badge = "Series"
            meta = "${item.series.readCount} of ${item.series.chapterCount} chapters"
        }
        is LibraryItem.FollowItem -> {
            badge = "Online"
            meta = item.follow.author ?: item.follow.category.label
        }
    }

    // Design: margin 12/16/4, radius 22, glass + a 115° accent wash, 14dp padding.
    TomeGlassSurface(
        shape = RoundedCornerShape(GlassSpec.HeroRadius),
        // See TomeGlassSurface: an elevation shadow shows through a translucent
        // panel and reads as a rectangle inside the card.
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = GlassSpec.HeroMarginSide,
                end = GlassSpec.HeroMarginSide,
                top = GlassSpec.HeroMarginTop,
                bottom = GlassSpec.HeroMarginBottom,
            )
            // No .clip() here: TomeGlassSurface applies its shadow on top of this
            // modifier and clips internally. Clipping first cut the soft shadow off
            // at the shape edge, leaving a hard dark rim and a lighter rectangle
            // showing through the translucent panel.
            .clickable(onClick = onClick),
    ) {
        // linear-gradient(115deg, rgba(124,109,249,0.30), transparent 62%)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                        0.62f to Color.Transparent,
                    )
                ),
        )
        Row(
            modifier = Modifier.padding(GlassSpec.HeroPadding),
            horizontalArrangement = Arrangement.spacedBy(GlassSpec.HeroGap),
        ) {
            TomeCoverArt(
                title = title,
                model = cover,
                compact = true,
                modifier = Modifier
                    .size(width = GlassSpec.HeroCoverWidth, height = GlassSpec.HeroCoverHeight)
                    .clip(RoundedCornerShape(GlassSpec.HeroCoverRadius)),
            )
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(GlassSpec.HeroTextGap),
            ) {
                Text(
                    text = "CONTINUE READING",
                    style = GlassSpec.HeroKicker,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = GlassSpec.HeroTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = GlassSpec.HeroMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(GlassSpec.HeroProgressHeight)
                        .clip(RoundedCornerShape(GlassSpec.ChipRadius))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceAtLeast(0.04f))
                            .height(GlassSpec.HeroProgressHeight)
                            .clip(RoundedCornerShape(GlassSpec.ChipRadius))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(GlassSpec.HeroPlayButton)
                    .shadow(
                        elevation = GlassSpec.ShadowPlay,
                        shape = RoundedCornerShape(GlassSpec.ChipRadius),
                        clip = false,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    .clip(RoundedCornerShape(GlassSpec.ChipRadius))
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Continue",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * The same hero for an online series the reader is part-way through. The local
 * variant is driven by a [LibraryItem]; followed series carry a remote cover and
 * no local progress, so they get their own entry point rather than a fake row.
 */
@Composable
private fun ContinueReadingHeroRemote(
    title: String,
    coverUrl: String?,
    meta: String,
    onClick: () -> Unit,
) {
    TomeGlassSurface(
        shape = RoundedCornerShape(GlassSpec.HeroRadius),
        // See TomeGlassSurface: an elevation shadow shows through a translucent
        // panel and reads as a rectangle inside the card.
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = GlassSpec.HeroMarginSide,
                end = GlassSpec.HeroMarginSide,
                top = GlassSpec.HeroMarginTop,
                bottom = GlassSpec.HeroMarginBottom,
            )
            // No .clip() here: TomeGlassSurface applies its shadow on top of this
            // modifier and clips internally. Clipping first cut the soft shadow off
            // at the shape edge, leaving a hard dark rim and a lighter rectangle
            // showing through the translucent panel.
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                        0.62f to Color.Transparent,
                    )
                ),
        )
        Row(
            modifier = Modifier.padding(GlassSpec.HeroPadding),
            horizontalArrangement = Arrangement.spacedBy(GlassSpec.HeroGap),
        ) {
            TomeCoverArt(
                title = title,
                model = coverUrl,
                compact = true,
                modifier = Modifier
                    .size(width = GlassSpec.HeroCoverWidth, height = GlassSpec.HeroCoverHeight)
                    .clip(RoundedCornerShape(GlassSpec.HeroCoverRadius)),
            )
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(GlassSpec.HeroTextGap),
            ) {
                Text(
                    text = "CONTINUE READING",
                    style = GlassSpec.HeroKicker,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = GlassSpec.HeroTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = GlassSpec.HeroMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(GlassSpec.HeroPlayButton)
                    .shadow(
                        elevation = GlassSpec.ShadowPlay,
                        shape = RoundedCornerShape(GlassSpec.ChipRadius),
                        clip = false,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    .clip(RoundedCornerShape(GlassSpec.ChipRadius))
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Continue",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportComicSheet(
    onFiles: () -> Unit,
    onFolder: () -> Unit,
    onBrowse: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text(
                text = "Add to library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            ImportAction(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                title = "Import comic files",
                detail = "CBZ, CBR, ZIP, and PDF",
                onClick = onFiles,
            )
            ImportAction(
                icon = Icons.Default.Folder,
                title = "Import image folder",
                detail = "Read images in filename order",
                onClick = onFolder,
            )
            onBrowse?.let {
                ImportAction(
                    icon = Icons.Default.Public,
                    title = "Browse online",
                    detail = "AquaReader, MangaDex, and more",
                    onClick = it,
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf("CBZ", "CBR", "ZIP", "PDF", "FOLDER").forEach { format ->
                    TomePill(
                        label = format,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportAction(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    TomeGlassSurface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                TomeInlineMeta(detail, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Horizontal shelf of online series the user follows, shown atop the Library. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SortAction(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TomeIconButton(
        icon = Icons.Default.SortByAlpha,
        contentDescription = "Sort",
        onClick = { open = true },
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        SortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(order.label) },
                trailingIcon = {
                    if (order == current) Icon(Icons.Default.Check, null)
                },
                onClick = {
                    onSelect(order)
                    open = false
                },
            )
        }
    }
}

@Composable
private fun FilterAction(current: BookFormat?, onSelect: (BookFormat?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TomeIconButton(
        icon = Icons.Default.FilterList,
        contentDescription = "Filter",
        onClick = { open = true },
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("All formats") },
            trailingIcon = { if (current == null) Icon(Icons.Default.Check, null) },
            onClick = { onSelect(null); open = false },
        )
        BookFormat.supported.forEach { format ->
            DropdownMenuItem(
                text = { Text(format.label) },
                trailingIcon = { if (current == format) Icon(Icons.Default.Check, null) },
                onClick = { onSelect(format); open = false },
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    TomeGlassSurface(
        shape = RoundedCornerShape(12.dp),
        raised = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(
                            "Search library...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionFilterRow(
    collections: List<Collection>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit,
    onLongPress: (Collection) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            SelectableChip("All", selected = selectedId == null, onClick = { onSelect(null) })
        }
        items(collections, key = { it.id }) { c ->
            SelectableChip(
                label = "${c.name}  ${c.bookCount}",
                selected = selectedId == c.id,
                onClick = { onSelect(c.id) },
                onLongClick = { onLongPress(c) },
            )
        }
        item {
            SelectableChip("+ New", selected = false, onClick = onNew)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(99.dp),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionsSheet(
    book: Book,
    collections: List<Collection>,
    membership: Set<Long>,
    onOpen: () -> Unit,
    onToggleCollection: (Long, Boolean) -> Unit,
    onNewCollection: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            book.author?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SheetRow(text = "Open", onClick = onOpen)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Collections",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (collections.isEmpty()) {
                Text(
                    "No collections yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                collections.forEach { c ->
                    val checked = membership.contains(c.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggleCollection(c.id, it) },
                        )
                        Text(c.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SheetRow(text = "+ New collection", onClick = onNewCollection)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SheetRow(
                text = "Remove from library",
                color = MaterialTheme.colorScheme.error,
                onClick = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove comic") },
            text = { Text("Remove \"${book.title}\" from your library? App downloads are deleted; imported original files are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SheetRow(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesActionsSheet(
    series: Series,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(series.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                text = "${series.chapterCount} chapters, ${series.readCount} read",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SheetRow(text = "Open", onClick = onOpen)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SheetRow(
                text = "Remove series from library",
                color = MaterialTheme.colorScheme.error,
                onClick = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove series") },
            text = {
                Text("Remove \"${series.title}\" and all its chapters from your library? App downloads are deleted; imported original files are kept.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.text.trim()) },
                enabled = text.text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
