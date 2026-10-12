package com.tome.reader.scraper

object SeriesCache {
    private val cache = mutableMapOf<String, ScrapedSeries>()
    fun put(key: String, series: ScrapedSeries) {
        cache[key] = series
        cache[routeKey(key, series.url)] = series
    }
    fun take(key: String): ScrapedSeries? = cache.remove(key)

    /**
     * Non-consuming read. Used by destinations that can be re-created when
     * returning through the back stack: [take] would empty the cache on the first
     * visit and leave the screen with no data (a black screen) on the second.
     */
    fun peek(key: String): ScrapedSeries? = cache[key]

    fun peek(key: String, seriesUrl: String): ScrapedSeries? {
        val normalizedUrl = normalizeUrl(seriesUrl)
        return cache[routeKey(key, seriesUrl)]
            ?: cache[key]?.takeIf { normalizeUrl(it.url) == normalizedUrl }
    }

    private fun routeKey(key: String, seriesUrl: String): String =
        "$key:${normalizeUrl(seriesUrl)}"

    private fun normalizeUrl(url: String): String =
        url.trim().trimEnd('/').lowercase()
}
