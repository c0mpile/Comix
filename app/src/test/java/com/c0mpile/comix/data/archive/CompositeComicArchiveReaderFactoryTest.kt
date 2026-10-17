package com.c0mpile.comix.data.archive

import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.domain.model.ComicFormat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for [CompositeComicArchiveReaderFactory] verifying format-based delegation.
 */
class CompositeComicArchiveReaderFactoryTest {

    private val cbzFactory = mockk<CbzArchiveReaderFactory>()
    private val pdfFactory = mockk<PdfArchiveReaderFactory>()
    private val compositeFactory = CompositeComicArchiveReaderFactory(
        cbzFactory = cbzFactory,
        pdfFactory = pdfFactory
    )

    private val source = mockk<RandomAccessSource>(relaxed = true)
    private val mockReader = mockk<ComicArchiveReader>()

    @Test
    fun delegatesCbzFormatToCbzFactory() = runTest {
        coEvery { cbzFactory.create(source, ComicFormat.CBZ, "comic.cbz") } returns mockReader

        val reader = compositeFactory.create(source, ComicFormat.CBZ, "comic.cbz")

        assertEquals(mockReader, reader)
        coVerify(exactly = 1) { cbzFactory.create(source, ComicFormat.CBZ, "comic.cbz") }
        coVerify(exactly = 0) { pdfFactory.create(any(), any(), any()) }
    }

    @Test
    fun delegatesPdfFormatToPdfFactory() = runTest {
        coEvery { pdfFactory.create(source, ComicFormat.PDF, "comic.pdf") } returns mockReader

        val reader = compositeFactory.create(source, ComicFormat.PDF, "comic.pdf")

        assertEquals(mockReader, reader)
        coVerify(exactly = 1) { pdfFactory.create(source, ComicFormat.PDF, "comic.pdf") }
        coVerify(exactly = 0) { cbzFactory.create(any(), any(), any()) }
    }

    @Test
    fun unsupportedFormatThrowsCorruptArchiveException() = runTest {
        val ex = runCatching {
            compositeFactory.create(source, ComicFormat.CBR, "comic.cbr")
        }.exceptionOrNull()
        org.junit.Assert.assertTrue("Expected CorruptArchiveException, got $ex", ex is CorruptArchiveException)
        io.mockk.verify(exactly = 1) { source.close() }
    }
}
