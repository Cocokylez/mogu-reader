package com.tome.reader.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tome.reader.appContainer
import com.tome.reader.di.AppContainer
import com.tome.reader.data.source.ChapterOrdering
import com.tome.reader.data.source.NaturalOrder
import com.tome.reader.notifications.ReaderNotifications
import com.tome.reader.scraper.BookmarkedSeries
import com.tome.reader.scraper.ScrapedChapter
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background check of followed series for new chapters. Runs periodically (and on
 * demand from Settings). For each follow it refreshes the known chapter count; when
 * the count has grown since the user last saw the series it bumps the Library badge,
 * and — if auto-download is enabled — pulls the new chapters down for offline reading.
 */
class FollowUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val container = context.appContainer
        val settings = container.settingsRepository.settings.first()
        if (!settings.autoUpdateFollows) return Result.success()

        val follows = runCatching { container.scraperRepository.followedOnce() }
            .getOrDefault(emptyList())
        if (follows.isEmpty()) return Result.success()

        var totalNew = 0
        var seriesWithNew = 0
        var downloaded = 0

        for (follow in follows) {
            currentCoroutineContext().ensureActive()
            val scraper = container.scraperRegistry.get(follow.scraperId) ?: continue
            if (!scraper.isAvailable || scraper.isBrowserOnly || !follow.category.isSupported) continue
            val chapters = try {
                withTimeout(45_000L) { withContext(Dispatchers.IO) { scraper.fetchChapters(follow.url) } }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                null
            } catch (_: Exception) { null }
            if (chapters.isNullOrEmpty()) continue

            val firstCheck = follow.lastCheckedAt == 0L
            container.scraperRepository.recordFollowCheck(follow.scraperId, follow.url, chapters.size)
            if (firstCheck) continue // first run only establishes the baseline

            val newCount = (chapters.size - follow.seenChapters).coerceAtLeast(0)
            if (newCount <= 0) continue
            val newlyDetected = (chapters.size - follow.latestChapters).coerceAtLeast(0)
            totalNew += newlyDetected
            if (newlyDetected > 0) seriesWithNew++

            if (settings.autoDownloadNewChapters) {
                downloaded += downloadNewChapters(container, scraper, follow, chapters, newCount)
            }
        }

        if (totalNew > 0) {
            val body = buildString {
                append(totalNew).append(" new chapter").append(if (totalNew == 1) "" else "s")
                append(" across ").append(seriesWithNew).append(" series")
                if (downloaded > 0) append(" · ").append(downloaded).append(" downloaded")
            }
            ReaderNotifications.showDownloadComplete(context, "New chapters available", body)
        }
        return Result.success()
    }

    private suspend fun downloadNewChapters(
        container: AppContainer,
        scraper: Scraper,
        follow: BookmarkedSeries,
        chapters: List<ScrapedChapter>,
        newCount: Int,
    ): Int {
        val ordered = chapters.withIndex().sortedWith(
            compareBy<IndexedValue<ScrapedChapter>> {
                ChapterOrdering.sortNumber(it.value.title, it.value.number) ?: Double.MAX_VALUE
            }.thenBy(NaturalOrder) { it.value.title },
        )
        val series = ScrapedSeries(
            title = follow.title,
            author = follow.author,
            coverUrl = follow.coverUrl,
            url = follow.url,
            category = follow.category,
        )
        // New chapters are the highest-numbered ones; cap to avoid a runaway backlog if
        // a source's count suddenly jumps. The downloader dedupes by chapter url, so any
        // already-downloaded chapter is a no-op.
        val targets = ordered.takeLast(newCount.coerceAtMost(MAX_AUTO_DOWNLOAD))
        var done = 0
        val downloaded = container.scraperDownloader.getDownloadedChapterIndices(scraper, series)
        for ((index, chapter) in targets) {
            currentCoroutineContext().ensureActive()
            if (index in downloaded) continue
            try {
                val content = withContext(Dispatchers.IO) { scraper.fetchChapter(chapter.url) }
                container.scraperDownloader.downloadChapter(scraper, series, chapter, content, index)
                done++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep missing chapters eligible for the next check. */ }
        }
        return done
    }

    companion object {
        const val UNIQUE_PERIODIC = "follow_updates_periodic"
        const val UNIQUE_ONCE = "follow_updates_once"
        private const val MAX_AUTO_DOWNLOAD = 10

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Enqueue the recurring check (kept across launches; ~every 6 hours). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<FollowUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Run a check right now (the "Check for updates" button in Settings). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FollowUpdateWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
