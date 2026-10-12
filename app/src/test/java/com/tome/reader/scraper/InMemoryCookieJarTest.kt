package com.tome.reader.scraper

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class InMemoryCookieJarTest {
    @Test fun cookiesRespectHostPathAndHttps() {
        val jar = InMemoryCookieJar()
        val site = "https://reader.example/account".toHttpUrl()
        jar.saveFromResponse(site, listOf(Cookie.Builder().name("session").value("test")
            .hostOnlyDomain("reader.example").path("/account").secure().build()))
        assertEquals(1, jar.loadForRequest(site).size)
        listOf("https://otherreader.example/account", "https://reader.example/chapters",
            "http://reader.example/account", "https://sub.reader.example/account").forEach {
            assertTrue(it, jar.loadForRequest(it.toHttpUrl()).isEmpty())
        }
    }

    @Test fun updatedCookieReplacesOldValue() {
        val jar = InMemoryCookieJar()
        val site = "https://reader.example/".toHttpUrl()
        for (value in listOf("old", "new")) jar.saveFromResponse(site,
            listOf(Cookie.Builder().name("session").value(value).hostOnlyDomain(site.host).build()))
        assertEquals(listOf("new"), jar.loadForRequest(site).map { it.value })
    }
}
