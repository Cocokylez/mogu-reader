package com.tome.reader.scraper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tome.reader.scraper.ScraperRegistry
import com.tome.reader.scraper.diagnostics.DiagnosticsReport
import com.tome.reader.scraper.diagnostics.ScraperDiagnosticsRunner
import com.tome.reader.scraper.diagnostics.SourceDiagnosticResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel : ViewModel() {

    private val _results = MutableStateFlow<List<SourceDiagnosticResult>>(emptyList())
    val results: StateFlow<List<SourceDiagnosticResult>> = _results.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _fullReportText = MutableStateFlow("")
    val fullReportText: StateFlow<String> = _fullReportText.asStateFlow()

    fun runDiagnostics(
        registry: ScraperRegistry,
        onReportReady: (DiagnosticsReport) -> Unit = {},
    ) {
        if (_isRunning.value) return
        _results.value = emptyList()
        _fullReportText.value = ""
        _isRunning.value = true

        viewModelScope.launch {
            val report = ScraperDiagnosticsRunner.run(
                registry = registry,
                includeDisabled = true,
                onProgress = { completed, total ->
                    _progress.value = completed to total
                },
            )
            _results.value = report.results
            _fullReportText.value = report.toFullText()
            runCatching { onReportReady(report) }
            _isRunning.value = false
        }
    }

    fun clearResults() {
        _results.value = emptyList()
        _fullReportText.value = ""
    }
}
