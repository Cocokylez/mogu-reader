package com.tome.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tome.reader.data.source.PageImageProvider
import com.tome.reader.di.AppContainer
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.domain.model.ReadingProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Adjacent chapters within a series (null when the book is standalone). */
data class ChapterNav(val prevId: Long? = null, val nextId: Long? = null)

/** Immutable snapshot the reader screen renders. */
sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState

    data class Images(
        val book: Book,
        val settings: AppSettings,
        val provider: PageImageProvider,
        val pageCount: Int,
    ) : ReaderUiState


}

class ReaderViewModel(
    private val container: AppContainer,
    private val bookId: Long,
) : ViewModel() {

    private val library = container.libraryRepository
    private val settings = container.settingsRepository

    private var provider: PageImageProvider? = null
    private val progressLock = Mutex()

    private val content = MutableStateFlow<Content>(Content.Loading)

    private val _chapterNav = MutableStateFlow(ChapterNav())
    val chapterNav: StateFlow<ChapterNav> = _chapterNav.asStateFlow()

    val uiState: StateFlow<ReaderUiState> = combine(
        library.observeBook(bookId),
        settings.settings,
        content,
    ) { book, appSettings, loaded ->
        when {
            loaded is Content.Error -> ReaderUiState.Error(loaded.message)
            loaded is Content.Loading -> ReaderUiState.Loading
            book == null -> ReaderUiState.Error("Book not found")
            loaded is Content.Images ->
                ReaderUiState.Images(book, appSettings, loaded.provider, loaded.provider.pageCount)
            else -> ReaderUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState.Loading)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = library.getBook(bookId)
        if (book == null) {
            content.value = Content.Error("Book not found")
            return
        }
        if (book.seriesId != null) {
            _chapterNav.value = ChapterNav(
                prevId = library.adjacentChapter(book, -1)?.id,
                nextId = library.adjacentChapter(book, +1)?.id,
            )
        }
        content.value = try {
            if (book.format.isImages) {
                val p = container.openImageBook(book)
                provider = p
                if (p.pageCount > 0) Content.Images(p) else {
                    p.close()
                    provider = null
                    Content.Error("No readable pages found")
                }
            } else {
                Content.Error("Tome supports manga and manhwa image books. Import CBZ, CBR, PDF or an image folder.")
            }
        } catch (e: CancellationException) {
            provider?.close()
            provider = null
            throw e
        } catch (e: Exception) {
            Content.Error(e.message ?: "Could not open this book")
        }
    }

    // --- progress ---------------------------------------------------------

    fun saveImagesProgress(page: Int, pageCount: Int) {
        val percent = if (pageCount > 0) (page + 1f) / pageCount else 0f
        persist(ReadingProgress(page, 0, percent.coerceIn(0f, 1f)))
    }

    fun saveWebtoonProgress(index: Int, offset: Int, pageCount: Int) {
        val page = index.coerceIn(0, maxOf(0, pageCount - 1))
        val percent = if (pageCount > 0) (page + 1f) / pageCount else 0f
        persist(ReadingProgress(page, offset.coerceAtLeast(0), percent.coerceIn(0f, 1f)))
    }

    private fun persist(progress: ReadingProgress) {
        // Finish the last save even when leaving destroys this ViewModel.
        container.appScope.launch { progressLock.withLock { container.saveProgress(bookId, progress) } }
    }

    // --- per-book overrides ----------------------------------------------

    fun setDirectionOverride(direction: ReadingDirection) {
        viewModelScope.launch { library.setDirectionOverride(bookId, direction) }
    }

    fun setReaderBrightness(value: Float) {
        viewModelScope.launch { settings.setReaderBrightness(value) }
    }

    fun setAutoScrollSpeed(speed: Float) {
        viewModelScope.launch { settings.setReaderAutoScrollSpeed(speed) }
    }

    override fun onCleared() {
        provider?.close()
        provider = null
    }

    private sealed interface Content {
        data object Loading : Content
        data class Error(val message: String) : Content
        data class Images(val provider: PageImageProvider) : Content
    }

    companion object {
        fun factory(container: AppContainer, bookId: Long) = viewModelFactory {
            initializer { ReaderViewModel(container, bookId) }
        }
    }
}
