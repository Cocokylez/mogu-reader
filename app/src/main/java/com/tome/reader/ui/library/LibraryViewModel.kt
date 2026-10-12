package com.tome.reader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tome.reader.di.AppContainer
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.domain.model.Collection
import com.tome.reader.domain.model.LibraryItem
import com.tome.reader.domain.model.LibraryLayout
import com.tome.reader.domain.model.LibraryQuery
import com.tome.reader.domain.model.SortOrder
import com.tome.reader.scraper.BookmarkedSeries
import com.tome.reader.scraper.ScraperCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = true,
    val hasAnyItems: Boolean = false,
    val items: List<LibraryItem> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val membership: Map<Long, Set<Long>> = emptyMap(),
    val query: LibraryQuery = LibraryQuery(),
    // Online series the user explicitly followed (shown as a Library section).
    val followed: List<BookmarkedSeries> = emptyList(),
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow(LibraryQuery())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        container.libraryRepository.observeStandaloneBooks(),
        container.libraryRepository.observeSeries(),
        container.collectionRepository.observeCollections(),
        container.collectionRepository.observeAllLinks(),
        query,
    ) { books, series, collections, links, q ->
        val membership = links.groupBy { it.bookId }
            .mapValues { entry -> entry.value.map { it.collectionId }.toSet() }
        val items: List<LibraryItem> =
            books.map { LibraryItem.BookItem(it) } + series.map { LibraryItem.SeriesItem(it) }
        LibraryUiState(
            loading = false,
            hasAnyItems = items.isNotEmpty(),
            items = container.applyLibraryQuery(items, q, membership),
            collections = collections.map { collection ->
                collection.copy(bookCount = books.count { membership[it.id]?.contains(collection.id) == true })
            },
            membership = membership,
            query = q,
        )
    }.combine(container.scraperRepository.followedSeries) { state, followed ->
        val comics = followed.filter { it.category == ScraperCategory.MANGA || it.category == ScraperCategory.MANHWA }
        val search = state.query.search.trim()
        val visible = comics.filter {
            state.query.formatFilter == null && state.query.collectionId == null &&
                (search.isEmpty() || it.title.contains(search, ignoreCase = true) ||
                    it.author?.contains(search, ignoreCase = true) == true)
        }
        // Follows join the same item list the grid/shelves/list/cards views render,
        // so the library is one collection instead of a strip plus a grid. Newest
        // first, matching how imports are ordered.
        val followItems = visible
            .sortedByDescending { it.lastOpenedAt }
            .map { LibraryItem.FollowItem(it) }
        state.copy(
            followed = visible,
            items = state.items + followItems,
            hasAnyItems = state.hasAnyItems || comics.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** The library's presentation mode, persisted so it survives restarts. */
    val layout: StateFlow<LibraryLayout> = container.settingsRepository.settings
        .map { it.libraryLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryLayout.GRID)

    fun setLayout(value: LibraryLayout) {
        viewModelScope.launch { container.settingsRepository.setLibraryLayout(value) }
    }

    fun unfollow(scraperId: String, url: String) {
        viewModelScope.launch { container.scraperRepository.removeFollow(scraperId, url) }
    }

    /** Clear a followed series' "new chapters" badge once the user opens it. */
    fun markFollowSeen(scraperId: String, url: String) {
        viewModelScope.launch { container.scraperRepository.markFollowSeen(scraperId, url) }
    }

    // --- query controls ---
    fun setSearch(value: String) = query.update { it.copy(search = value) }
    fun setSort(value: SortOrder) = query.update { it.copy(sort = value) }
    fun setFormatFilter(value: BookFormat?) = query.update { it.copy(formatFilter = value) }
    fun setCollectionFilter(value: Long?) = query.update { it.copy(collectionId = value) }

    // --- import / delete ---
    fun import(files: List<Uri>, trees: List<Uri>) {
        if (files.isEmpty() && trees.isEmpty()) return
        viewModelScope.launch {
            val result = container.importBooks(files, trees)
            _messages.emit(
                when {
                    result.added <= 0 -> "No new comics imported. Choose CBZ, CBR, ZIP, PDF, or image folders."
                    result.skipped > 0 -> "Imported ${result.added} comics; ${result.skipped} duplicate, unsupported, or unreadable selections skipped"
                    result.added == 1 -> "Imported 1 comic"
                    else -> "Imported ${result.added} comics"
                }
            )
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch { container.deleteBook(book) }
    }

    fun deleteSeries(seriesId: Long) {
        viewModelScope.launch { container.libraryRepository.deleteSeries(seriesId) }
    }

    // --- collections ---
    fun createCollection(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.collectionRepository.create(name) }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            container.collectionRepository.delete(id)
            if (query.value.collectionId == id) setCollectionFilter(null)
        }
    }

    fun setBookInCollection(bookId: Long, collectionId: Long, inCollection: Boolean) {
        viewModelScope.launch {
            if (inCollection) container.collectionRepository.addBook(bookId, collectionId)
            else container.collectionRepository.removeBook(bookId, collectionId)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LibraryViewModel(container) }
        }
    }
}
