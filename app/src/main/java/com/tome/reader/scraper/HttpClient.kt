package com.tome.reader.scraper

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object HttpClient {

    /**
     * A single pinned User-Agent. It must stay constant (and match the WebView's UA
     * in the Cloudflare-unlock screen) because Cloudflare binds its `cf_clearance`
     * cookie to the exact UA that solved the challenge — rotating UAs would void it.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36"

    /**
     * Desktop UA for sites that serve a different document to phones.
     *
     * WEBTOON is the case this exists for: on the mobile UA its series page comes
     * back as a 30KB shell with no episode list at all, so every series looked
     * like it had zero chapters. The same URL on a desktop UA returns ul#_listUl
     * with real episode links. Only use this for sources that are not behind
     * Cloudflare -- cf_clearance is bound to the UA that solved the challenge, so
     * CF-protected sources must keep [USER_AGENT], which matches the WebView.
     */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val cookieJar = InMemoryCookieJar()

    private fun nextUserAgent(): String = USER_AGENT

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cookieJar(cookieJar)
            .build()
    }

    private fun buildRequest(url: String, referer: String? = null, userAgent: String? = null): Request {
        val ua = userAgent ?: nextUserAgent()
        return Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,zh-CN,zh;q=0.8,ja;q=0.7")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
            .apply {
                if (referer != null) header("Referer", referer)
            }
            .build()
    }

    @Throws(IOException::class)
    private fun executeWithRetry(request: Request): okhttp3.Response {
        for (attempt in 0..1) {
            if (Thread.currentThread().isInterrupted) throw IOException("Request cancelled")
            val response = try {
                client.newCall(request).execute()
            } catch (_: UnknownHostException) {
                throw IOException("Cannot reach ${request.url.host}. Check your connection or try the source website.")
            }
            if (response.isSuccessful) return response
            val code = response.code
            val retryAfter = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 5) ?: 1L
            response.close()
            if (attempt == 0 && code in listOf(429, 502, 503, 504)) {
                Thread.sleep(retryAfter * 1000)
                continue
            }
            // Some sites (Comikey) reject the full Sec-Fetch-* header set with 403
            // while answering a minimal request normally. Worth one plain retry
            // before telling the reader the source needs a browser.
            if (attempt == 0 && code in listOf(401, 403)) {
                val plain = request.newBuilder()
                    .removeHeader("Sec-Fetch-Dest").removeHeader("Sec-Fetch-Mode")
                    .removeHeader("Sec-Fetch-Site").removeHeader("Sec-Fetch-User")
                    .removeHeader("Upgrade-Insecure-Requests")
                    .removeHeader("Cache-Control").removeHeader("Pragma")
                    .build()
                val retried = try { client.newCall(plain).execute() } catch (_: IOException) { null }
                if (retried != null) {
                    if (retried.isSuccessful) return retried
                    retried.close()
                }
            }
            val explanation = when (code) {
                401, 403 -> "Source requires browser access"
                404 -> "This page is no longer available"
                429 -> "Source rate limit reached; try again later"
                else -> "Source request failed"
            }
            throw IOException("$explanation (HTTP $code, ${request.url.host})")
        }
        throw IOException("Source request failed")
    }

    /**
     * True only for an actual Cloudflare interstitial.
     *
     * Detection is structural on purpose. Two earlier versions guessed from page
     * text and both threw away real pages: matching "please enable javascript"
     * condemned every single-page app (Flame Comics' catalogue), and gating that
     * phrase on a small page still condemned Flame Comics' *chapter* pages, which
     * are images with ~700 characters of text and so looked "too small to be
     * real". Page size says nothing about legitimacy. A challenge always ships
     * Cloudflare's own markup, so that is all we look for.
     */
    private fun isCloudflarePage(doc: Document): Boolean {
        val hasChallengeMarkup = doc.selectFirst(
            "#cf-error-details, .cf-error-page, .cf-browser-verification, " +
                "#challenge-form, #challenge-running, #challenge-stage, " +
                "script[src*=challenges.cloudflare.com]"
        ) != null
        if (hasChallengeMarkup) return true

        // The interstitial's own title, exactly -- not a page that merely mentions it.
        val title = doc.title().trim().lowercase()
        if (title == "just a moment..." || title == "just a moment" ||
            title.startsWith("attention required")
        ) return true

        // Challenge bootstrap, present even when the markup above is inlined.
        val html = doc.html()
        return html.contains("cf_chl_opt") || html.contains("__cf_chl_")
    }


    fun fetchDocument(url: String, referer: String? = null, userAgent: String? = null): Document {
        val request = buildRequest(url, referer, userAgent)
        val (bodyBytes, contentType) = executeWithRetry(request).use { response ->
            (response.body?.bytes() ?: ByteArray(0)) to response.header("Content-Type", "").orEmpty()
        }

        val charset = when {
            contentType.contains("charset=") ->
                contentType.substringAfter("charset=").substringBefore(";").trim()
            bodyBytes.size > 3 && bodyBytes[0] == 0xEF.toByte() &&
                bodyBytes[1] == 0xBB.toByte() && bodyBytes[2] == 0xBF.toByte() -> "UTF-8"
            bodyBytes.size > 2 && bodyBytes[0] == 0xFF.toByte() &&
                bodyBytes[1] == 0xFE.toByte() -> "UTF-16LE"
            bodyBytes.size > 2 && bodyBytes[0] == 0xFE.toByte() &&
                bodyBytes[1] == 0xFF.toByte() -> "UTF-16BE"
            else -> "UTF-8"
        }
        val body = try { String(bodyBytes, Charset.forName(charset)) }
        catch (_: Exception) { String(bodyBytes, Charsets.UTF_8) }

        val doc = Jsoup.parse(body, url)

        if (isCloudflarePage(doc)) {
            Log.w("MoguScraper", "Cloudflare BLOCKED $url title=${doc.title().take(60)} bodyLen=${doc.text().length}")
            throw IOException("Blocked by Cloudflare: $url — site is protected")
        }
        return doc
    }

    /**
     * POST a form-encoded body and parse the response as HTML. Used for AJAX search
     * endpoints (e.g. Novel Updates' admin-ajax live search). The Cloudflare check is
     * skipped because these endpoints return bare HTML fragments, not full pages.
     */
    fun postForm(url: String, form: Map<String, String>, referer: String? = null): Document {
        val bodyBuilder = FormBody.Builder()
        form.forEach { (key, value) -> bodyBuilder.add(key, value) }
        val request = buildRequest(url, referer)
            .newBuilder()
            .post(bodyBuilder.build())
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "*/*")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .build()
        val bytes = executeWithRetry(request).use { it.body?.bytes() ?: ByteArray(0) }
        return Jsoup.parse(String(bytes, Charsets.UTF_8), url)
    }

    fun fetchJson(url: String, referer: String? = null): JSONObject {
        val request = buildRequest(url, referer)
            .newBuilder()
            .header("Accept", "application/json, text/plain, */*")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .build()
        val body = executeWithRetry(request).use { it.body?.string() ?: "{}" }
        if (body.trimStart().let { !it.startsWith("{") && !it.startsWith("[") }) {
            throw IOException("Not JSON response from ${request.url}: ${body.take(100)}")
        }
        return JSONObject(body)
    }

    fun fetchJsonArray(url: String, referer: String? = null): org.json.JSONArray {
        val request = buildRequest(url, referer)
            .newBuilder()
            .header("Accept", "application/json, text/plain, */*")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .build()
        val body = executeWithRetry(request).use { it.body?.string() ?: "[]" }
        if (body.trimStart().let { !it.startsWith("[") }) {
            throw IOException("Not JSON array response from ${request.url}: ${body.take(100)}")
        }
        return org.json.JSONArray(body)
    }

    fun fetchProtobuf(url: String, referer: String? = null): ByteArray {
        val request = buildRequest(url, referer)
            .newBuilder()
            .header("Accept", "application/x-protobuf")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .build()
        val bytes = executeWithRetry(request).use { it.body?.bytes() ?: ByteArray(0) }
        return bytes
    }

    fun fetchBytes(url: String, referer: String? = null): ByteArray {
        val request = buildRequest(url, referer)
            .newBuilder()
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .header("Sec-Fetch-Dest", "image")
            .header("Sec-Fetch-Mode", "no-cors")
            .build()
        val bytes = executeWithRetry(request).use { it.body?.bytes() ?: ByteArray(0) }
        return bytes
    }

    fun downloadImage(url: String, destination: File, referer: String? = null): File {
        val request = buildRequest(url, referer).newBuilder()
            .header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
            // buildRequest describes a page navigation. Sending that for an image
            // makes hotlink checks suspicious of the request; these are what a
            // browser actually sends when loading an <img>.
            .header("Sec-Fetch-Dest", "image")
            .header("Sec-Fetch-Mode", "no-cors")
            .header("Sec-Fetch-Site", "cross-site")
            .removeHeader("Upgrade-Insecure-Requests")
            .removeHeader("Sec-Fetch-User")
            .build()
        return executeWithRetry(request).use { response ->
            val body = response.body ?: throw IOException("Empty page image")
            ImageDownloads.write(body.byteStream(), destination, body.contentLength())
        }
    }

    fun clearCookies() {
        cookieJar.clear()
    }
}
