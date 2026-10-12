package com.tome.reader.scraper

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.scraperDataStore by preferencesDataStore(name = "scrapers")

data class BookmarkedSeries(
    val scraperId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val url: String,
    val category: ScraperCategory,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    // Update tracking for followed series: chapter count at the user's last-seen
    // baseline vs. the latest background check, and when that check last ran (0 = never).
    val seenChapters: Int = 0,
    val latestChapters: Int = 0,
    val lastCheckedAt: Long = 0,
) {
    /** How many new chapters have appeared since the user last opened this series. */
    val newChapterCount: Int get() = (latestChapters - seenChapters).coerceAtLeast(0)
}

class ScraperRepository(private val context: Context) {

    private val bookmarksKey = stringPreferencesKey("series_bookmarks")

    // Series the user explicitly added to their Library (distinct from the automatic
    // "recently opened" bookmarks above).
    private val followsKey = stringPreferencesKey("series_follows")

    val bookmarkedSeries: Flow<List<BookmarkedSeries>> = context.scraperDataStore.data.map { prefs ->
        val raw = prefs[bookmarksKey] ?: return@map emptyList()
        parseBookmarks(raw).filter { it.category.isSupported }
    }

    val followedSeries: Flow<List<BookmarkedSeries>> = context.scraperDataStore.data.map { prefs ->
        parseBookmarks(prefs[followsKey]).filter { it.category.isSupported }
    }

    suspend fun addBookmark(series: BookmarkedSeries) {
        if (!series.category.isSupported) return
        context.scraperDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[bookmarksKey]).toMutableList()
            current.removeAll { it.scraperId == series.scraperId && it.url == series.url }
            current.add(0, series)
            prefs[bookmarksKey] = encodeBookmarks(current)
        }
    }

    suspend fun removeBookmark(scraperId: String, url: String) {
        context.scraperDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[bookmarksKey]).toMutableList()
            current.removeAll { it.scraperId == scraperId && it.url == url }
            prefs[bookmarksKey] = encodeBookmarks(current)
        }
    }

    suspend fun addFollow(series: BookmarkedSeries) {
        if (!series.category.isSupported) return
        context.scraperDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[followsKey]).toMutableList()
            current.removeAll { it.scraperId == series.scraperId && it.url == series.url }
            current.add(0, series)
            prefs[followsKey] = encodeBookmarks(current)
        }
    }

    suspend fun removeFollow(scraperId: String, url: String) {
        context.scraperDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[followsKey]).toMutableList()
            current.removeAll { it.scraperId == scraperId && it.url == url }
            prefs[followsKey] = encodeBookmarks(current)
        }
    }

    /** One-shot snapshot of the current follows, for background workers. */
    suspend fun followedOnce(): List<BookmarkedSeries> = followedSeries.first()

    /** Merge restored follows into the current set (existing entries win on conflict). */
    suspend fun restoreFollows(restored: List<BookmarkedSeries>) {
        if (restored.isEmpty()) return
        context.scraperDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[followsKey]).toMutableList()
            for (series in restored.filter { it.category.isSupported }) {
                val exists = current.any { it.scraperId == series.scraperId && it.url == series.url }
                if (!exists) current.add(series)
            }
            prefs[followsKey] = encodeBookmarks(current)
        }
    }

    /**
     * Record a background update check. The first ever check silently sets the baseline
     * (so following a series doesn't flag its whole backlog as "new"); later checks just
     * refresh the latest known chapter count, leaving the badge to reflect the delta.
     */
    suspend fun recordFollowCheck(scraperId: String, url: String, latest: Int) {
        editFollow(scraperId, url) { follow ->
            val firstCheck = follow.lastCheckedAt == 0L
            follow.copy(
                seenChapters = if (firstCheck) latest else follow.seenChapters,
                latestChapters = latest,
                lastCheckedAt = System.currentTimeMillis(),
            )
        }
    }

    /** Clear the "new chapters" badge by moving the seen baseline up to the latest. */
    suspend fun markFollowSeen(scraperId: String, url: String) {
        editFollow(scraperId, url) { follow ->
            follow.copy(seenChapters = maxOf(follow.seenChapters, follow.latestChapters))
        }
    }

    private suspend fun editFollow(
        scraperId: String,
        url: String,
        transform: (BookmarkedSeries) -> BookmarkedSeries,
    ) {
        context.scraperDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[followsKey]).toMutableList()
            val idx = current.indexOfFirst { it.scraperId == scraperId && it.url == url }
            if (idx >= 0) {
                current[idx] = transform(current[idx])
                prefs[followsKey] = encodeBookmarks(current)
            }
        }
    }

    private fun parseBookmarks(raw: String?): List<BookmarkedSeries> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val rawAuthor = obj.optString("a", "")
                    val rawCover = obj.optString("c", "")
                    BookmarkedSeries(
                        scraperId = obj.getString("sid"),
                        title = obj.getString("t"),
                        author = rawAuthor.takeIf { it.isNotBlank() && it != "null" },
                        coverUrl = rawCover.takeIf { it.isNotBlank() && it != "null" },
                        url = obj.getString("u"),
                        category = try {
                            ScraperCategory.valueOf(obj.getString("cat"))
                        } catch (_: Exception) {
                            ScraperCategory.NOVEL
                        },
                        lastOpenedAt = obj.optLong("ts", System.currentTimeMillis()),
                        seenChapters = obj.optInt("seen", 0),
                        latestChapters = obj.optInt("lat", 0),
                        lastCheckedAt = obj.optLong("chk", 0L),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeBookmarks(list: List<BookmarkedSeries>): String {
        return JSONArray(list.map { s ->
            JSONObject().apply {
                put("sid", s.scraperId)
                put("t", s.title)
                put("a", s.author ?: JSONObject.NULL)
                put("c", s.coverUrl ?: JSONObject.NULL)
                put("u", s.url)
                put("cat", s.category.name)
                put("ts", s.lastOpenedAt)
                put("seen", s.seenChapters)
                put("lat", s.latestChapters)
                put("chk", s.lastCheckedAt)
            }
        }).toString()
    }

}
