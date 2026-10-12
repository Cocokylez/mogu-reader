package com.tome.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tome.reader.di.AppContainer
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.domain.model.ReadingQuality
import com.tome.reader.domain.model.TextLayoutMode
import com.tome.reader.domain.model.ThemeOption
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.settingsRepository

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(value: ThemeOption) {
        viewModelScope.launch { repo.setTheme(value) }
    }

    fun setDirection(value: ReadingDirection) {
        viewModelScope.launch { repo.setDirection(value) }
    }

    fun setReadingQuality(value: ReadingQuality) {
        viewModelScope.launch { repo.setReadingQuality(value) }
    }

    fun setTextLayout(value: TextLayoutMode) {
        viewModelScope.launch { repo.setTextLayout(value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { repo.setKeepScreenOn(value) }
    }

    fun setVolumeKeyPaging(value: Boolean) {
        viewModelScope.launch { repo.setVolumeKeyPaging(value) }
    }

    fun setAccent(value: Int) {
        viewModelScope.launch { repo.setAccentColor(value) }
    }

    fun setAutoUpdateFollows(value: Boolean) {
        viewModelScope.launch { repo.setAutoUpdateFollows(value) }
    }

    fun setAutoDownloadNewChapters(value: Boolean) {
        viewModelScope.launch { repo.setAutoDownloadNewChapters(value) }
    }

    fun setShowAdultContent(value: Boolean) {
        viewModelScope.launch { repo.setShowAdultContent(value) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
