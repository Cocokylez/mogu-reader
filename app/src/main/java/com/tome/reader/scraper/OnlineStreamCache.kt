package com.tome.reader.scraper

import android.content.Context
import java.io.File

object OnlineStreamCache {
    private const val DIR_NAME = "online-stream-pages"
    private const val MAX_AGE_MS = 3L * 24L * 60L * 60L * 1000L
    private const val MAX_BYTES = 220L * 1024L * 1024L

    fun directory(context: Context): File =
        File(context.cacheDir, DIR_NAME).apply { mkdirs() }

    fun touch(file: File) {
        if (file.exists()) file.setLastModified(System.currentTimeMillis())
    }

    fun prune(context: Context, maxAgeMs: Long = MAX_AGE_MS, maxBytes: Long = MAX_BYTES) {
        val dir = directory(context)
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        val now = System.currentTimeMillis()
        files
            .filter { now - it.lastModified() > maxAgeMs }
            .forEach { it.delete() }

        val remaining = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = remaining.sumOf { it.length() }
        if (total <= maxBytes) return

        for (file in remaining.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }
}
