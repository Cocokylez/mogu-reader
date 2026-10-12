package com.tome.reader.scraper.diagnostics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tome.reader.notifications.ReaderNotifications
import com.tome.reader.scraper.ScraperRegistry

class DiagnosticsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        ReaderNotifications.showDownloadRunning(
            context = context,
            title = "Source diagnostics running",
            body = "Testing Mogu Reader sources in the background",
        )

        return try {
            val report = ScraperDiagnosticsRunner.run(
                registry = ScraperRegistry(),
                includeDisabled = true,
            )
            DiagnosticsReportStore.saveLatest(context, report)
            ReaderNotifications.clearDownloadRunning(context, "Source diagnostics running")
            ReaderNotifications.showDownloadComplete(
                context = context,
                title = "Source diagnostics finished",
                body = "${report.okCount} OK, ${report.errorCount} broken/empty, ${report.skippedCount} skipped",
            )
            Result.success()
        } catch (e: Exception) {
            ReaderNotifications.clearDownloadRunning(context, "Source diagnostics running")
            ReaderNotifications.showDownloadFailed(
                context = context,
                title = "Source diagnostics failed",
                body = e.message?.take(180) ?: "Unknown error",
            )
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "source_diagnostics_background"
    }
}
