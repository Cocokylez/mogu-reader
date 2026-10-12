package com.tome.reader.scraper.third_party

import com.google.protobuf.CodedInputStream

object MangaPlusProtobuf {

    data class MpTitle(
        val titleId: Int = 0,
        val name: String = "",
        val author: String = "",
        val portraitImageUrl: String = "",
    )

    data class MpChapter(
        val chapterId: Int = 0,
        val titleId: Int = 0,
        val chapterNumber: Int = 0,
        val chapterName: String = "",
    )

    data class MpPage(
        val imageUrl: String = "",
        val encryptionKey: String = "",
    )

    // ── top-level parsers ──────────────────────────────────────────

    fun parseAllTitles(bytes: ByteArray): List<MpTitle> {
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            if (fn == 1) {
                val msg = readMessage(input) ?: break
                return parseSuccessAllTitles(msg)
            }
            if (!skipField(input, tag)) break
        }
        return emptyList()
    }

    fun parseTitleDetail(bytes: ByteArray): Triple<MpTitle?, List<MpChapter>, String> {
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            if (fn == 1) {
                val msg = readMessage(input) ?: break
                return parseSuccessTitleDetail(msg)
            }
            if (!skipField(input, tag)) break
        }
        return Triple(null, emptyList(), "")
    }

    fun parseMangaViewer(bytes: ByteArray): Pair<List<MpPage>, String> {
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            if (fn == 1) {
                val msg = readMessage(input) ?: break
                return parseSuccessMangaViewer(msg)
            }
            if (!skipField(input, tag)) break
        }
        return Pair(emptyList(), "")
    }

    private fun parseSuccessAllTitles(bytes: ByteArray): List<MpTitle> {
        val titles = mutableListOf<MpTitle>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            when (tag shr 3) {
                // Legacy flat AllTitlesView (the `all` endpoint).
                5 -> {
                    val msg = readMessage(input) ?: break
                    titles.addAll(parseAllTitlesView(msg))
                }
                // AllTitlesViewV2 (the `allV2` endpoint). Current responses put Title
                // messages directly in field 1; older builds may wrap them in groups.
                25 -> {
                    val msg = readMessage(input) ?: break
                    val grouped = parseAllTitlesViewV2(msg)
                    titles.addAll(grouped.ifEmpty { parseAllTitlesView(msg) })
                }
                else -> if (!skipField(input, tag)) break
            }
        }
        if (titles.isNotEmpty()) return titles.distinctBy { it.titleId }
        // Resilience across app versions: if neither known field matched, scan every
        // sub-message and try both layouts. parseTitle rejects non-title data, so this
        // can't invent bogus entries.
        return scanForTitles(bytes).distinctBy { it.titleId }
    }

    private fun scanForTitles(bytes: ByteArray): List<MpTitle> {
        val titles = mutableListOf<MpTitle>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            if ((tag and 0x7) == 2) {
                val msg = readMessage(input) ?: break
                val grouped = parseAllTitlesViewV2(msg)
                if (grouped.isNotEmpty()) titles.addAll(grouped)
                else titles.addAll(parseAllTitlesView(msg))
            } else {
                if (!skipField(input, tag)) break
            }
        }
        return titles
    }

    private fun parseAllTitlesViewV2(bytes: ByteArray): List<MpTitle> {
        val titles = mutableListOf<MpTitle>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            if ((tag shr 3) == 1) { // repeated AllTitlesGroup
                val msg = readMessage(input) ?: break
                titles.addAll(parseAllTitlesGroup(msg))
            } else {
                if (!skipField(input, tag)) break
            }
        }
        return titles
    }

    private fun parseAllTitlesGroup(bytes: ByteArray): List<MpTitle> {
        val titles = mutableListOf<MpTitle>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            // Field 1 is the group label (e.g. "A"); field 2 is the repeated Title.
            if ((tag shr 3) == 2) {
                val msg = readMessage(input) ?: break
                parseTitle(msg)?.let { titles.add(it) }
            } else {
                if (!skipField(input, tag)) break
            }
        }
        return titles
    }

    private fun parseSuccessTitleDetail(bytes: ByteArray): Triple<MpTitle?, List<MpChapter>, String> {
        var title: MpTitle? = null
        val chapters = mutableListOf<MpChapter>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            if (fn == 8) {
                val msg = readMessage(input) ?: break
                val (t, ch) = parseTitleDetailView(msg)
                if (title == null) title = t
                chapters.addAll(ch)
            } else {
                if (!skipField(input, tag)) break
            }
        }
        if (chapters.isNotEmpty()) return Triple(title, chapters, "")
        // title_detailV3 moved TitleDetailView off field 8, so the loop above found
        // nothing and every MANGA Plus series opened with an empty chapter list.
        // Rather than chase the field number each time the API version changes,
        // find the view structurally.
        val (scannedTitle, scannedChapters) = scanForTitleDetail(bytes, 0)
        return Triple(title ?: scannedTitle, scannedChapters, "")
    }

    /**
     * Walks nested sub-messages looking for chapter records.
     *
     * [parseChapter] only requires a positive id, which any unrelated message with
     * a field-2 varint would satisfy, so entries found this way must also carry a
     * name or a number before they count as real chapters.
     */
    private fun scanForTitleDetail(bytes: ByteArray, depth: Int): Pair<MpTitle?, List<MpChapter>> {
        if (depth > MAX_SCAN_DEPTH) return null to emptyList()
        var title: MpTitle? = null
        val chapters = mutableListOf<MpChapter>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            if ((tag and 0x7) != 2) {
                if (!skipField(input, tag)) break
                continue
            }
            val msg = readMessage(input) ?: break
            // A length-delimited field may be a string or raw bytes, not a nested
            // message. Feeding those to the parsers throws
            // InvalidProtocolBufferException, which used to abort the whole scan
            // and leave every MANGA Plus series with no chapters. Anything that
            // does not parse is simply not a chapter record.
            val view = runCatching { parseTitleDetailView(msg) }.getOrNull()
            if (view != null) {
                if (title == null && view.first != null) title = view.first
                chapters.addAll(view.second.filter(::looksLikeRealChapter))
            }
            if (view == null || view.second.isEmpty()) {
                val deeper = runCatching { scanForTitleDetail(msg, depth + 1) }.getOrNull()
                if (deeper != null) {
                    if (title == null) title = deeper.first
                    chapters.addAll(deeper.second)
                }
            }
        }
        return title to chapters.distinctBy { it.chapterId }
    }

    private fun looksLikeRealChapter(chapter: MpChapter): Boolean =
        chapter.chapterId > 0 && (chapter.chapterName.isNotBlank() || chapter.chapterNumber > 0)

    private fun parseSuccessMangaViewer(bytes: ByteArray): Pair<List<MpPage>, String> {
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            if (fn == 10) {
                val msg = readMessage(input) ?: break
                return parseMangaViewerView(msg)
            }
            if (!skipField(input, tag)) break
        }
        return Pair(emptyList(), "")
    }

    private fun parseAllTitlesView(bytes: ByteArray): List<MpTitle> {
        val titles = mutableListOf<MpTitle>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            if (fn == 1) {
                val msg = readMessage(input) ?: break
                parseTitle(msg)?.let { titles.add(it) }
            } else {
                if (!skipField(input, tag)) break
            }
        }
        return titles
    }

    private fun parseTitleDetailView(bytes: ByteArray): Pair<MpTitle?, List<MpChapter>> {
        var title: MpTitle? = null
        val chapters = mutableListOf<MpChapter>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            when (fn) {
                1 -> {
                    val msg = readMessage(input) ?: break
                    parseTitle(msg)?.let { title = it }
                }
                // Field 2 was the chapter list in the older TitleDetailView; V3
                // moved it to 38, which is why every series came back with no
                // chapters. Both are read so either response shape works. Wire
                // structure confirmed against title_detailV3 for One Piece:
                // .1(Success) -> .8(TitleDetailView) -> .38(Chapter) x1192,
                // each carrying name at field 4.
                2, 38 -> {
                    val msg = readMessage(input) ?: break
                    parseChapter(msg)?.let { chapters.add(it) }
                }
                else -> if (!skipField(input, tag)) break
            }
        }
        return Pair(title, chapters)
    }

    private fun parseMangaViewerView(bytes: ByteArray): Pair<List<MpPage>, String> {
        val pages = mutableListOf<MpPage>()
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            val fn = tag shr 3
            if (fn == 1) {
                val msg = readMessage(input) ?: break
                parsePage(msg)?.let { pages.add(it) }
            } else {
                if (!skipField(input, tag)) break
            }
        }
        return Pair(pages, "")
    }

    private fun parseTitle(bytes: ByteArray): MpTitle? {
        var id = 0
        var name = ""
        var author = ""
        var cover = ""
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            when (tag shr 3) {
                1 -> id = input.readInt32()
                2 -> name = input.readString()
                3 -> author = input.readString()
                4 -> cover = input.readString()
                else -> if (!skipField(input, tag)) break
            }
        }
        return if (id > 0 && name.isNotBlank()) MpTitle(id, name, author, cover) else null
    }

    private fun parseChapter(bytes: ByteArray): MpChapter? {
        var chapterId = 0
        var titleId = 0
        var chapterNumber = 0
        var chapterName = ""
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            when (tag shr 3) {
                1 -> titleId = input.readInt32()
                2 -> chapterId = input.readInt32()
                3 -> chapterNumber = input.readInt32()
                4 -> chapterName = input.readString()
                else -> if (!skipField(input, tag)) break
            }
        }
        return if (chapterId > 0) MpChapter(chapterId, titleId, chapterNumber, chapterName) else null
    }

    private fun parsePage(bytes: ByteArray): MpPage? {
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            if ((tag shr 3) == 1) {
                val msg = readMessage(input) ?: break
                return parseMangaPage(msg)
            }
            if (!skipField(input, tag)) break
        }
        return null
    }

    private fun parseMangaPage(bytes: ByteArray): MpPage? {
        var imageUrl = ""
        var encryptionKey = ""
        val input = CodedInputStream.newInstance(bytes)
        while (true) {
            val tag = readTag(input) ?: break
            when (tag shr 3) {
                1 -> imageUrl = input.readString()
                5 -> encryptionKey = input.readString()
                else -> if (!skipField(input, tag)) break
            }
        }
        return if (imageUrl.isNotBlank()) MpPage(imageUrl, encryptionKey) else null
    }

    /**
     * Reads the next field tag, or null when the message is exhausted.
     *
     * CodedInputStream signals end-of-stream by returning tag 0 rather than by
     * throwing. Passing that 0 on to [skipField] throws "invalid tag", which used
     * to be swallowed silently -- so every `while (true)` loop in this file read
     * tag 0 again, forever. That spun a Dispatchers.IO thread at 100% and left
     * MANGA Plus stuck on "Loading..." in the browse feed, uncancellable because a
     * tight non-suspending loop never reaches a cancellation point.
     */
    /** Deep enough for the nested views, shallow enough to stay cheap. */
    private const val MAX_SCAN_DEPTH = 4

    private fun readTag(input: CodedInputStream): Int? {
        return try { input.readTag().takeIf { it != 0 } } catch (_: Exception) { null }
    }

    private fun readMessage(input: CodedInputStream): ByteArray? {
        return try {
            val size = input.readRawVarint32()
            input.readRawBytes(size)
        } catch (_: Exception) { null }
    }

    /** Skips an unknown field. Returns false when the stream can no longer be
     *  advanced, which is the caller's signal to stop reading instead of looping. */
    private fun skipField(input: CodedInputStream, tag: Int): Boolean {
        return try { input.skipField(tag) } catch (_: Exception) { false }
    }

    fun xorDecrypt(data: ByteArray, keyHex: String): ByteArray {
        if (keyHex.isBlank()) return data
        val key = hexToBytes(keyHex)
        if (key.isEmpty()) return data
        return ByteArray(data.size) { i ->
            (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val s = hex.filter { it in "0123456789abcdefABCDEF" }
        return ByteArray(s.length / 2) {
            ((s[it * 2].digitToIntOrNull(16) ?: 0).shl(4) or
                (s[it * 2 + 1].digitToIntOrNull(16) ?: 0)).toByte()
        }
    }
}
