package com.tome.reader.scraper

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class InMemoryCookieJar : CookieJar {

    private val cookieStore = linkedMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val key = "${cookie.domain}\n${cookie.path}\n${cookie.name}"
            cookieStore[key] = cookie
        }
        cookieStore.entries.removeAll { it.value.expiresAt <= System.currentTimeMillis() }
        while (cookieStore.size > 256) cookieStore.remove(cookieStore.keys.first())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookieStore.entries.removeAll { it.value.expiresAt <= System.currentTimeMillis() }
        val matched = cookieStore.values.filter { it.matches(url) }.toMutableList()
        // Bridge cookies the WebView obtained (e.g. Cloudflare's cf_clearance), so
        // requests pass the same challenge the user solved in the unlock screen.
        mergeWebViewCookies(url, matched)
        return matched
    }

    private fun mergeWebViewCookies(url: HttpUrl, into: MutableList<Cookie>) {
        val raw = runCatching { CookieManager.getInstance().getCookie(url.toString()) }
            .getOrNull() ?: return
        raw.split(";").forEach { pair ->
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "").trim()
            if (name.isNotEmpty() && into.none { it.name == name }) {
                runCatching {
                    Cookie.Builder().name(name).value(value).domain(url.host).build()
                }.getOrNull()?.let { into.add(it) }
            }
        }
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
    }
}
