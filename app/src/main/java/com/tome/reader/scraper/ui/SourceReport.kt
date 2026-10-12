package com.tome.reader.scraper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.ui.components.tomeDisplayTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun copySourceReport(
    context: Context,
    scraper: Scraper,
    series: ScrapedSeries? = null,
    error: String? = null,
    extraLines: List<String> = emptyList(),
) {
    val text = buildSourceReport(scraper, series, error, extraLines)
    val clip = ClipData.newPlainText("Mogu Reader Source Report", text)
    context.getSystemService(Context.CLIPBOARD_SERVICE)?.let { manager ->
        (manager as ClipboardManager).setPrimaryClip(clip)
    }
    Toast.makeText(context, "Copied source report", Toast.LENGTH_SHORT).show()
}

private fun buildSourceReport(
    scraper: Scraper,
    series: ScrapedSeries?,
    error: String?,
    extraLines: List<String>,
): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return buildString {
        appendLine("Mogu Reader Source Report")
        appendLine("Created: ${fmt.format(Date())}")
        appendLine()
        appendLine("Source: ${scraper.name}")
        appendLine("Source ID: ${scraper.id}")
        appendLine("Category: ${scraper.category.label}")
        appendLine("Base URL: ${scraper.baseUrl}")
        appendLine("Shown in Browse: ${scraper.isAvailable}")
        if (series != null) {
            appendLine()
            appendLine("Series: ${tomeDisplayTitle(series.title, series.url)}")
            appendLine("Series URL: ${series.url}")
            appendLine("Cover URL: ${series.coverUrl ?: "missing"}")
            appendLine("Author: ${series.author ?: "unknown"}")
            appendLine("Status: ${series.status ?: "unknown"}")
        }
        extraLines.filter { it.isNotBlank() }.forEach { appendLine(it) }
        error?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Current error:")
            appendLine(it)
        }
        scraper.lastErrorText?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Last scraper error:")
            appendLine(it)
        }
        scraper.diagnosticsText.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Diagnostics:")
            appendLine(it)
        }
    }
}
