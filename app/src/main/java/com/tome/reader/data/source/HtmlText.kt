package com.tome.reader.data.source

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Converts an XHTML chapter into readable text blocks, preserving image markers
 * in document order so EPUB illustrations can be rendered by the reader.
 */
object HtmlText {

    sealed interface HtmlBlock {
        data class Text(val value: String) : HtmlBlock
        data class Image(val src: String, val altText: String?) : HtmlBlock
    }

    data class Extracted(
        val text: String,
        val blocks: List<HtmlBlock>,
    )

    private val BLOCK = setOf(
        "p", "div", "br", "li", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "tr", "section", "article", "hr", "figcaption",
        "pre", "ul", "ol", "table", "header", "footer"
    )

    fun extract(html: String): String = extractStructured(html).text

    fun extractStructured(html: String): Extracted {
        val doc = Jsoup.parse(html)
        val sb = StringBuilder()
        val blocks = ArrayList<HtmlBlock>()
        val root = doc.body() ?: doc

        fun flushText() {
            val text = normalize(sb.toString())
            if (text.isNotBlank()) blocks.add(HtmlBlock.Text(text))
            sb.setLength(0)
        }

        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node is TextNode) {
                    val t = node.text()
                    if (t.isNotEmpty()) sb.append(t)
                } else if (node is Element && node.normalName() in IMAGE_TAGS) {
                    imageSrc(node)?.let { src ->
                        flushText()
                        blocks.add(HtmlBlock.Image(src = src, altText = node.attr("alt").takeIf { it.isNotBlank() }))
                    }
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element) {
                    val tag = node.normalName()
                    if (tag in BLOCK) {
                        sb.append('\n')
                        if (tag == "p" || tag == "div" || tag == "blockquote" ||
                            (tag.length == 2 && tag[0] == 'h')
                        ) {
                            sb.append('\n')
                        }
                    }
                }
            }
        }, root)

        flushText()
        return Extracted(
            text = normalize(blocks.filterIsInstance<HtmlBlock.Text>().joinToString("\n\n") { it.value }),
            blocks = blocks,
        )
    }

    private fun normalize(value: String): String {
        return value
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2007', ' ')
            .replace("\u00AD", "")
            .replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun imageSrc(element: Element): String? {
        return listOf("src", "data-src", "href", "xlink:href")
            .asSequence()
            .map { element.attr(it).trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private val IMAGE_TAGS = setOf("img", "image")
}
