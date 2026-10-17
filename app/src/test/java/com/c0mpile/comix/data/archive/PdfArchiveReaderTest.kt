package com.c0mpile.comix.data.archive

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.c0mpile.comix.data.source.ByteArrayRandomAccessSource
import com.c0mpile.comix.domain.model.ComicFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [PdfArchiveReader] and [PdfArchiveReaderFactory].
 */
class PdfArchiveReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val mockRenderer = mockk<PdfRenderer>(relaxed = true)
    private val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true)
    private lateinit var cacheDir: File
    private lateinit var factory: PdfArchiveReaderFactory

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("pdf_cache")
        factory = PdfArchiveReaderFactory(
            cacheDir = cacheDir,
            ioDispatcher = testDispatcher,
            pdfRendererFactory = { mockRenderer },
            pfdOpener = { mockPfd }
        )
    }

    private fun createValidPdfBytes(): ByteArray {
        val header = "%PDF-1.4\n".toByteArray(Charsets.US_ASCII)
        val body = "1 0 obj << /Type /Catalog >> endobj\nxref\ntrailer << >>\nstartxref\n%%EOF".toByteArray(Charsets.US_ASCII)
        return header + body
    }

    @Test
    fun wrongFormatThrowsCorruptArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val source = ByteArrayRandomAccessSource(createValidPdfBytes())

        val result = runCatching {
            factory.create(source, ComicFormat.CBZ, "test.cbz")
        }

        assertTrue(result.exceptionOrNull() is CorruptArchiveException)
        assertTrue(source.isClosed)
    }

    @Test
    fun emptySourceThrowsEmptyArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val source = ByteArrayRandomAccessSource(ByteArray(0))

        val result = runCatching {
            factory.create(source, ComicFormat.PDF, "empty.pdf")
        }

        assertTrue(result.exceptionOrNull() is EmptyArchiveException)
        assertTrue(source.isClosed)
    }

    @Test
    fun missingPdfSignatureThrowsCorruptArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val source = ByteArrayRandomAccessSource("Not a PDF file content".toByteArray())

        val result = runCatching {
            factory.create(source, ComicFormat.PDF, "corrupt.pdf")
        }

        assertTrue(result.exceptionOrNull() is CorruptArchiveException)
        assertTrue(source.isClosed)
    }

    @Test
    fun shortHeaderThrowsCorruptArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val source = ByteArrayRandomAccessSource(byteArrayOf(0x25, 0x50, 0x44))

        val result = runCatching {
            factory.create(source, ComicFormat.PDF, "short.pdf")
        }

        assertTrue(result.exceptionOrNull() is CorruptArchiveException)
        assertTrue(source.isClosed)
    }

    @Test
    fun rendererInstantiationFailureThrowsCorruptArchiveException() = runTest(testDispatcher) {
        val failingFactory = PdfArchiveReaderFactory(
            cacheDir = cacheDir,
            ioDispatcher = testDispatcher,
            pdfRendererFactory = { throw IOException("Failed to initialize native renderer") },
            pfdOpener = { mockPfd }
        )
        val source = ByteArrayRandomAccessSource(createValidPdfBytes())

        val result = runCatching {
            failingFactory.create(source, ComicFormat.PDF, "fail.pdf")
        }

        assertTrue(result.exceptionOrNull() is CorruptArchiveException)
        assertTrue(source.isClosed)
    }

    @Test
    fun emptyPdfWithZeroPagesThrowsEmptyArchiveException() = runTest(testDispatcher) {
        every { mockRenderer.pageCount } returns 0
        val source = ByteArrayRandomAccessSource(createValidPdfBytes())

        val result = runCatching {
            factory.create(source, ComicFormat.PDF, "nopages.pdf")
        }

        assertTrue(result.exceptionOrNull() is EmptyArchiveException)
        verify(exactly = 1) { mockRenderer.close() }
        assertTrue(source.isClosed)
    }

    @Test
    fun validPdfGeneratesPagesWithUSLocaleNames() = runTest(testDispatcher) {
        every { mockRenderer.pageCount } returns 3
        val source = ByteArrayRandomAccessSource(createValidPdfBytes())

        val reader = factory.create(source, ComicFormat.PDF, "sample.pdf")
        val pages = reader.getPages()

        assertEquals(3, pages.size)
        assertEquals("page_0001.pdf", pages[0].entryName)
        assertEquals(0, pages[0].index)
        assertEquals("page_0002.pdf", pages[1].entryName)
        assertEquals(1, pages[1].index)
        assertEquals("page_0003.pdf", pages[2].entryName)
        assertEquals(2, pages[2].index)

        reader.close()
        verify(exactly = 1) { mockRenderer.close() }
        assertTrue(source.isClosed)
    }

    @Test
    fun openPageBytesWithInvalidIndexThrowsIndexOutOfBoundsException() = runTest(testDispatcher) {
        every { mockRenderer.pageCount } returns 2
        val source = ByteArrayRandomAccessSource(createValidPdfBytes())
        val reader = factory.create(source, ComicFormat.PDF, "sample.pdf")

        val resultNegative = runCatching {
            reader.openPageBytes(-1)
        }
        assertTrue(resultNegative.exceptionOrNull() is IndexOutOfBoundsException)

        val resultOutOfRange = runCatching {
            reader.openPageBytes(2)
        }
        assertTrue(resultOutOfRange.exceptionOrNull() is IndexOutOfBoundsException)

        reader.close()
    }

    @Test
    fun openPageBytesAfterCloseThrowsIOException() = runTest(testDispatcher) {
        every { mockRenderer.pageCount } returns 1
        val source = ByteArrayRandomAccessSource(createValidPdfBytes())
        val reader = factory.create(source, ComicFormat.PDF, "sample.pdf")

        reader.close()

        val result = runCatching {
            reader.openPageBytes(0)
        }
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
