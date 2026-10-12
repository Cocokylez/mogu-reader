package com.tome.reader.scraper.diagnostics

import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.ScraperRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class DiagnosticStatus {
    OK,
    ERROR,
    SKIPPED,
}

data class SourceDiagnosticResult(
    val sourceId: String,
    val sourceName: String,
    val category: String,
    val baseUrl: String,
    val isAvailable: Boolean,
    val status: DiagnosticStatus,
    val label: String,
    val itemCount: Int,
    // Chapters fetched from a sample series: >0 = openable, 0 = lists but no chapters,
    // -1 = not tested (file-only / official-stub sources, or latest failed).
    val chapterCount: Int,
    val error: String?,
    val diagnostics: String,
    val sampleTitles: List<String>,
)

data class DiagnosticsReport(
    val startedAt: Long,
    val finishedAt: Long,
    val results: List<SourceDiagnosticResult>,
) {
    val okCount: Int get() = results.count { it.status == DiagnosticStatus.OK }
    val errorCount: Int get() = results.count { it.status == DiagnosticStatus.ERROR }
    val skippedCount: Int get() = results.count { it.status == DiagnosticStatus.SKIPPED }

    fun toFullText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("Mogu Reader Source Diagnostics")
            appendLine("Started: ${fmt.format(Date(startedAt))}")
            appendLine("Finished: ${fmt.format(Date(finishedAt))}")
            appendLine("Summary: $okCount OK, $errorCount broken/empty, $skippedCount skipped, ${results.size} total")
            appendLine()
            for (result in results) {
                appendLine("==== ${result.sourceName} (${result.sourceId}) ====")
                appendLine("Category: ${result.category}")
                appendLine("Base URL: ${result.baseUrl}")
                appendLine("Available in Browse: ${result.isAvailable}")
                appendLine("Status: ${result.status.name}")
                appendLine("Label: ${result.label}")
                appendLine("Items: ${result.itemCount}")
                appendLine(
                    "Chapters (sample series): " +
                        if (result.chapterCount >= 0) result.chapterCount.toString() else "not tested",
                )
                if (result.error != null) appendLine("Error: ${result.error}")
                if (result.sampleTitles.isNotEmpty()) {
                    appendLine("Sample titles:")
                    result.sampleTitles.forEach { appendLine("- $it") }
                }
                if (result.diagnostics.isNotBlank()) {
                    appendLine("Diagnostics:")
                    appendLine(result.diagnostics)
                }
                appendLine()
            }
        }
    }
}

object ScraperDiagnosticsRunner {
    private const val SOURCE_TIMEOUT_MS = 30_000L

    // How many sources to probe at once. Parallelism keeps the whole run short
    // (~one timeout, not the sum of all of them) so it finishes before the screen
    // or background worker can be interrupted.
    private const val MAX_CONCURRENCY = 8

    suspend fun run(
        registry: ScraperRegistry,
        includeDisabled: Boolean = true,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): DiagnosticsReport = coroutineScope {
        val startedAt = System.currentTimeMillis()
        val scrapers = if (includeDisabled) registry.all() else registry.available()
        val total = scrapers.size
        val completed = AtomicInteger(0)
        val gate = Semaphore(MAX_CONCURRENCY)
        onProgress(0, total)

        // awaitAll preserves list order, so the report stays in registry order.
        val results = scrapers.map { scraper ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val result = diagnose(scraper)
                    onProgress(completed.incrementAndGet(), total)
                    result
                }
            }
        }.awaitAll()

        DiagnosticsReport(
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
            results = results,
        )
    }

    private suspend fun diagnose(scraper: Scraper): SourceDiagnosticResult {
        return try {
            withTimeout(SOURCE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val items = scraper.fetchLatest()
                    if (items.isEmpty()) {
                        return@withContext baseResult(
                            scraper = scraper,
                            status = DiagnosticStatus.ERROR,
                            itemCount = 0,
                            chapterCount = -1,
                            error = scraper.lastErrorText ?: "No latest/popular content returned",
                            diagnostics = scraper.diagnosticsText.ifBlank { "No diagnostics emitted" },
                            sampleTitles = emptyList(),
                        )
                    }

                    // Listing series isn't enough — confirm we can actually open chapters
                    // (the thing that makes a source usable). Try a few of the sampled
                    // series in case the first happens to be empty. Skip for file-only and
                    // official-stub sources, where a chapter list doesn't apply.
                    val testsChapters = scraper.id !in fileOnlySources && scraper.id !in stubSources
                    var chapterCount = -1
                    var chapterError: String? = null
                    if (testsChapters) {
                        chapterCount = 0
                        for (candidate in items.take(3)) {
                            runCatching { scraper.fetchChapters(candidate.url) }
                                .onSuccess { chs -> if (chs.isNotEmpty()) chapterCount = chs.size }
                                .onFailure { e ->
                                    chapterError = "${e.javaClass.simpleName}: ${e.message?.take(120)}"
                                }
                            if (chapterCount > 0) {
                                chapterError = null
                                break
                            }
                        }
                    }

                    val chaptersOk = !testsChapters || chapterCount > 0
                    baseResult(
                        scraper = scraper,
                        status = if (chaptersOk) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                        itemCount = items.size,
                        chapterCount = chapterCount,
                        error = if (chaptersOk) {
                            null
                        } else {
                            chapterError ?: "Lists series but returned no chapters"
                        },
                        diagnostics = scraper.diagnosticsText,
                        sampleTitles = items.take(6).map { it.title },
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Per-source timeout (not a parent cancellation) — record and move on.
            baseResult(
                scraper = scraper,
                status = DiagnosticStatus.ERROR,
                itemCount = 0,
                chapterCount = -1,
                error = "Timed out after ${SOURCE_TIMEOUT_MS / 1000}s",
                diagnostics = scraper.diagnosticsText,
                sampleTitles = emptyList(),
            )
        } catch (e: CancellationException) {
            // The whole run was cancelled (screen left / worker stopped). Don't swallow
            // it as a per-source failure — let it abort the run honestly.
            throw e
        } catch (e: Exception) {
            baseResult(
                scraper = scraper,
                status = DiagnosticStatus.ERROR,
                itemCount = 0,
                chapterCount = -1,
                error = "${e.javaClass.simpleName}: ${e.message?.take(160) ?: "Unknown error"}",
                diagnostics = scraper.diagnosticsText,
                sampleTitles = emptyList(),
            )
        }
    }

    private fun baseResult(
        scraper: Scraper,
        status: DiagnosticStatus,
        itemCount: Int,
        chapterCount: Int,
        error: String?,
        diagnostics: String,
        sampleTitles: List<String>,
    ): SourceDiagnosticResult {
        val diagnosticsWithNote = diagnosticsWithAvailabilityNote(scraper, diagnostics)
        return SourceDiagnosticResult(
            sourceId = scraper.id,
            sourceName = scraper.name,
            category = scraper.category.label,
            baseUrl = scraper.baseUrl,
            isAvailable = scraper.isAvailable,
            status = status,
            label = diagnosticLabel(scraper, status, itemCount, chapterCount, error, diagnosticsWithNote),
            itemCount = itemCount,
            chapterCount = chapterCount,
            error = error,
            diagnostics = diagnosticsWithNote,
            sampleTitles = sampleTitles,
        )
    }

    private fun diagnosticLabel(
        scraper: Scraper,
        status: DiagnosticStatus,
        itemCount: Int,
        chapterCount: Int,
        error: String?,
        diagnostics: String,
    ): String {
        val text = listOf(
            scraper.id,
            scraper.name,
            scraper.baseUrl,
            error.orEmpty(),
            diagnostics,
        ).joinToString(" ").lowercase(Locale.US)

        return when {
            scraper.id in fileOnlySources -> "file-only"
            status == DiagnosticStatus.OK -> "working"
            text.containsAny("cloudflare", "captcha", "403", "forbidden", "access denied", "blocked") -> "blocked"
            text.containsAny("javascript", "js-rendered", "webview", "browser", "app-only", "redirecting...") ||
                scraper.id in browserLikelySources -> "needs JS/browser"
            // Browsed fine but a sampled series returned no chapters — usable for discovery,
            // not for reading (e.g. AJAX/JS-loaded chapter lists).
            itemCount > 0 && chapterCount == 0 -> "lists only — no chapters"
            itemCount == 0 -> "empty parser"
            else -> "blocked"
        }
    }

    private fun diagnosticsWithAvailabilityNote(scraper: Scraper, diagnostics: String): String {
        val note = if (!scraper.isAvailable) {
            "Source is disabled in Browse; diagnostics probed it anyway for repair."
        } else {
            ""
        }
        return listOf(note, diagnostics)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it) }

    private val fileOnlySources = setOf("gutenberg", "libgen", "zlib", "annasarchive", "pdfdrive")

    // Official "open the app/site" stubs — they don't expose a chapter list to scrape,
    // so chapter-testing them would be a false negative.
    private val stubSources = setOf("azuki", "comikey", "globalcomix", "lezhin", "manta", "tappytoon")

    private val browserLikelySources = setOf(
        "azuki",
        "comikey",
        "globalcomix",
        "lezhin",
        "manta",
        "mangaplus",
        "tappytoon",
        "viz",
    )
}
