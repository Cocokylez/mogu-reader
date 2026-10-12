@file:OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)

package com.tome.reader.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.domain.model.ReadingQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Image reader supporting paged (left-to-right / right-to-left) and webtoon
 * (vertical continuous) modes, with pinch-to-zoom and tap zones. The current
 * page/scroll position is persisted as it changes.
 */
@Composable
fun ImageReader(
    state: ReaderUiState.Images,
    vm: ReaderViewModel,
    chapterNav: ChapterNav,
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit,
) {
    val direction = state.book.directionOverride ?: state.settings.defaultDirection
    val isWebtoon = direction == ReadingDirection.WEBTOON
    val pagedImageScale = ContentScale.Fit
    val accent = state.book.accentColor
    var uiVisible by remember(state.book.id) { mutableStateOf(true) }
    var autoScroll by remember(state.book.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val startPrimary = remember(state.book.id) {
        state.book.progress.primary.coerceIn(0, maxOf(0, state.pageCount - 1))
    }

    val startOffset = remember(state.book.id) { state.book.progress.secondary.coerceAtLeast(0) }
    var restored by remember(state.book.id) { mutableStateOf(!isWebtoon || startOffset == 0) }
    val readyPages = remember(state.book.id) { mutableStateMapOf<Int, Boolean>() }

    // Hide the system bars while the reader chrome is hidden.
    ImmersiveMode(enabled = !uiVisible)

    Box(Modifier.fillMaxSize().background(ReaderBlack)) {

        // Both controllers are created up front so the seek bar and volume keys can
        // drive whichever layout is active without conditional hooks.
        val listState = rememberLazyListState(startPrimary)
        val pagerState = rememberPagerState(initialPage = startPrimary) { state.pageCount }
        var previousMode by remember(state.book.id) { mutableStateOf(isWebtoon) }
        LaunchedEffect(isWebtoon) {
            autoScroll = false
            if (previousMode != isWebtoon) {
                if (isWebtoon) listState.scrollToItem(pagerState.currentPage)
                else pagerState.scrollToPage(listState.firstVisibleItemIndex.coerceIn(0, state.pageCount - 1))
                previousMode = isWebtoon
            }
        }
        ReaderAutoScroll(listState, autoScroll && isWebtoon && restored, state.settings.readerAutoScrollSpeed) {
            autoScroll = false
        }
        val latestMode by rememberUpdatedState(isWebtoon)
        DisposableEffect(state.book.id) {
            onDispose {
                if (latestMode && restored) vm.saveWebtoonProgress(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, state.pageCount)
                else if (!latestMode) vm.saveImagesProgress(pagerState.currentPage, state.pageCount)
            }
        }

        if (isWebtoon) {
            WebtoonContent(
                state = state,
                listState = listState,
                nextChapterId = chapterNav.nextId,
                onOpenChapter = onOpenChapter,
                onToggleUi = { uiVisible = !uiVisible },
                onPageReady = { readyPages[it] = true },
            )
            LaunchedEffect(state.book.id) {
                if (!restored) {
                    kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        snapshotFlow { readyPages[startPrimary] == true }.filter { it }.first()
                    }
                    listState.scrollToItem(startPrimary, startOffset)
                    restored = true
                }
            }
            LaunchedEffect(listState, restored) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .sample(500)
                    .collect { (index, offset) -> if (restored) vm.saveWebtoonProgress(index, offset, state.pageCount) }
            }
        } else {
            PagedContent(
                state = state,
                pagerState = pagerState,
                rtl = direction == ReadingDirection.RTL,
                imageScale = pagedImageScale,
                nextChapterId = chapterNav.nextId,
                prevChapterId = chapterNav.prevId,
                onOpenChapter = onOpenChapter,
                onToggleUi = { uiVisible = !uiVisible },
            )
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    vm.saveImagesProgress(page, state.pageCount)
                }
            }
        }

        val currentIndex = if (isWebtoon) listState.firstVisibleItemIndex else pagerState.currentPage

        fun seekTo(target: Int) {
            val t = target.coerceIn(0, maxOf(0, state.pageCount - 1))
            scope.launch { if (isWebtoon) listState.scrollToItem(t) else pagerState.scrollToPage(t) }
        }

        fun turn(forward: Boolean) {
            val next = currentIndex + if (forward) 1 else -1
            when {
                next in 0 until state.pageCount -> seekTo(next)
                forward -> chapterNav.nextId?.let(onOpenChapter)
                else -> chapterNav.prevId?.let(onOpenChapter)
            }
        }

        VolumeKeyPaging(enabled = state.settings.volumeKeyPaging) { forward -> turn(forward) }

        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderOverlayBar(
                title = state.book.title,
                onBack = onBack,
                accent = accent,
                subtitle = when (direction) {
                    ReadingDirection.RTL -> "Right to left"
                    ReadingDirection.WEBTOON -> "Webtoon"
                    else -> "Left to right"
                },
            ) {
                if (isWebtoon) AutoScrollControls(
                    autoScroll, state.settings.readerAutoScrollSpeed,
                    onToggle = { autoScroll = !autoScroll }, onSpeed = vm::setAutoScrollSpeed,
                )
            }
        }

        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderDock(
                current = currentIndex,
                total = state.pageCount,
                accent = accent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                onSeek = { seekTo(it) },
                direction = direction,
                onDirection = vm::setDirectionOverride,
                brightness = state.settings.readerBrightness,
                onBrightness = vm::setReaderBrightness,
                onPrevChapter = chapterNav.prevId?.let { id -> { onOpenChapter(id) } },
                onNextChapter = chapterNav.nextId?.let { id -> { onOpenChapter(id) } },
            )
        }
    }
}

@Composable
private fun PagedContent(
    state: ReaderUiState.Images,
    pagerState: PagerState,
    rtl: Boolean,
    imageScale: ContentScale,
    nextChapterId: Long?,
    prevChapterId: Long?,
    onOpenChapter: (Long) -> Unit,
    onToggleUi: () -> Unit,
) {
    var zoomed by remember { mutableStateOf(false) }
    // Reset zoom (and re-enable paging) whenever the page settles on a new index.
    LaunchedEffect(pagerState.currentPage) { zoomed = false }
    val scope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        reverseLayout = rtl,
        userScrollEnabled = !zoomed,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val model by rememberPageModel(state.provider, page)
        ZoomableImage(
            model = model,
            contentScale = imageScale,
            onZoomChanged = { zoomed = it },
            onTapZone = { fraction ->
                // Map the tap to a reading direction, then turn the page or, at a
                // chapter boundary, jump to the adjacent chapter.
                val goForward = if (rtl) fraction < 0.33f else fraction > 0.66f
                val goBackward = if (rtl) fraction > 0.66f else fraction < 0.33f
                when {
                    goForward -> if (pagerState.currentPage < pagerState.pageCount - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        nextChapterId?.let(onOpenChapter)
                    }
                    goBackward -> if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    } else {
                        prevChapterId?.let(onOpenChapter)
                    }
                    else -> onToggleUi()
                }
            },
        )
    }
}

@Composable
fun ZoomableImage(
    model: Any?,
    contentScale: ContentScale,
    onZoomChanged: (Boolean) -> Unit,
    onTapZone: (Float) -> Unit,
) {
    var scale by remember(model) { mutableStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    val latestTap by rememberUpdatedState(onTapZone)
    val latestZoom by rememberUpdatedState(onZoomChanged)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos -> latestTap(pos.x / size.width.toFloat()) },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        latestZoom(scale > 1f)
                    },
                )
            }
            // Only intercept multi-touch pinch, or drags while already zoomed.
            // Single-finger gestures at 1x fall through so the pager can swipe.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (scale > 1f || pressed >= 2) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            latestZoom(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            CircularProgressIndicator(color = Color.White)
        } else if (model is PageLoadFailure) {
            Text(model.message, color = Color.White, modifier = Modifier.padding(24.dp))
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}

@Composable
private fun WebtoonContent(
    state: ReaderUiState.Images,
    listState: LazyListState,
    nextChapterId: Long?,
    onOpenChapter: (Long) -> Unit,
    onToggleUi: () -> Unit,
    onPageReady: (Int) -> Unit,
) {
    var sawUserScroll by remember(state.book.id) { mutableStateOf(false) }
    var autoOpened by remember(state.book.id, nextChapterId) { mutableStateOf(false) }

    LaunchedEffect(listState, state.book.id) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (moving) sawUserScroll = true
        }
    }

    LaunchedEffect(listState, state.pageCount, nextChapterId, sawUserScroll) {
        if (nextChapterId == null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.any { it.index >= state.pageCount }
        }.distinctUntilChanged().collectLatest { nextButtonVisible ->
            if (nextButtonVisible && sawUserScroll && !autoOpened) {
                delay(450)
                autoOpened = true
                onOpenChapter(nextChapterId)
            }
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.pageCount) { index ->
            val model by rememberPageModel(state.provider, index)
            Box(
                modifier = Modifier
                    .fillMaxWidth()

                    .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }) },
                contentAlignment = Alignment.Center,
            ) {
                if (model == null) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    if (model is PageLoadFailure) Text((model as PageLoadFailure).message, color = Color.White)
                    else ComicPageImage(model = model!!, onAspect = { onPageReady(index) })
                }
            }
        }
        if (nextChapterId != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(onClick = { onOpenChapter(nextChapterId) }) { Text("Next chapter") }
                }
            }
        }
    }
}

