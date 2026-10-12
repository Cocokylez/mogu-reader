package com.tome.reader.data.source

object ChapterOrdering {
    private val labeledNumberPatterns = listOf(
        Regex("""(?i)\bchapter\s*#?\s*(\d+(?:\.\d+)?)"""),
        Regex("""(?i)\bchap\.?\s*#?\s*(\d+(?:\.\d+)?)"""),
        Regex("""(?i)\bch\.?\s*#?\s*(\d+(?:\.\d+)?)"""),
        Regex("""(?i)\bepisode\s*#?\s*(\d+(?:\.\d+)?)"""),
        Regex("""(?i)\bep\.?\s*#?\s*(\d+(?:\.\d+)?)"""),
    )
    private val looseNumberPattern = Regex("""(?<!\d)(\d+(?:\.\d+)?)(?!\d)""")

    fun sortNumber(title: String?, provided: Double? = null): Double? {
        val fromTitle = numberFromTitle(title)
        return fromTitle ?: provided
    }

    fun numberFromTitle(title: String?): Double? {
        val text = title?.replace('\u00A0', ' ')?.trim()?.takeIf { it.isNotBlank() } ?: return null
        labeledNumberPatterns.forEach { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { return it }
        }
        return looseNumberPattern.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
            .firstOrNull { it >= 0.0 }
    }
}
