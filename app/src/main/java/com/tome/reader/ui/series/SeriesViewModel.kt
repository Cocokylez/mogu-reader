package com.tome.reader.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tome.reader.di.AppContainer
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.Series
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    val loading: Boolean = true,
    val series: Series? = null,
    val chapters: List<Book> = emptyList(),
)

class SeriesViewModel(
    private val container: AppContainer,
    private val seriesId: Long,
) : ViewModel() {

    val uiState: StateFlow<SeriesDetailUiState> = combine(
        container.libraryRepository.observeSeriesDetail(seriesId),
        container.libraryRepository.observeChapters(seriesId),
    ) { series, chapters ->
        SeriesDetailUiState(loading = false, series = series, chapters = chapters)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState())

    fun deleteSeries() {
        viewModelScope.launch { container.libraryRepository.deleteSeries(seriesId) }
    }

    fun markRead(bookId: Long) {
        viewModelScope.launch { container.libraryRepository.markRead(bookId) }
    }

    fun markUnread(bookId: Long) {
        viewModelScope.launch { container.libraryRepository.markUnread(bookId) }
    }

    companion object {
        fun factory(container: AppContainer, seriesId: Long) = viewModelFactory {
            initializer { SeriesViewModel(container, seriesId) }
        }
    }
}

/**
 * The chapter "Continue" should resume: the most recently read in-progress
 * chapter, else the first unread one, else the first chapter.
 */
fun resumeChapterId(chapters: List<Book>): Long? {
    val inProgress = chapters
        .filter { it.progress.percent > 0.0001f && it.progress.percent < 0.95f }
        .maxByOrNull { it.lastReadAt ?: 0L }
    if (inProgress != null) return inProgress.id
    chapters.firstOrNull { it.progress.percent <= 0.0001f }?.let { return it.id }
    return chapters.firstOrNull()?.id
}
