package com.tome.reader.domain.model

/**
 * How an image book is laid out.
 *  - [LTR]/[RTL]: paged, swipe horizontally (RTL is the manga default).
 *  - [WEBTOON]: vertical continuous scroll (manhwa / long-strip).
 */
enum class ReadingDirection { LTR, RTL, WEBTOON }

/** How a text book is laid out. */
enum class TextLayoutMode { PAGED, SCROLL }

/** Background shade behind reader pages (paged mode + webtoon gutters). */
enum class ReaderBackground { BLACK, GRAY, WHITE }

/** App-wide theme. SEPIA is a warm reading palette; SYSTEM follows the OS. */
enum class ThemeOption { SYSTEM, LIGHT, DARK, SEPIA }

/** How chapter lists are displayed. Storage order is unchanged. */
enum class ChapterListOrder(val label: String) {
    ASCENDING("1 -> Current"),
    DESCENDING("Current -> 1");

    fun toggled(): ChapterListOrder =
        if (this == ASCENDING) DESCENDING else ASCENDING
}
