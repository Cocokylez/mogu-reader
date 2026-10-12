package com.tome.reader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize

/**
 * Splits a styled chapter into screen-sized pages for paginated mode.
 *
 * The whole chapter is measured once at the available width; lines are grouped
 * into pages by accumulating their heights until the viewport is full. Each page
 * is the sub-sequence covering its lines, so paragraph styling (indent, justify,
 * heading) is preserved and no text is lost or repeated.
 */
@Composable
fun rememberPaginatedChapter(
    text: AnnotatedString,
    style: TextStyle,
    size: IntSize,
): List<AnnotatedString> {
    val measurer = rememberTextMeasurer()
    return remember(text, style, size.width, size.height) {
        if (size.width <= 0 || size.height <= 0 || text.text.isEmpty()) {
            listOf(text)
        } else {
            paginate(measurer, text, style, size.width, size.height)
        }
    }
}

@Composable
fun rememberPaginatedContent(
    blocks: List<BookContentBlock>,
    style: TextStyle,
    size: IntSize,
): List<BookContentBlock> {
    val measurer = rememberTextMeasurer()
    return remember(blocks, style, size.width, size.height) {
        if (size.width <= 0 || size.height <= 0) {
            blocks.ifEmpty { listOf(BookContentBlock.Text(AnnotatedString(""))) }
        } else {
            val pages = ArrayList<BookContentBlock>()
            for (block in blocks) {
                when (block) {
                    is BookContentBlock.Text -> {
                        if (block.value.text.isBlank()) {
                            pages.add(block)
                        } else {
                            paginate(measurer, block.value, style, size.width, size.height)
                                .forEach { pages.add(BookContentBlock.Text(it)) }
                        }
                    }
                    is BookContentBlock.Image -> pages.add(block)
                }
            }
            pages.ifEmpty { listOf(BookContentBlock.Text(AnnotatedString(""))) }
        }
    }
}

private fun paginate(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    width: Int,
    height: Int,
): List<AnnotatedString> {
    val layout = measurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = width),
    )
    val lineCount = layout.lineCount
    if (lineCount == 0) return listOf(text)

    val pages = ArrayList<AnnotatedString>()
    var startLine = 0
    while (startLine < lineCount) {
        val top = layout.getLineTop(startLine)
        var endLine = startLine
        while (endLine + 1 < lineCount && (layout.getLineBottom(endLine + 1) - top) <= height) {
            endLine++
        }
        val startChar = layout.getLineStart(startLine).coerceIn(0, text.length)
        val endChar = layout.getLineEnd(endLine, visibleEnd = true).coerceIn(startChar, text.length)
        pages.add(text.subSequence(startChar, endChar))
        startLine = endLine + 1
    }
    return pages.ifEmpty { listOf(text) }
}
