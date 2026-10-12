package com.tome.reader.data.backup

import androidx.room.withTransaction
import com.tome.reader.data.local.AppDatabase
import com.tome.reader.data.local.BookCollectionCrossRef
import com.tome.reader.data.local.BookDao
import com.tome.reader.data.local.BookEntity
import com.tome.reader.data.local.CollectionDao
import com.tome.reader.data.local.CollectionEntity
import com.tome.reader.data.local.OnlineHistoryDao
import com.tome.reader.data.local.OnlineHistoryEntity
import com.tome.reader.data.local.SeriesDao
import com.tome.reader.data.local.SeriesEntity
import com.tome.reader.data.repository.SettingsRepository
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.domain.model.ContentKind
import com.tome.reader.scraper.BookmarkedSeries
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.ScraperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports/imports the user's app data — followed series, online reading history,
 * settings, and the library DB rows (books/series/collections/links) — as a single
 * JSON document.
 *
 * Downloaded files themselves are not embedded; a restore can re-link files still
 * present on the same device. Imported files need access again on another device.
 * Legacy novel records are retained in backups but hidden by the manga library.
 */
class BackupManager(
    private val bookDao: BookDao,
    private val seriesDao: SeriesDao,
    private val collectionDao: CollectionDao,
    private val onlineHistoryDao: OnlineHistoryDao,
    private val scraperRepository: ScraperRepository,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
) {
    data class ImportResult(
        val books: Int,
        val series: Int,
        val collections: Int,
        val onlineHistory: Int,
        val follows: Int,
    )

    suspend fun export(): String = withContext(Dispatchers.IO) {
        JSONObject().apply {
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("settings", settingsToJson(settingsRepository.current()))
            put("follows", JSONArray(scraperRepository.followedOnce().map(::followToJson)))
            put("onlineHistory", JSONArray(onlineHistoryDao.getAllOnce().map(::onlineHistoryToJson)))
            put("series", JSONArray(seriesDao.getAllOnce().map(::seriesToJson)))
            put("books", JSONArray(bookDao.getAllOnce().map(::bookToJson)))
            put("collections", JSONArray(collectionDao.getAllCollectionsOnce().map(::collectionToJson)))
            put("links", JSONArray(collectionDao.getAllLinksOnce().map(::linkToJson)))
        }.toString()
    }

    suspend fun import(json: String): ImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        require(root.optInt("version", 1) in 1..VERSION) { "This backup version is not supported" }
        require(root.has("books") || root.has("follows") || root.has("settings")) { "Not a Mogu Reader backup" }

        // Validate before making any changes. Source ids belong to the backup,
        // never to the current device: resolve their new ids by URI/name below.
        val settings = root.optJSONObject("settings")?.let(::settingsFromJson)
        val follows = root.optJSONArray("follows").objects().map(::followFromJson)
        val series = root.optJSONArray("series").objects().map(::seriesFromJson)
        val books = root.optJSONArray("books").objects().map(::bookFromJson)
        val collections = root.optJSONArray("collections").objects().map(::collectionFromJson)
        val links = root.optJSONArray("links").objects().map(::linkFromJson)
        val history = root.optJSONArray("onlineHistory").objects().map(::onlineHistoryFromJson)
        require(series.all { it.id > 0 && it.sourceUri.isNotBlank() && it.kind in ContentKind.entries.map { kind -> kind.name } }) {
            "Invalid series in backup"
        }
        require(books.all { it.id > 0 && it.uri.isNotBlank() && it.format in BookFormat.entries.map { format -> format.name } }) {
            "Invalid comic in backup"
        }
        require(collections.all { it.id > 0 && it.name.isNotBlank() }) { "Invalid collection in backup" }
        require(series.map { it.id }.distinct().size == series.size && books.map { it.id }.distinct().size == books.size &&
            collections.map { it.id }.distinct().size == collections.size) { "Duplicate ids in backup" }

        var seriesCount = 0
        var bookCount = 0
        var collectionCount = 0
        var historyCount = 0
        database.withTransaction {
            val seriesIds = mutableMapOf<Long, Long>()
            for (row in series) {
                val existing = seriesDao.getBySourceUri(row.sourceUri)
                val targetId = existing?.id ?: seriesDao.insert(row.copy(id = 0)).also { if (it > 0) seriesCount++ }
                if (targetId > 0) seriesIds[row.id] = targetId
            }
            val bookIds = mutableMapOf<Long, Long>()
            for (row in books) {
                val existing = bookDao.getByUri(row.uri)
                val mappedSeriesId = row.seriesId?.let { seriesIds[it] }
                if (existing == null) {
                    val id = bookDao.insert(row.copy(id = 0, seriesId = mappedSeriesId))
                    if (id > 0) {
                        bookIds[row.id] = id
                        bookCount++
                    }
                } else {
                    bookIds[row.id] = existing.id
                    if ((row.lastReadAt ?: 0) > (existing.lastReadAt ?: 0)) {
                        bookDao.update(existing.copy(
                            lastReadAt = row.lastReadAt,
                            progressPrimary = row.progressPrimary,
                            progressSecondary = row.progressSecondary,
                            progressPercent = row.progressPercent,
                            directionOverride = row.directionOverride,
                        ))
                    }
                }
            }
            val collectionIds = mutableMapOf<Long, Long>()
            val collectionByName = collectionDao.getAllCollectionsOnce().associateBy { it.name }.toMutableMap()
            for (row in collections) {
                val id = collectionByName[row.name]?.id ?: collectionDao.insert(row.copy(id = 0)).also {
                    if (it > 0) {
                        collectionCount++
                        collectionByName[row.name] = row.copy(id = it)
                    }
                }
                if (id > 0) collectionIds[row.id] = id
            }
            for (link in links) {
                val bookId = bookIds[link.bookId] ?: continue
                val collectionId = collectionIds[link.collectionId] ?: continue
                collectionDao.addLink(BookCollectionCrossRef(bookId, collectionId))
            }
            for (row in history) {
                if (row.scraperId.isBlank() || row.seriesUrl.isBlank()) continue
                val existing = onlineHistoryDao.get(row.scraperId, row.seriesUrl)
                if (existing == null) {
                    if (onlineHistoryDao.insert(row) > 0) historyCount++
                } else if (row.lastReadAt > existing.lastReadAt) {
                    onlineHistoryDao.update(row.copy(id = existing.id))
                    historyCount++
                }
            }
        }
        scraperRepository.restoreFollows(follows)
        settings?.let { settingsRepository.restore(it) }

        ImportResult(
            books = bookCount,
            series = seriesCount,
            collections = collectionCount,
            onlineHistory = historyCount,
            follows = follows.size,
        )
    }

    // --- serialization --------------------------------------------------------

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun bookToJson(b: BookEntity) = JSONObject().apply {
        put("id", b.id)
        put("uri", b.uri)
        put("isTreeUri", b.isTreeUri)
        put("title", b.title)
        put("author", b.author ?: JSONObject.NULL)
        put("format", b.format)
        put("coverPath", b.coverPath ?: JSONObject.NULL)
        put("entryCount", b.entryCount)
        put("addedAt", b.addedAt)
        put("lastReadAt", b.lastReadAt ?: JSONObject.NULL)
        put("progressPrimary", b.progressPrimary)
        put("progressSecondary", b.progressSecondary)
        put("progressPercent", b.progressPercent.toDouble())
        put("directionOverride", b.directionOverride ?: JSONObject.NULL)
        put("textLayoutOverride", b.textLayoutOverride ?: JSONObject.NULL)
        put("seriesId", b.seriesId ?: JSONObject.NULL)
        put("seriesOrder", b.seriesOrder)
        put("accentColor", b.accentColor ?: JSONObject.NULL)
    }

    private fun bookFromJson(o: JSONObject) = BookEntity(
        id = o.optLong("id", 0),
        uri = o.getString("uri"),
        isTreeUri = o.optBoolean("isTreeUri", false),
        title = o.optString("title"),
        author = o.optStringOrNull("author"),
        format = o.optString("format"),
        coverPath = o.optStringOrNull("coverPath"),
        entryCount = o.optInt("entryCount", 0),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        lastReadAt = o.optLongOrNull("lastReadAt"),
        progressPrimary = o.optInt("progressPrimary", 0).coerceAtLeast(0),
        progressSecondary = o.optInt("progressSecondary", 0).coerceAtLeast(0),
        progressPercent = o.optDouble("progressPercent", 0.0).takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 1f) ?: 0f,
        directionOverride = o.optStringOrNull("directionOverride"),
        textLayoutOverride = o.optStringOrNull("textLayoutOverride"),
        seriesId = o.optLongOrNull("seriesId"),
        seriesOrder = o.optInt("seriesOrder", 0),
        accentColor = o.optIntOrNull("accentColor"),
    )

    private fun seriesToJson(s: SeriesEntity) = JSONObject().apply {
        put("id", s.id)
        put("title", s.title)
        put("coverPath", s.coverPath ?: JSONObject.NULL)
        put("kind", s.kind)
        put("sourceUri", s.sourceUri)
        put("createdAt", s.createdAt)
        put("accentColor", s.accentColor ?: JSONObject.NULL)
    }

    private fun seriesFromJson(o: JSONObject) = SeriesEntity(
        id = o.optLong("id", 0),
        title = o.optString("title"),
        coverPath = o.optStringOrNull("coverPath"),
        kind = o.optString("kind"),
        sourceUri = o.getString("sourceUri"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        accentColor = o.optIntOrNull("accentColor"),
    )

    private fun collectionToJson(c: CollectionEntity) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("createdAt", c.createdAt)
    }

    private fun collectionFromJson(o: JSONObject) = CollectionEntity(
        id = o.optLong("id", 0),
        name = o.optString("name"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun linkToJson(l: BookCollectionCrossRef) = JSONObject().apply {
        put("bookId", l.bookId)
        put("collectionId", l.collectionId)
    }

    private fun linkFromJson(o: JSONObject) = BookCollectionCrossRef(
        bookId = o.optLong("bookId", 0),
        collectionId = o.optLong("collectionId", 0),
    )

    private fun onlineHistoryToJson(h: OnlineHistoryEntity) = JSONObject().apply {
        put("scraperId", h.scraperId)
        put("seriesUrl", h.seriesUrl)
        put("title", h.title)
        put("author", h.author ?: JSONObject.NULL)
        put("coverUrl", h.coverUrl ?: JSONObject.NULL)
        put("category", h.category)
        put("sourceName", h.sourceName)
        put("chapterIndex", h.chapterIndex)
        put("chapterTitle", h.chapterTitle ?: JSONObject.NULL)
        put("scrollItem", h.scrollItem)
        put("scrollOffset", h.scrollOffset)
        put("readChapterIndices", h.readChapterIndices)
        put("lastReadAt", h.lastReadAt)
    }

    private fun onlineHistoryFromJson(o: JSONObject) = OnlineHistoryEntity(
        id = 0,
        scraperId = o.optString("scraperId"),
        seriesUrl = o.optString("seriesUrl"),
        title = o.optString("title"),
        author = o.optStringOrNull("author"),
        coverUrl = o.optStringOrNull("coverUrl"),
        category = o.optString("category"),
        sourceName = o.optString("sourceName"),
        chapterIndex = o.optInt("chapterIndex", 0),
        chapterTitle = o.optStringOrNull("chapterTitle"),
        scrollItem = o.optInt("scrollItem", 0),
        scrollOffset = o.optInt("scrollOffset", 0),
        readChapterIndices = o.optString("readChapterIndices"),
        lastReadAt = o.optLong("lastReadAt", System.currentTimeMillis()),
    )

    private fun followToJson(s: BookmarkedSeries) = JSONObject().apply {
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

    private fun followFromJson(o: JSONObject) = BookmarkedSeries(
        scraperId = o.optString("sid"),
        title = o.optString("t"),
        author = o.optStringOrNull("a"),
        coverUrl = o.optStringOrNull("c"),
        url = o.optString("u"),
        category = runCatching { ScraperCategory.valueOf(o.optString("cat")) }
            .getOrDefault(ScraperCategory.MANGA),
        lastOpenedAt = o.optLong("ts", System.currentTimeMillis()),
        seenChapters = o.optInt("seen", 0),
        latestChapters = o.optInt("lat", 0),
        lastCheckedAt = o.optLong("chk", 0),
    )

    private fun settingsToJson(s: AppSettings) = JSONObject().apply {
        put("theme", s.theme.name)
        put("defaultDirection", s.defaultDirection.name)
        put("keepScreenOn", s.keepScreenOn)
        put("volumeKeyPaging", s.volumeKeyPaging)
        put("textLayout", s.textLayout.name)
        put("fontSizeSp", s.fontSizeSp.toDouble())
        put("lineSpacing", s.lineSpacing.toDouble())
        put("marginDp", s.marginDp)
        put("justify", s.justify)
        put("indentParagraphs", s.indentParagraphs)
        put("chapterListOrder", s.chapterListOrder.name)
        put("readingQuality", s.readingQuality.name)
        put("accentColor", s.accentColor)
        put("autoUpdateFollows", s.autoUpdateFollows)
        put("autoDownloadNewChapters", s.autoDownloadNewChapters)
        put("onlineReadingMode", s.onlineReadingMode.name)
        put("readerBackground", s.readerBackground.name)
        put("readerBrightness", s.readerBrightness.toDouble())
        put("readerAutoScrollSpeed", s.readerAutoScrollSpeed.toDouble())
    }

    private fun settingsFromJson(o: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(o.optString("theme"), d.theme),
            defaultDirection = enumOr(o.optString("defaultDirection"), d.defaultDirection),
            keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
            volumeKeyPaging = o.optBoolean("volumeKeyPaging", d.volumeKeyPaging),
            textLayout = enumOr(o.optString("textLayout"), d.textLayout),
            fontSizeSp = o.optDouble("fontSizeSp", d.fontSizeSp.toDouble()).toFloat(),
            lineSpacing = o.optDouble("lineSpacing", d.lineSpacing.toDouble()).toFloat(),
            marginDp = o.optInt("marginDp", d.marginDp),
            justify = o.optBoolean("justify", d.justify),
            indentParagraphs = o.optBoolean("indentParagraphs", d.indentParagraphs),
            chapterListOrder = enumOr(o.optString("chapterListOrder"), d.chapterListOrder),
            readingQuality = enumOr(o.optString("readingQuality"), d.readingQuality),
            accentColor = o.optInt("accentColor", d.accentColor),
            autoUpdateFollows = o.optBoolean("autoUpdateFollows", d.autoUpdateFollows),
            autoDownloadNewChapters = o.optBoolean("autoDownloadNewChapters", d.autoDownloadNewChapters),
            onlineReadingMode = enumOr(o.optString("onlineReadingMode"), d.onlineReadingMode),
            readerBackground = enumOr(o.optString("readerBackground"), d.readerBackground),
            readerBrightness = o.optDouble("readerBrightness", d.readerBrightness.toDouble()).toFloat(),
            readerAutoScrollSpeed = o.optDouble("readerAutoScrollSpeed", d.readerAutoScrollSpeed.toDouble()).toFloat(),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    companion object {
        const val VERSION = 1
    }
}
