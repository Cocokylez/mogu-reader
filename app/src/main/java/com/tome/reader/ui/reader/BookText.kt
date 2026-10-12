package com.tome.reader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.tome.reader.data.source.TextChapter
import com.tome.reader.data.source.TextContentBlock

sealed interface BookContentBlock {
    data class Text(val value: AnnotatedString) : BookContentBlock
    data class Image(val path: String, val altText: String?) : BookContentBlock
}

/**
 * Builds render blocks for a text chapter. EPUBs may include image blocks; TXT
 * and scraper text still flow through the normal text-only path.
 */
fun buildBookContent(
    chapter: TextChapter,
    indent: Boolean,
    justify: Boolean,
    includeTitle: Boolean = true,
): List<BookContentBlock> {
    val blocks = ArrayList<BookContentBlock>()
    val title = chapter.title
    var pendingTitle = includeTitle && !title.isNullOrBlank()

    for (block in chapter.contentBlocks) {
        when (block) {
            is TextContentBlock.Text -> {
                if (block.value.isNotBlank()) {
                    blocks.add(
                        BookContentBlock.Text(
                            buildTextBlock(
                                text = block.value,
                                indent = indent,
                                justify = justify,
                                title = title.takeIf { pendingTitle },
                            )
                        )
                    )
                    pendingTitle = false
                }
            }
            is TextContentBlock.Image -> {
                if (pendingTitle) {
                    blocks.add(BookContentBlock.Text(buildTitleText(title.orEmpty())))
                    pendingTitle = false
                }
                blocks.add(BookContentBlock.Image(block.path, block.altText))
            }
        }
    }
    if (pendingTitle) blocks.add(BookContentBlock.Text(buildTitleText(title.orEmpty())))
    return blocks.ifEmpty {
        listOf(BookContentBlock.Text(AnnotatedString("")))
    }
}

/**
 * Builds a book-like [AnnotatedString] for a chapter:
 *  - an optional centered, bold chapter title,
 *  - paragraphs that are either first-line indented or separated by blank lines,
 *  - optional justified alignment.
 */
fun buildBookText(
    chapter: TextChapter,
    indent: Boolean,
    justify: Boolean,
    includeTitle: Boolean = true,
): AnnotatedString {
    val parts = buildBookContent(chapter, indent, justify, includeTitle)
        .filterIsInstance<BookContentBlock.Text>()
        .map { it.value }
        .filter { it.text.isNotBlank() }
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append("\n\n")
            append(part)
        }
    }
}

private fun buildTitleText(title: String): AnnotatedString {
    return buildAnnotatedString {
        withStyle(ParagraphStyle(textAlign = TextAlign.Center)) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.25.em)) {
                append(title.trim())
            }
        }
    }
}

private fun buildTextBlock(
    text: String,
    indent: Boolean,
    justify: Boolean,
    title: String? = null,
): AnnotatedString {
    val paragraphs = splitParagraphs(text)
    val align = if (justify) TextAlign.Justify else TextAlign.Start
    val firstLineIndent = if (indent) TextIndent(firstLine = 1.6.em) else TextIndent.None
    val separator = if (indent) "\n" else "\n\n"

    return buildAnnotatedString {
        if (!title.isNullOrBlank()) {
            append(buildTitleText(title))
            append("\n\n")
        }
        paragraphs.forEachIndexed { index, paragraph ->
            withStyle(ParagraphStyle(textAlign = align, textIndent = firstLineIndent)) {
                append(paragraph)
            }
            if (index < paragraphs.lastIndex) append(separator)
        }
    }
}

/**
 * Splits chapter text into paragraphs robustly:
 *  - blank lines separate normal paragraphs;
 *  - otherwise single line breaks are treated as paragraph breaks, rescuing
 *    EPUBs that separate paragraphs with br tags instead of p tags.
 * Soft line breaks inside a paragraph collapse to spaces.
 */
fun splitParagraphs(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parts = if (trimmed.contains("\n\n")) {
        trimmed.split(Regex("\\n{2,}"))
    } else {
        trimmed.split('\n')
    }
    return parts
        .map { it.replace('\n', ' ').trim() }
        .filter { it.isNotEmpty() }
}
