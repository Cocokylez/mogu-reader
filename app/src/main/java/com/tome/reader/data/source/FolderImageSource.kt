package com.tome.reader.data.source

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tome.reader.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a folder of loose images (a tree uri) as a single book. Nothing is
 * extracted or copied: each image is a content uri Coil can load directly.
 */
class FolderImageSource(
    private val context: Context,
    private val treeUri: Uri,
) : ImageBookSource {

    override val format: BookFormat = BookFormat.IMAGE_FOLDER

    override suspend fun probe(): SourceProbe = withContext(Dispatchers.IO) {
        val images = listImages()
        val cover = images.firstOrNull()?.let { readBytes(it) }
        SourceProbe(
            title = Saf.folderName(context, treeUri),
            author = null,
            coverBytes = cover,
            entryCount = images.size,
        )
    }

    override suspend fun openImages(): PageImageProvider = withContext(Dispatchers.IO) {
        UriPageProvider(listImages())
    }

    private fun listImages(): List<Uri> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isFile && ImageExtensions.isImage(it.name ?: "") }
            .sortedWith(compareBy(NaturalOrder) { it.name ?: "" })
            .map { it.uri }
    }

    private fun readBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}
