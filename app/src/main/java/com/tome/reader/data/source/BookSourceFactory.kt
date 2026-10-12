package com.tome.reader.data.source

import android.content.Context
import android.net.Uri
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.scraper.ScrapedImageBookSource
import java.io.File

/**
 * Maps a (uri, format) pair to the concrete [BookSource] that can read it. This
 * is the single registration point for formats: adding a new one means adding a
 * branch here (and a case in [FormatDetector]).
 */
class BookSourceFactory(private val context: Context, private val appContext: Context? = null) {

    val storage = AppStorage(context)

    fun create(uri: Uri, format: BookFormat): BookSource {
        val path = uri.path ?: ""
        val isScraped = path.contains("/scraper/")

        return when (format) {
            BookFormat.CBZ, BookFormat.ZIP -> ZipImageSource(context, uri, format, storage)
            BookFormat.CBR -> RarImageSource(context, uri, storage)
            BookFormat.IMAGE_FOLDER -> {
                if (isScraped) {
                    ScrapedImageBookSource(context, format, File(path))
                } else {
                    FolderImageSource(context, uri)
                }
            }
            BookFormat.PDF -> PdfImageSource(context, uri, storage)
            BookFormat.EPUB, BookFormat.TXT -> error("Only manga and manhwa image formats are supported")
        }
    }
}
