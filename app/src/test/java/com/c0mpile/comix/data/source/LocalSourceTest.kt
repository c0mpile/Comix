package com.c0mpile.comix.data.source

import com.c0mpile.comix.domain.model.ComicFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [LocalSource] companion helpers.
 */
class LocalSourceTest {

    @Test
    fun detectSupportedFormatIdentifiesCbzAndPdf() {
        assertEquals(ComicFormat.CBZ, LocalSource.detectSupportedFormat("comic.cbz"))
        assertEquals(ComicFormat.CBZ, LocalSource.detectSupportedFormat("comic.CBZ"))
        assertEquals(ComicFormat.PDF, LocalSource.detectSupportedFormat("issue1.pdf"))
        assertEquals(ComicFormat.PDF, LocalSource.detectSupportedFormat("issue1.PDF"))

        assertNull(LocalSource.detectSupportedFormat("comic.cbr"))
        assertNull(LocalSource.detectSupportedFormat("comic.cb7"))
        assertNull(LocalSource.detectSupportedFormat("text.txt"))
        assertNull(LocalSource.detectSupportedFormat(".hidden.cbz"))
        assertNull(LocalSource.detectSupportedFormat(".hidden.pdf"))
    }
}
