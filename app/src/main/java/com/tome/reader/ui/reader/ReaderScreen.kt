package com.tome.reader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tome.reader.appContainer
import com.tome.reader.domain.model.AppSettings

/**
 * Entry point for the manga/manhwa image reader.
 */
@Composable
fun ReaderScreen(
    bookId: Long,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit,
) {
    val context = LocalContext.current
    val vm: ReaderViewModel = viewModel(key = "comic-$bookId", factory = ReaderViewModel.factory(context.appContainer, bookId))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val chapterNav by vm.chapterNav.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)
    KeepScreenOn(enabled = settings.keepScreenOn)

    when (val s = state) {
        ReaderUiState.Loading -> ReaderLoading(onBack)
        is ReaderUiState.Error -> ReaderError(s.message, onBack)
        is ReaderUiState.Images -> ImageReader(
            state = s,
            vm = vm,
            chapterNav = chapterNav,
            onBack = onBack,
            onOpenChapter = onOpenChapter,
        )

    }
}
