package com.tome.reader.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDownloaderTest {
    @Test fun comicLinksAcceptQueriesFragmentsAndCase() {
        assertTrue(WebDownloader.isSupportedFileUrl("https://example.org/chapter.CBZ?token=x#download"))
        assertTrue(WebDownloader.isSupportedFileUrl("https://example.org/chapter.rar"))
        assertTrue(WebDownloader.isSupportedFileUrl("https://example.org/volume.pdf#page=1"))
    }

    @Test fun novelAndArticleLinksAreNotOfferedAsComicDownloads() {
        assertFalse(WebDownloader.isSupportedFileUrl("https://example.org/novel.epub"))
        assertFalse(WebDownloader.isSupportedFileUrl("https://example.org/chapter.txt?file=comic.cbz"))
        assertFalse(WebDownloader.isSupportedFileUrl("https://example.org/article.html"))
    }
}
