package com.tome.reader.ui.library

import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.tome.reader.domain.model.LibraryItem
import com.tome.reader.domain.model.LibraryLayout
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.components.tomeDisplayTitle
import com.tome.reader.ui.components.tomeGradient
import com.tome.reader.ui.components.tomeInitials
import com.tome.reader.ui.theme.GlassSpec
import java.io.File

/**
 * Presentation details shared by every layout, read off whichever kind of
 * [LibraryItem] we are showing so the views below stay layout-only.
 */
private data class ItemFacts(
    val title: String,
    // Any? because a follow's cover is a remote URL while an import is a File;
    // Coil accepts either as a model.
    val coverModel: Any?,
    val badge: String,
    val meta: String,
    val progress: Float,
    val unread: Int,
    val isNew: Boolean,
)

private fun factsOf(item: LibraryItem): ItemFacts = when (item) {
    is LibraryItem.BookItem -> ItemFacts(
        title = tomeDisplayTitle(item.book.title, fallback = "Untitled"),
        coverModel = item.book.coverPath?.let(::File)?.takeIf { it.exists() },
        badge = item.book.format.label,
        meta = when {
            item.book.progress.percent >= 0.99f -> "Finished"
            item.book.progress.percent > 0f -> "${(item.book.progress.percent * 100).toInt()}%"
            else -> "Not started"
        },
        progress = item.book.progress.percent,
        unread = 0,
        isNew = item.book.progress.percent <= 0f,
    )

    is LibraryItem.SeriesItem -> ItemFacts(
        title = tomeDisplayTitle(item.series.title, fallback = "Untitled"),
        coverModel = item.series.coverPath?.let(::File)?.takeIf { it.exists() },
        badge = "SERIES",
        meta = "${item.series.readCount}/${item.series.chapterCount} chapters",
        progress = item.series.progress,
        unread = item.series.unreadCount,
        isNew = item.series.progress <= 0f,
    )

    is LibraryItem.FollowItem -> ItemFacts(
        title = tomeDisplayTitle(item.follow.title, fallback = "Untitled"),
        coverModel = item.follow.coverUrl,
        badge = "ONLINE",
        meta = item.follow.author ?: item.follow.category.label,
        progress = 0f,
        unread = (item.follow.latestChapters - item.follow.seenChapters).coerceAtLeast(0),
        isNew = item.follow.latestChapters > item.follow.seenChapters,
    )
}

/** Status text and colour, matching the design's `status` / `statusColor`. */
@Composable
private fun statusOf(facts: ItemFacts): Pair<String, Color> = when {
    facts.progress >= 0.99f -> "Read" to MaterialTheme.colorScheme.onSurfaceVariant
    facts.progress > 0f -> "${(facts.progress * 100).toInt()}%" to MaterialTheme.colorScheme.primary
    else -> "New" to Color(0xFF6FA57E) // matte green, not neon
}

/**
 * The cover plate exactly as the design draws it: a 140° gradient with radial
 * shine and centred initials, an optional dark badge top-left, a tag pill
 * top-right, and a 3dp progress hairline pinned to the bottom.
 */
@Composable
private fun SpecCover(
    facts: ItemFacts,
    initialsSize: TextUnit,
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = GlassSpec.CoverRadius,
    showBadge: Boolean = true,
    showProgress: Boolean = true,
    shadowElevation: androidx.compose.ui.unit.Dp = GlassSpec.ShadowCover,
) {
    val gradient = tomeGradient(facts.title)
    Box(
        modifier = modifier
            .shadow(shadowElevation, RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tomeInitials(facts.title),
                fontSize = initialsSize,
                fontWeight = androidx.compose.ui.text.font.FontWeight.W800,
                color = Color.White.copy(alpha = 0.86f),
            )
        }

        facts.coverModel?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showBadge) {
            Text(
                text = facts.badge.uppercase(),
                style = GlassSpec.CoverBadge,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(GlassSpec.CoverBadgeRadius))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(
                        0.5.dp,
                        Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(GlassSpec.CoverBadgeRadius),
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )

            val tag = when {
                facts.unread > 0 -> if (facts.unread > 99) "99+" else facts.unread.toString()
                facts.isNew -> "NEW"
                else -> null
            }
            if (tag != null) {
                Text(
                    text = tag,
                    style = GlassSpec.CoverBadge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(GlassSpec.ChipRadius))
                        .background(
                            if (tag == "NEW") MaterialTheme.colorScheme.primary else Color(0xFFE11D48)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }

        if (showProgress) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(GlassSpec.CoverProgressHeight)
                    .background(Color.White.copy(alpha = 0.14f)),
            ) {
                if (facts.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(facts.progress.coerceIn(0f, 1f))
                            .height(GlassSpec.CoverProgressHeight)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** The design's layout row: one glass track holding four equal segments. */
@Composable
fun LibraryLayoutSwitcher(
    selected: LibraryLayout,
    onSelect: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    TomeGlassSurface(
        shape = RoundedCornerShape(GlassSpec.SwitcherRadius),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GlassSpec.SwitcherPadding),
            horizontalArrangement = Arrangement.spacedBy(GlassSpec.SwitcherGap),
        ) {
            LibraryLayout.entries.forEach { layout ->
                val active = layout == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(GlassSpec.SwitcherSegmentRadius))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable { onSelect(layout) }
                        .padding(
                            top = GlassSpec.SwitcherSegmentTop,
                            bottom = GlassSpec.SwitcherSegmentBottom,
                        ),
                ) {
                    Text(
                        text = layout.label,
                        style = GlassSpec.SwitcherLabel,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** `repeat(3, minmax(0,1fr))` with `gap:14px 12px` and `padding:16px`. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridView(
    items: List<LibraryItem>,
    onOpen: (LibraryItem) -> Unit,
    onLongPress: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GlassSpec.GridColumns),
        contentPadding = PaddingValues(
            start = GlassSpec.GridPadding,
            end = GlassSpec.GridPadding,
            top = GlassSpec.GridPadding,
            bottom = GlassSpec.NavClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(GlassSpec.GridColumnGap),
        verticalArrangement = Arrangement.spacedBy(GlassSpec.GridRowGap),
        modifier = modifier.fillMaxSize(),
    ) {
        items(items.size, key = { items[it].key }) { index ->
            val item = items[index]
            val facts = factsOf(item)
            Column(
                modifier = Modifier.combinedClickable(
                    onClick = { onOpen(item) },
                    onLongClick = { onLongPress(item) },
                ),
            ) {
                SpecCover(
                    facts = facts,
                    initialsSize = GlassSpec.InitialsGrid,
                    modifier = Modifier.fillMaxWidth().aspectRatio(GlassSpec.CoverAspect),
                )
                Text(
                    text = facts.title,
                    style = GlassSpec.GridTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = facts.meta,
                    style = GlassSpec.GridMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Horizontal rails, series first and then grouped by comic format. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryShelvesView(
    items: List<LibraryItem>,
    onOpen: (LibraryItem) -> Unit,
    onLongPress: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shelves: List<Pair<String, List<LibraryItem>>> = remember(items) {
        val series = items.filterIsInstance<LibraryItem.SeriesItem>()
        val books = items.filterIsInstance<LibraryItem.BookItem>()
        // Follows used to be filtered out here, so a library made only of followed
        // series produced no shelves at all and the view looked broken.
        val follows = items.filterIsInstance<LibraryItem.FollowItem>()
        buildList {
            if (follows.isNotEmpty()) add("Following" to follows)
            if (series.isNotEmpty()) add("Series" to series)
            books.groupBy { it.book.format.label }
                .toSortedMap()
                .forEach { (label, group) -> add(label to group) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = GlassSpec.ChipRowTop, bottom = GlassSpec.NavClearance),
    ) {
        items(shelves, key = { it.first }) { (label, shelfItems) ->
            Column(modifier = Modifier.padding(bottom = GlassSpec.ShelfBottomGap)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = GlassSpec.ShelfTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${shelfItems.size}",
                        style = GlassSpec.ShelfCount,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(GlassSpec.ShelfGap),
                ) {
                    items(shelfItems, key = { it.key }) { item ->
                        val facts = factsOf(item)
                        Column(
                            modifier = Modifier
                                .width(GlassSpec.ShelfItemWidth)
                                .combinedClickable(
                                    onClick = { onOpen(item) },
                                    onLongClick = { onLongPress(item) },
                                ),
                        ) {
                            SpecCover(
                                facts = facts,
                                initialsSize = GlassSpec.InitialsShelf,
                                modifier = Modifier.fillMaxWidth().aspectRatio(GlassSpec.CoverAspect),
                            )
                            Text(
                                text = facts.title,
                                style = GlassSpec.ShelfItemTitle,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dense glass rows: 44×66 cover, title, badge · meta, progress, status. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListView(
    items: List<LibraryItem>,
    onOpen: (LibraryItem) -> Unit,
    onLongPress: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = GlassSpec.GridRowGap,
            bottom = GlassSpec.NavClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(GlassSpec.ListGap),
    ) {
        items(items, key = { it.key }) { item ->
            val facts = factsOf(item)
            val (status, statusColor) = statusOf(facts)
            TomeGlassSurface(
                shape = RoundedCornerShape(GlassSpec.ListRowRadius),
                // No elevation: on a translucent panel the shadow shows through
                // and leaves a visible rectangle. The hairline and top shine carry
                // the edge instead.
                elevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    // See the hero: pre-clipping cuts the surface's shadow and
                    // leaves a rectangle showing through the glass.
                    .combinedClickable(
                        onClick = { onOpen(item) },
                        onLongClick = { onLongPress(item) },
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(GlassSpec.ListRowPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpecCover(
                        facts = facts,
                        initialsSize = GlassSpec.InitialsList,
                        radius = GlassSpec.ListCoverRadius,
                        showBadge = false,
                        showProgress = false,
                        modifier = Modifier
                            .width(GlassSpec.ListCoverWidth)
                            .height(GlassSpec.ListCoverHeight),
                    )
                    Column(
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = facts.title,
                            style = GlassSpec.ListTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${facts.badge} · ${facts.meta}",
                            style = GlassSpec.ListMeta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ProgressHairline(facts.progress)
                    }
                    Text(
                        text = status,
                        style = GlassSpec.ListStatus,
                        color = statusColor,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

/**
 * A browsable deck: the next few covers fan out behind the front one, which is
 * the only tappable card. Offsets follow the design's 26dp step, 0.06 scale
 * falloff and 0.22 alpha falloff.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryCardDeck(
    items: List<LibraryItem>,
    onOpen: (LibraryItem) -> Unit,
    onLongPress: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by rememberSaveable(items.size) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, items.lastIndex)
    val depth = minOf(4, items.size)
    // Horizontal drag of the front card. The arrows still work; this makes the
    // deck behave the way a deck looks like it should.
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 26.dp, bottom = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            for (offset in depth - 1 downTo 0) {
                val item = items[(safeIndex + offset) % items.size]
                val facts = factsOf(item)
                Box(
                    modifier = Modifier
                        .zIndex((depth - offset).toFloat())
                        .width(GlassSpec.DeckCardWidth)
                        .graphicsLayer {
                            translationX = offset * GlassSpec.DeckStep.toPx() +
                                if (offset == 0) dragX.value else 0f
                            // A little tilt while dragging sells the card leaving.
                            if (offset == 0) rotationZ = dragX.value / 60f
                            val s = 1f - offset * GlassSpec.DeckScaleStep
                            scaleX = s
                            scaleY = s
                            alpha = 1f - offset * GlassSpec.DeckAlphaStep
                        }
                        .then(
                            if (offset == 0) {
                                Modifier
                                    .pointerInput(items.size, safeIndex) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                scope.launch {
                                                    val throw_ = size.width * 0.26f
                                                    if (abs(dragX.value) > throw_) {
                                                        val dir = if (dragX.value > 0f) 1 else -1
                                                        dragX.animateTo(
                                                            dir * size.width * 1.25f,
                                                            tween(190),
                                                        )
                                                        // Dragging right reaches back
                                                        // for the previous card.
                                                        index = if (dir > 0) {
                                                            (safeIndex + items.size - 1) % items.size
                                                        } else {
                                                            (safeIndex + 1) % items.size
                                                        }
                                                        dragX.snapTo(0f)
                                                    } else {
                                                        dragX.animateTo(0f, spring())
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                scope.launch { dragX.animateTo(0f, spring()) }
                                            },
                                        ) { _, delta ->
                                            scope.launch { dragX.snapTo(dragX.value + delta) }
                                        }
                                    }
                                    .combinedClickable(
                                        onClick = { onOpen(item) },
                                        onLongClick = { onLongPress(item) },
                                    )
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    SpecCover(
                        facts = facts,
                        initialsSize = GlassSpec.InitialsDeck,
                        radius = GlassSpec.DeckCardRadius,
                        shadowElevation = GlassSpec.ShadowDeck,
                        modifier = Modifier.fillMaxWidth().aspectRatio(GlassSpec.CoverAspect),
                    )
                    // The design lays the title over the bottom of the front card.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                )
                            )
                            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
                    ) {
                        Column {
                            Text(
                                text = facts.title,
                                style = GlassSpec.HeroTitle.copy(fontSize = 15.sp),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = facts.meta,
                                style = GlassSpec.ListMeta,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(bottom = GlassSpec.NavClearance),
        ) {
            DeckButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous cover") {
                index = (safeIndex + items.size - 1) % items.size
            }
            Text(
                text = "${safeIndex + 1} / ${items.size}",
                style = GlassSpec.DeckLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeckButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next cover") {
                index = (safeIndex + 1) % items.size
            }
        }
    }
}

@Composable
private fun DeckButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    TomeGlassSurface(
        shape = CircleShape,
        modifier = Modifier.size(GlassSpec.DeckButton).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ProgressHairline(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassSpec.CoverProgressHeight)
            .clip(RoundedCornerShape(GlassSpec.ChipRadius))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(GlassSpec.CoverProgressHeight)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
