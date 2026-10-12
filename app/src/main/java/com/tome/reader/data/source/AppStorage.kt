package com.tome.reader.data.source

import android.content.Context
import java.io.File

/**
 * Centralises where reader files live on disk:
 *  - covers   -> filesDir (persistent; survives cache clears).
 *  - sources  -> cacheDir (local copies of archives/PDFs needed for random access).
 *  - pages    -> cacheDir (extracted/rendered page images).
 *
 * Everything except covers is disposable; if the OS clears the cache the app
 * simply re-extracts on next open.
 */
class AppStorage(context: Context) {
    private val covers = File(context.filesDir, "covers").apply { mkdirs() }
    private val sources = File(context.cacheDir, "sources").apply { mkdirs() }
    private val pages = File(context.cacheDir, "pages").apply { mkdirs() }

    fun coverFile(key: String): File = File(covers, "$key.jpg")

    fun sourceFile(key: String, ext: String): File = File(sources, "$key.$ext")

    fun pagesDir(key: String): File = File(pages, key).apply { mkdirs() }

    /** Remove every cached/persistent file belonging to one book. */
    fun deleteFor(key: String) {
        coverFile(key).delete()
        sources.listFiles { f -> f.nameWithoutExtension == key }?.forEach { it.delete() }
        pagesDir(key).deleteRecursively()
    }
}
