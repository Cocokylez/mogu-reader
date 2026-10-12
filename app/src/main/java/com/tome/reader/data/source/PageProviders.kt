package com.tome.reader.data.source

import android.net.Uri
import java.io.File

/** Serves page images that were extracted to numbered files (0, 1, 2 ...). */
class FilePageProvider(
    override val pageCount: Int,
    private val dir: File,
) : PageImageProvider {
    override suspend fun getPage(index: Int): Any? {
        val f = File(dir, index.toString())
        return if (f.exists()) f else null
    }
}

/** Serves page images that are loose content uris (an image folder). */
class UriPageProvider(
    private val uris: List<Uri>,
) : PageImageProvider {
    override val pageCount: Int = uris.size
    override suspend fun getPage(index: Int): Any? = uris.getOrNull(index)
}
