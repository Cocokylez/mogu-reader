package com.tome.reader.online

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.tome.reader.data.source.FormatDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Comic downloads use temporary files so interruptions cannot replace an existing chapter. */
object WebDownloader {
    private val SUPPORTED_EXT = setOf("pdf", "cbz", "cbr", "zip", "rar")

    fun isSupportedFileUrl(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase() in SUPPORTED_EXT

    suspend fun download(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        authHeader: String? = null,
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var partial: File? = null
        try {
            val original = URL(url)
            var current = original
            var redirects = 0
            while (true) {
                require(current.protocol == "https" || current.protocol == "http") { "Unsupported download address" }
                val cookie = runCatching { CookieManager.getInstance().getCookie(current.toString()) }.getOrNull()
                val sameOrigin = current.protocol == original.protocol && current.host.equals(original.host, true) &&
                    current.port == original.port
                val conn = open(current, userAgent, cookie, authHeader.takeIf { sameOrigin })
                connection = conn
                if (conn.responseCode !in setOf(301, 302, 303, 307, 308)) break
                check(redirects++ < 5) { "Too many download redirects" }
                val next = URL(current, conn.getHeaderField("Location") ?: error("Missing redirect address"))
                check(current.protocol != "https" || next.protocol == "https") { "Insecure download redirect" }
                conn.disconnect()
                current = next
            }
            val conn = checkNotNull(connection)
            check(conn.responseCode in 200..299) { "Download failed (HTTP ${conn.responseCode})" }
            val name = fileName(
                current.toString(),
                conn.getHeaderField("Content-Disposition") ?: contentDisposition,
                conn.contentType?.takeIf { extensionForMime(it) != null } ?: mimeType ?: conn.contentType,
            )
            require(name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXT) {
                "Choose a CBZ, CBR, ZIP, RAR, or PDF comic file"
            }
            val dir = File(context.filesDir, "downloads").apply { mkdirs() }
            val temporary = File.createTempFile("comic-", ".part", dir)
            partial = temporary
            var written = 0L
            conn.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                    }
                }
            }
            check(written > 0 && (conn.contentLengthLong <= 0 || written == conn.contentLengthLong)) {
                "The download was incomplete"
            }
            check(FormatDetector.detect(context, Uri.fromFile(temporary), name)?.isImages == true) {
                "The downloaded file is not a supported comic"
            }
            val ext = name.substringAfterLast('.')
            val stem = name.substringBeforeLast('.').take(90)
            val file = File(dir, "$stem-${UUID.randomUUID().toString().take(8)}.$ext")
            check(temporary.renameTo(file)) { "Couldn't save the download" }
            partial = null
            Result.success(file)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            connection?.disconnect()
            partial?.delete()
        }
    }

    private fun open(url: URL, userAgent: String?, cookie: String?, authHeader: String?): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent ?: "Mozilla/5.0 (Android) MoguReader")
            setRequestProperty("Accept-Encoding", "identity")
            if (!cookie.isNullOrEmpty()) setRequestProperty("Cookie", cookie)
            if (!authHeader.isNullOrBlank()) setRequestProperty("Authorization", "Basic $authHeader")
        }

    private fun fileName(url: String, contentDisposition: String?, mimeType: String?): String {
        val match = contentDisposition?.let {
            Regex("filename\\*?=([^;]+)", RegexOption.IGNORE_CASE).find(it)
        }
        val raw = match?.groupValues?.get(1)?.trim()?.trim('"')?.removePrefix("UTF-8''")
            ?: url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank { "comic" }
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        var name = decoded.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trimStart('.').ifBlank { "comic" }
        if (name.substringAfterLast('.', "").lowercase() !in SUPPORTED_EXT) {
            extensionForMime(mimeType)?.let { name += ".$it" }
        }
        return name
    }

    private fun extensionForMime(mime: String?): String? = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
        "application/pdf" -> "pdf"
        "application/zip", "application/x-zip-compressed", "application/x-cbz", "application/vnd.comicbook+zip" -> "cbz"
        "application/x-cbr", "application/vnd.comicbook-rar", "application/vnd.rar", "application/x-rar-compressed" -> "cbr"
        else -> null
    }
}
