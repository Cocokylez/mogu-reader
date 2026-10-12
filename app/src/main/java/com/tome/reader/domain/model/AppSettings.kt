package com.tome.reader.domain.model

/**
 * User preferences, persisted with DataStore (see SettingsRepository).
 * The text-reader typography values double as the per-book defaults.
 */
data class AppSettings(
    // Dark is the default so the violet "glass" redesign is the out-of-box look.
    val theme: ThemeOption = ThemeOption.DARK,
    val defaultDirection: ReadingDirection = ReadingDirection.LTR,
    val keepScreenOn: Boolean = true,
    val volumeKeyPaging: Boolean = false,
    val textLayout: TextLayoutMode = TextLayoutMode.SCROLL,
    val fontSizeSp: Float = 18f,
    val lineSpacing: Float = 1.5f,
    val marginDp: Int = 16,
    val justify: Boolean = true,
    val indentParagraphs: Boolean = false,
    val chapterListOrder: ChapterListOrder = ChapterListOrder.ASCENDING,
    val readingQuality: ReadingQuality = ReadingQuality.COMFY,
    // How the library presents its items (the redesign's grid/shelves/list/cards row).
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    // Cover-accent UI colour (ARGB). Defaults to the redesign's violet.
    val accentColor: Int = DEFAULT_ACCENT,
    // Background check of followed series for new chapters; optional auto-download.
    val autoUpdateFollows: Boolean = true,
    val autoDownloadNewChapters: Boolean = false,
    // Online reader: WEBTOON = vertical continuous (default, best for manhwa);
    // LTR/RTL = paged with tap zones (RTL is the Japanese-manga default).
    val onlineReadingMode: ReadingDirection = ReadingDirection.WEBTOON,
    val readerBackground: ReaderBackground = ReaderBackground.BLACK,
    // Screen dim applied as an overlay in the reader. 1f = full brightness.
    val readerBrightness: Float = 1f,
    // Vertical comic auto-scroll speed in density-independent pixels per second.
    val readerAutoScrollSpeed: Float = 60f,
    // 18+ material is hidden until the reader turns it on.
    val showAdultContent: Boolean = false,
    // The opening letter is shown once, on the very first launch.
    val seenWelcomeLetter: Boolean = false,
) {
    companion object {
        const val MIN_FONT_SP = 12f
        const val MAX_FONT_SP = 32f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.2f
        const val MIN_MARGIN_DP = 0
        const val MAX_MARGIN_DP = 48

        // Accent options offered in Settings (matches the design's tweak palette).
        // Matte accents: same hues, lower saturation and value, so nothing glows
        // against the near-black background.
        const val DEFAULT_ACCENT: Int = 0xFFB84455.toInt() // Red (matte)
        val ACCENT_CHOICES: List<Int> = listOf(
            0xFFB84455.toInt(), // Red (default)
            0xFF6A5FC0.toInt(), // Violet
            0xFF3F63A8.toInt(), // Blue
            0xFF2E7C90.toInt(), // Cyan
            0xFF3F8A5C.toInt(), // Green
            0xFFC0743E.toInt(), // Orange
        )
    }
}

enum class ReadingQuality {
    COMPACT,
    COMFY,
    IMAGE_FULLSCREEN,
}

/**
 * Library presentation modes from the redesign's layout row.
 *
 * GRID is the long-standing default. SHELVES groups the library into horizontal
 * rails, LIST is a dense single-column view with progress, and CARDS is a browsable
 * stacked deck for picking up something to read.
 */
enum class LibraryLayout(val label: String) {
    GRID("Grid"),
    SHELVES("Shelves"),
    LIST("List"),
    CARDS("Cards"),
}
