package com.c0mpile.comix.data.archive

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.c0mpile.comix.data.source.LocalRandomAccessSource
import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.domain.model.ComicFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Instrumented tests for [PdfArchiveReader] and [PdfArchiveReaderFactory] verifying
 * real PDF document rendering, page enumeration, and corrupt archive handling on device.
 */
@RunWith(AndroidJUnit4::class)
class PdfArchiveReaderAndroidTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var factory: PdfArchiveReaderFactory

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("pdf_test_cache")
        factory = PdfArchiveReaderFactory(
            cacheDir = cacheDir,
            ioDispatcher = Dispatchers.IO
        )
    }

    private fun createMultiPagePdfBytes(pageCount: Int = 3): ByteArray {
        val pdfDocument = PdfDocument()
        for (i in 1..pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(100, 150, i).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint().apply {
                color = when (i) {
                    1 -> Color.RED
                    2 -> Color.GREEN
                    else -> Color.BLUE
                }
            }
            canvas.drawRect(0f, 0f, 100f, 150f, paint)
            pdfDocument.finishPage(page)
        }
        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        return outputStream.toByteArray()
    }

    @Test
    fun opensRealMultiPagePdfAndReturnsPagesWithCorrectNames() = runBlocking {
        val pdfBytes = createMultiPagePdfBytes(3)
        val source = TestByteArrayRandomAccessSource(pdfBytes)

        val reader = factory.create(source, ComicFormat.PDF, "sample.pdf")
        val pages = reader.getPages()

        assertEquals(3, pages.size)
        assertEquals(0, pages[0].index)
        assertEquals("page_0001.pdf", pages[0].entryName)
        assertEquals(1, pages[1].index)
        assertEquals("page_0002.pdf", pages[1].entryName)
        assertEquals(2, pages[2].index)
        assertEquals("page_0003.pdf", pages[2].entryName)

        reader.close()
        assertTrue(source.isClosed)
    }

    @Test
    fun rendersPageBytesAsValidPngWithExpectedDimensions() = runBlocking {
        val pdfBytes = createMultiPagePdfBytes(3)
        val source = TestByteArrayRandomAccessSource(pdfBytes)

        val reader = factory.create(source, ComicFormat.PDF, "sample.pdf")

        for (i in 0 until 3) {
            val pageBytes = reader.openPageBytes(i)
            assertTrue(pageBytes.isNotEmpty())

            val bitmap = BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size)
            assertNotNull(bitmap)
            assertEquals(200, bitmap.width)
            assertEquals(300, bitmap.height)

            val centerPixel = bitmap.getPixel(100, 150)
            assertEquals(255, Color.alpha(centerPixel))
            when (i) {
                0 -> {
                    assertTrue(Color.red(centerPixel) > 200)
                    assertTrue(Color.green(centerPixel) < 50)
                    assertTrue(Color.blue(centerPixel) < 50)
                }
                1 -> {
                    assertTrue(Color.green(centerPixel) > 200)
                    assertTrue(Color.red(centerPixel) < 50)
                    assertTrue(Color.blue(centerPixel) < 50)
                }
                2 -> {
                    assertTrue(Color.blue(centerPixel) > 200)
                    assertTrue(Color.red(centerPixel) < 50)
                    assertTrue(Color.green(centerPixel) < 50)
                }
            }
            bitmap.recycle()
        }

        reader.close()
        assertTrue(source.isClosed)
    }

    @Test
    fun rendersPagesFromLocalRandomAccessSource() = runBlocking {
        val pdfBytes = createMultiPagePdfBytes(2)
        val file = tempFolder.newFile("local_test.pdf")
        file.writeBytes(pdfBytes)

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val channel = inputStream.channel
        val localSource = LocalRandomAccessSource(
            pfd = pfd,
            inputStream = inputStream,
            channel = channel,
            sourceSize = file.length()
        )

        val reader = factory.create(localSource, ComicFormat.PDF, "local_test.pdf")
        val pages = reader.getPages()
        assertEquals(2, pages.size)

        val page0Bytes = reader.openPageBytes(0)
        val bitmap = BitmapFactory.decodeByteArray(page0Bytes, 0, page0Bytes.size)
        assertNotNull(bitmap)
        assertEquals(200, bitmap.width)
        assertEquals(300, bitmap.height)
        bitmap.recycle()

        reader.close()
    }

    @Test
    fun corruptPdfWithValidHeaderPrefixThrowsCorruptArchiveException() = runBlocking {
        val validHeader = "%PDF-1.4\n".toByteArray(Charsets.US_ASCII)
        val corruptBody = "Trailer xref broken invalid non-pdf binary content\n%%EOF".toByteArray(Charsets.US_ASCII)
        val corruptBytes = validHeader + corruptBody
        val source = TestByteArrayRandomAccessSource(corruptBytes)

        val result = runCatching {
            factory.create(source, ComicFormat.PDF, "corrupt.pdf")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CorruptArchiveException)
        assertTrue(source.isClosed)
    }

    private class TestByteArrayRandomAccessSource(
        private val bytes: ByteArray
    ) : RandomAccessSource {

        var isClosed: Boolean = false
            private set

        override fun size(): Long {
            checkNotClosed()
            return bytes.size.toLong()
        }

        override fun readAt(
            offset: Long,
            buffer: ByteArray,
            bufferOffset: Int,
            length: Int
        ): Int {
            checkNotClosed()
            if (offset < 0L || offset >= bytes.size) {
                return -1
            }
            val available = (bytes.size - offset).toInt()
            val toRead = min(length, available)
            if (toRead <= 0) {
                return 0
            }
            System.arraycopy(bytes, offset.toInt(), buffer, bufferOffset, toRead)
            return toRead
        }

        override fun close() {
            isClosed = true
        }

        private fun checkNotClosed() {
            if (isClosed) {
                throw IOException("RandomAccessSource is already closed")
            }
        }
    }
}
