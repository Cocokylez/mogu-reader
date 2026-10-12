package com.tome.reader.data.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tome.reader.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders PDF pages to images with the built-in [PdfRenderer]. This handles both
 * image-style and text PDFs (text pages are rasterised). The PDF is copied into
 * the cache once because PdfRenderer needs a seekable file descriptor.
 *
 * The text-extraction fallback mentioned in the spec is intentionally a separate
 * concern: a dedicated text extractor (e.g. PdfBox-Android) could implement
 * [TextBookSource] behind the same abstraction without touching the readers.
 */
class PdfImageSource(
    private val context: Context,
    private val uri: Uri,
    private val storage: AppStorage,
) : ImageBookSource {

    override val format: BookFormat = BookFormat.PDF
    private val key = Keys.forUri(uri.toString())

    override suspend fun probe(): SourceProbe = withContext(Dispatchers.IO) {
        val file = ensureLocalCopy()
        var count = 0
        var cover: ByteArray? = null
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    count = renderer.pageCount
                    if (count > 0) {
                        val tmp = File.createTempFile("cover", ".jpg", context.cacheDir)
                        renderPageToFile(renderer, 0, tmp, COVER_WIDTH)
                        cover = tmp.readBytes()
                        tmp.delete()
                    }
                }
            }
        }
        SourceProbe(Saf.title(context, uri), null, cover, count)
    }

    override suspend fun openImages(): PageImageProvider = withContext(Dispatchers.IO) {
        val file = ensureLocalCopy()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        PdfPageProvider(renderer, pfd, storage.pagesDir(key))
    }

    private fun ensureLocalCopy(): File {
        val file = storage.sourceFile(key, "pdf")
        if (file.exists() && file.length() > 0) return file
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        return file
    }

    companion object {
        private const val COVER_WIDTH = 1024
    }
}

/**
 * Lazily renders and caches one PDF page at a time. PdfRenderer is not
 * thread-safe and only allows a single open page, so access is serialised with a
 * [Mutex].
 */
class PdfPageProvider(
    private val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor,
    private val dir: File,
    private val targetWidth: Int = 1440,
) : PageImageProvider {

    private val mutex = Mutex()
    override val pageCount: Int = renderer.pageCount

    override suspend fun getPage(index: Int): Any? {
        if (index !in 0 until pageCount) return null
        val target = File(dir, "$index.jpg")
        if (target.exists() && target.length() > 0) return target
        mutex.withLock {
            if (target.exists() && target.length() > 0) return target
            withContext(Dispatchers.IO) {
                runCatching { renderPageToFile(renderer, index, target, targetWidth) }
            }
        }
        return if (target.exists() && target.length() > 0) target else null
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

/** Render a single PDF page onto a white background and save it as a JPEG. */
private fun renderPageToFile(renderer: PdfRenderer, index: Int, target: File, targetWidth: Int) {
    renderer.openPage(index).use { page ->
        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
        val width = targetWidth
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        target.parentFile?.mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
    }
}
