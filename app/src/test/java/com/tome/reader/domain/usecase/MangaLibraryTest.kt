package com.tome.reader.domain.usecase

import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.domain.model.ContentKind
import com.tome.reader.domain.model.LibraryItem
import com.tome.reader.domain.model.LibraryQuery
import com.tome.reader.domain.model.ReadingProgress
import com.tome.reader.domain.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaLibraryTest {
    @Test fun legacyNovelsRemainRecognizableButAreNotImportable() {
        assertEquals(BookFormat.EPUB, BookFormat.valueOf("EPUB"))
        assertEquals(BookFormat.TXT, BookFormat.valueOf("TXT"))
        assertFalse(BookFormat.isSupported("EPUB"))
        assertFalse(BookFormat.isSupported("TXT"))
        assertFalse(BookFormat.isSupported("UNKNOWN"))
        assertTrue(BookFormat.isSupported("PDF"))
        assertEquals(setOf("CBZ", "CBR", "ZIP", "IMAGE_FOLDER", "PDF"), BookFormat.supported.map { it.name }.toSet())
    }

    @Test fun legacyNovelsAreHiddenEvenWhenTheirTitleMatchesSearch() {
        val items = listOf(
            comic(1, "Moon Manga", BookFormat.CBZ),
            comic(2, "Moon Novel", BookFormat.EPUB),
            comic(3, "Moon Clip", BookFormat.TXT),
            LibraryItem.SeriesItem(Series(4, "Moon Novel Series", null, ContentKind.TEXT, 3, 0, null, 0)),
        )
        val result = ApplyLibraryQuery()(items, LibraryQuery(search = "moon"), emptyMap())
        assertEquals(listOf("Moon Manga"), result.map { it.title })
        assertEquals(4, items.size) // Filtering never mutates legacy records.
    }

    @Test fun comicCollectionsAndFormatsStillFilterTogether() {
        val items = listOf(comic(1, "One", BookFormat.CBZ), comic(2, "Two", BookFormat.PDF))
        val result = ApplyLibraryQuery()(
            items, LibraryQuery(formatFilter = BookFormat.PDF, collectionId = 7),
            mapOf(1L to setOf(7L), 2L to setOf(7L)),
        )
        assertEquals(listOf("Two"), result.map { it.title })
    }

    private fun comic(id: Long, title: String, format: BookFormat) = LibraryItem.BookItem(
        Book(id, "file:///comic$id", false, title, null, format, null, 10, 0, null,
            ReadingProgress(), null, null),
    )
}
