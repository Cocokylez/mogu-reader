package com.tome.reader.data.source

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile

/**
 * ZIP metadata is read through its central directory, avoiding repeated image
 * decompression just to list filenames. SAF sources are copied once to cache.
 */
object Zips {
    fun openArchive(context: Context, uri: Uri): ZipFile {
        if (uri.scheme == "file") return ZipFile(File(requireNotNull(uri.path)))
        val target = AppStorage(context).sourceFile(Keys.forUri(uri.toString()), "zip")
        synchronized(this) {
            if (!target.exists() || target.length() == 0L) {
                val partial = File.createTempFile("comic-", ".part", target.parentFile)
                try {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the comic archive")
                    input.use { source -> partial.outputStream().use { source.copyTo(it, 64 * 1024) } }
                    check(partial.renameTo(target)) { "Couldn't cache the comic archive" }
                } finally {
                    partial.delete()
                }
            }
        }
        return ZipFile(target)
    }

    /** Read every entry whose name satisfies [keep] into memory, preserving order. */
    fun readAll(
        context: Context,
        uri: Uri,
        keep: (String) -> Boolean,
    ): LinkedHashMap<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        openArchive(context, uri).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.isDirectory && keep(entry.name)) {
                    map[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }
        return map
    }

    /** Read a single named entry, or null if absent. */
    fun readEntry(context: Context, uri: Uri, name: String): ByteArray? {
        return openArchive(context, uri).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { it.name.equals(name, ignoreCase = true) }
            entry?.let { zip.getInputStream(it).use { input -> input.readBytes() } }
        }
    }

    /** Read a known set of entries in one ZIP pass, matching names case-insensitively. */
    fun readEntries(
        context: Context,
        uri: Uri,
        names: Set<String>,
    ): LinkedHashMap<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        if (names.isEmpty()) return map
        val wanted = names.associateBy { it.lowercase() }
        openArchive(context, uri).use { zip ->
            for (entry in zip.entries()) {
                val key = wanted[entry.name.lowercase()]
                if (!entry.isDirectory && key != null) {
                    map[key] = zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }
        return map
    }
}
