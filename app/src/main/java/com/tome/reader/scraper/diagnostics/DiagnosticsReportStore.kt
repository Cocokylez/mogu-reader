package com.tome.reader.scraper.diagnostics

import android.content.Context
import java.io.File

object DiagnosticsReportStore {
    private const val DIR_NAME = "diagnostics"
    private const val LATEST_NAME = "latest-source-diagnostics.txt"

    fun latestFile(context: Context): File {
        return File(context.filesDir, DIR_NAME).apply { mkdirs() }.resolve(LATEST_NAME)
    }

    fun saveLatest(context: Context, report: DiagnosticsReport): File {
        val file = latestFile(context)
        file.writeText(report.toFullText())
        return file
    }

    fun readLatest(context: Context): String? {
        val file = latestFile(context)
        return file.takeIf { it.exists() && it.length() > 0L }?.readText()
    }
}
