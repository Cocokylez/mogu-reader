package com.tome.reader.data.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

/** Helpers for reading display names out of Storage Access Framework uris. */
object Saf {

    /** Best-effort human display name for a single-document uri. */
    fun displayName(context: Context, uri: Uri): String? {
        var name: String? = null
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        }
        return name
    }

    /** Title for a single document: display name without its extension. */
    fun title(context: Context, uri: Uri): String {
        val n = displayName(context, uri) ?: return "Untitled"
        val dot = n.lastIndexOf('.')
        return if (dot > 0) n.substring(0, dot) else n
    }

    /** Folder (tree uri) name. */
    fun folderName(context: Context, treeUri: Uri): String {
        return runCatching { DocumentFile.fromTreeUri(context, treeUri)?.name }.getOrNull()
            ?: treeUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: "Folder"
    }
}
