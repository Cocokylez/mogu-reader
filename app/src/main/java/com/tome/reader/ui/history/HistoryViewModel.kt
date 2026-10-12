package com.tome.reader.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tome.reader.di.AppContainer
import com.tome.reader.domain.model.HistoryEntry
import com.tome.reader.domain.model.OnlineHistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A single row in History: either a local book/chapter or streamed online content. */
sealed interface HistoryListItem {
    val lastReadAt: Long

    data class Local(val entry: HistoryEntry) : HistoryListItem {
        override val lastReadAt get() = entry.lastReadAt
    }

    data class Online(val entry: OnlineHistoryEntry) : HistoryListItem {
        override val lastReadAt get() = entry.lastReadAt
    }
}

data class HistoryUiState(
    val loading: Boolean = true,
    val items: List<HistoryListItem> = emptyList(),
)

class HistoryViewModel(container: AppContainer) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        container.libraryRepository.observeHistory(),
        container.onlineHistoryRepository.observeRecent(),
    ) { local, online ->
        val merged = (
            local.map { HistoryListItem.Local(it) } +
                online.filter { it.category == "MANGA" || it.category == "MANHWA" }
                    .map { HistoryListItem.Online(it) }
            ).sortedByDescending { it.lastReadAt }
        HistoryUiState(loading = false, items = merged)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HistoryViewModel(container) }
        }
    }
}
