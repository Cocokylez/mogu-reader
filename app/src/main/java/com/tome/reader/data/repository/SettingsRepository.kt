package com.tome.reader.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.ChapterListOrder
import com.tome.reader.domain.model.LibraryLayout
import com.tome.reader.domain.model.ReaderBackground
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.domain.model.ReadingQuality
import com.tome.reader.domain.model.TextLayoutMode
import com.tome.reader.domain.model.ThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads and writes [AppSettings] via Jetpack DataStore (no UI thread blocking). */
class SettingsRepository(private val context: Context) {

    private object Key {
        val THEME = stringPreferencesKey("theme")
        val DIRECTION = stringPreferencesKey("direction")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_KEY_PAGING = booleanPreferencesKey("volume_key_paging")
        val TEXT_LAYOUT = stringPreferencesKey("text_layout")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val MARGIN = intPreferencesKey("margin")
        val JUSTIFY = booleanPreferencesKey("justify")
        val INDENT = booleanPreferencesKey("indent_paragraphs")
        val CHAPTER_LIST_ORDER = stringPreferencesKey("chapter_list_order")
        val READING_QUALITY = stringPreferencesKey("reading_quality")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val AUTO_UPDATE_FOLLOWS = booleanPreferencesKey("auto_update_follows")
        val AUTO_DOWNLOAD_NEW = booleanPreferencesKey("auto_download_new")
        val ONLINE_READING_MODE = stringPreferencesKey("online_reading_mode")
        val READER_BACKGROUND = stringPreferencesKey("reader_background")
        val AUTO_SCROLL_SPEED = floatPreferencesKey("reader_auto_scroll_speed")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val SHOW_ADULT = booleanPreferencesKey("show_adult_content")
        val SEEN_WELCOME = booleanPreferencesKey("seen_welcome_letter")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[Key.THEME]?.let { enumOrNull<ThemeOption>(it) } ?: ThemeOption.DARK,
            defaultDirection = p[Key.DIRECTION]?.let { enumOrNull<ReadingDirection>(it) }
                ?: ReadingDirection.LTR,
            keepScreenOn = p[Key.KEEP_SCREEN_ON] ?: true,
            volumeKeyPaging = p[Key.VOLUME_KEY_PAGING] ?: false,
            textLayout = p[Key.TEXT_LAYOUT]?.let { enumOrNull<TextLayoutMode>(it) }
                ?: TextLayoutMode.SCROLL,
            fontSizeSp = p[Key.FONT_SIZE] ?: 18f,
            lineSpacing = p[Key.LINE_SPACING] ?: 1.5f,
            marginDp = p[Key.MARGIN] ?: 16,
            justify = p[Key.JUSTIFY] ?: true,
            indentParagraphs = p[Key.INDENT] ?: false,
            chapterListOrder = p[Key.CHAPTER_LIST_ORDER]?.let { enumOrNull<ChapterListOrder>(it) }
                ?: ChapterListOrder.ASCENDING,
            readingQuality = p[Key.READING_QUALITY]?.let { enumOrNull<ReadingQuality>(it) }
                ?: ReadingQuality.COMFY,
            libraryLayout = p[Key.LIBRARY_LAYOUT]?.let { enumOrNull<LibraryLayout>(it) }
                ?: LibraryLayout.GRID,
            accentColor = p[Key.ACCENT_COLOR] ?: AppSettings.DEFAULT_ACCENT,
            autoUpdateFollows = p[Key.AUTO_UPDATE_FOLLOWS] ?: true,
            autoDownloadNewChapters = p[Key.AUTO_DOWNLOAD_NEW] ?: false,
            onlineReadingMode = p[Key.ONLINE_READING_MODE]?.let { enumOrNull<ReadingDirection>(it) }
                ?: ReadingDirection.WEBTOON,
            readerBackground = p[Key.READER_BACKGROUND]?.let { enumOrNull<ReaderBackground>(it) }
                ?: ReaderBackground.BLACK,
            readerBrightness = (p[Key.READER_BRIGHTNESS] ?: 1f).coerceIn(0.2f, 1f),
            readerAutoScrollSpeed = (p[Key.AUTO_SCROLL_SPEED] ?: 60f).coerceIn(20f, 160f),
            showAdultContent = p[Key.SHOW_ADULT] ?: false,
            seenWelcomeLetter = p[Key.SEEN_WELCOME] ?: false,
        )
    }

    suspend fun setTheme(value: ThemeOption) = edit { it[Key.THEME] = value.name }
    suspend fun setDirection(value: ReadingDirection) = edit { it[Key.DIRECTION] = value.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Key.KEEP_SCREEN_ON] = value }
    suspend fun setVolumeKeyPaging(value: Boolean) = edit { it[Key.VOLUME_KEY_PAGING] = value }
    suspend fun setTextLayout(value: TextLayoutMode) = edit { it[Key.TEXT_LAYOUT] = value.name }
    suspend fun setFontSize(value: Float) = edit { it[Key.FONT_SIZE] = value }
    suspend fun setLineSpacing(value: Float) = edit { it[Key.LINE_SPACING] = value }
    suspend fun setMargin(value: Int) = edit { it[Key.MARGIN] = value }
    suspend fun setJustify(value: Boolean) = edit { it[Key.JUSTIFY] = value }
    suspend fun setIndentParagraphs(value: Boolean) = edit { it[Key.INDENT] = value }
    suspend fun setChapterListOrder(value: ChapterListOrder) =
        edit { it[Key.CHAPTER_LIST_ORDER] = value.name }
    suspend fun setReadingQuality(value: ReadingQuality) = edit { it[Key.READING_QUALITY] = value.name }
    suspend fun setLibraryLayout(value: LibraryLayout) = edit { it[Key.LIBRARY_LAYOUT] = value.name }
    suspend fun setAccentColor(value: Int) = edit { it[Key.ACCENT_COLOR] = value }
    suspend fun setAutoUpdateFollows(value: Boolean) = edit { it[Key.AUTO_UPDATE_FOLLOWS] = value }
    suspend fun setAutoDownloadNewChapters(value: Boolean) = edit { it[Key.AUTO_DOWNLOAD_NEW] = value }
    suspend fun setOnlineReadingMode(value: ReadingDirection) =
        edit { it[Key.ONLINE_READING_MODE] = value.name }
    suspend fun setReaderBackground(value: ReaderBackground) =
        edit { it[Key.READER_BACKGROUND] = value.name }
    suspend fun setReaderBrightness(value: Float) = edit { it[Key.READER_BRIGHTNESS] = value.coerceIn(0.2f, 1f) }
    suspend fun setShowAdultContent(value: Boolean) = edit { it[Key.SHOW_ADULT] = value }
    suspend fun setSeenWelcomeLetter(value: Boolean) = edit { it[Key.SEEN_WELCOME] = value }
    suspend fun setReaderAutoScrollSpeed(value: Float) =
        edit { it[Key.AUTO_SCROLL_SPEED] = value.coerceIn(20f, 160f) }

    /** Current settings snapshot (used by backup export). */
    suspend fun current(): AppSettings = settings.first()

    /** Overwrite all settings from a restored backup. */
    suspend fun restore(s: AppSettings) = edit { p ->
        p[Key.THEME] = s.theme.name
        p[Key.DIRECTION] = s.defaultDirection.name
        p[Key.KEEP_SCREEN_ON] = s.keepScreenOn
        p[Key.VOLUME_KEY_PAGING] = s.volumeKeyPaging
        p[Key.TEXT_LAYOUT] = s.textLayout.name
        p[Key.FONT_SIZE] = s.fontSizeSp
        p[Key.LINE_SPACING] = s.lineSpacing
        p[Key.MARGIN] = s.marginDp
        p[Key.JUSTIFY] = s.justify
        p[Key.INDENT] = s.indentParagraphs
        p[Key.CHAPTER_LIST_ORDER] = s.chapterListOrder.name
        p[Key.READING_QUALITY] = s.readingQuality.name
        p[Key.ACCENT_COLOR] = s.accentColor
        p[Key.AUTO_UPDATE_FOLLOWS] = s.autoUpdateFollows
        p[Key.AUTO_DOWNLOAD_NEW] = s.autoDownloadNewChapters
        p[Key.ONLINE_READING_MODE] = s.onlineReadingMode.name
        p[Key.READER_BACKGROUND] = s.readerBackground.name
        p[Key.READER_BRIGHTNESS] = s.readerBrightness.coerceIn(0.2f, 1f)
        p[Key.AUTO_SCROLL_SPEED] = s.readerAutoScrollSpeed.coerceIn(20f, 160f)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()
}
