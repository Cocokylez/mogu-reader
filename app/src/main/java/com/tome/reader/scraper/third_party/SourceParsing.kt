package com.tome.reader.scraper.third_party

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

internal object SourceParsing {
    fun sourceUrl(value: String, base: String, path: Regex): String {
        val uri = runCatching { URI(base).resolve(value) }.getOrNull()
        val expectedHost = URI(base).host
        if (uri == null || uri.scheme !in setOf("http", "https") ||
            uri.host?.removePrefix("www.") != expectedHost || !path.matches(uri.path)) {
            throw IOException("This link does not belong to this source.")
        }
        return "$base${uri.path.trimEnd('/')}"
    }

    fun image(element: Element?, base: String): String? {
        if (element == null) return null
        val raw = listOf("data-src", "data-lazy-src", "src")
            .firstNotNullOfOrNull { element.attr(it).takeIf(String::isNotBlank) } ?: return null
        var uri = runCatching { URI(base).resolve(raw) }.getOrNull() ?: return null
        if (uri.path == "/_next/image") {
            val wrapped = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("url=") }?.substringAfter('=')
            if (wrapped != null) uri = runCatching { URI(base).resolve(URLDecoder.decode(wrapped, "UTF-8")) }.getOrNull() ?: return null
        }
        return uri.toString().takeIf { uri.scheme in setOf("http", "https") && uri.host != null }
    }

    fun nextProps(doc: Document): JSONObject = try {
        JSONObject(doc.selectFirst("#__NEXT_DATA__")?.data().orEmpty())
            .getJSONObject("props").getJSONObject("pageProps")
    } catch (e: Exception) {
        throw IOException("This source changed its page format. Open the website or try another source.", e)
    }

    fun astroProps(doc: Document, component: String): JSONObject {
        val raw = doc.selectFirst("astro-island[component-url*=$component]")?.attr("props") ?: return JSONObject()
        return runCatching {
            val tagged = JSONObject(raw)
            JSONObject().apply { tagged.keys().forEach { key -> put(key, astroValue(tagged.opt(key))) } }
        }.getOrDefault(JSONObject())
    }

    private fun astroValue(value: Any?): Any? {
        if (value !is JSONArray) return value
        return when (value.optInt(0, -1)) {
            1 -> JSONArray().apply {
                val entries = value.optJSONArray(1) ?: JSONArray()
                for (i in 0 until entries.length()) put(astroValue(entries.opt(i)))
            }
            0 -> when (val entry = value.opt(1)) {
                is JSONObject -> JSONObject().apply { entry.keys().forEach { key -> put(key, astroValue(entry.opt(key))) } }
                else -> entry
            }
            else -> JSONObject.NULL
        }
    }

    fun requireImages(urls: List<String>, doc: Document, name: String): List<String> {
        if (urls.isEmpty()) {
            val message = if (Regex("unlock|early access|premium chapter|purchase chapter|sign in to read", RegexOption.IGNORE_CASE).containsMatchIn(doc.selectFirst("main")?.text() ?: doc.text())) {
                "This chapter requires access on $name. Open the website or choose a free chapter."
            } else "No readable pages were found on $name. This chapter may be unavailable or the site has changed."
            throw IOException(message)
        }
        if (urls.size > 2000) throw IOException("This chapter contains too many pages to load.")
        return urls.distinct()
    }
}
