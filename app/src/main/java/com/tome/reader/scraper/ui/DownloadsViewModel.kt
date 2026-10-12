package com.tome.reader.scraper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tome.reader.data.local.BookEntity
import com.tome.reader.data.local.SeriesWithProgress
import com.tome.reader.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the downloads manager: every downloaded (scraped) series with its on-disk
 * size, plus delete actions for a whole series or a single chapter. Reuses the
 * existing [com.tome.reader.data.repository.LibraryRepository] delete plumbing,
 * which removes both the DB rows and the cached files.
 */
class DownloadsViewModel(private val container: AppContainer) : ViewModel() {

    /** A downloaded series plus how much space it occupies on disk. */
    data class DownloadedSeries(
        val info: SeriesWithProgress,
        val sizeBytes: Long,
    )

    val downloads: StateFlow<List<DownloadedSeries>> =
        container.database.seriesDao().observeScraperSeries()
            .map { list ->
                withContext(Dispatchers.IO) {
                    list.map { s ->
                        DownloadedSeries(
                            info = s,
                            sizeBytes = container.scraperDownloader.downloadSize(s.series.sourceUri),
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The downloaded chapters of one series, in reading order, observed live. */
    fun chapters(seriesId: Long): Flow<List<BookEntity>> =
        container.database.bookDao().observeChapters(seriesId)

    fun deleteSeries(seriesId: Long) {
        viewModelScope.launch { container.libraryRepository.deleteSeries(seriesId) }
    }

    fun deleteChapter(bookId: Long) {
        viewModelScope.launch { container.libraryRepository.deleteChapter(bookId) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DownloadsViewModel(container) }
        }
    }
}
