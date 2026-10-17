package com.c0mpile.comix.data.archive

import com.c0mpile.comix.data.source.ByteArrayRandomAccessSource
import com.c0mpile.comix.domain.model.ComicFormat
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [CbzArchiveReader] and [CbzArchiveReaderFactory].
 */
class CbzArchiveReaderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val factory = CbzArchiveReaderFactory(testDispatcher)

    @Test
    fun readingValidArchiveWithStoredAndDeflatedEntriesExtractsExactBytes() = runTest(testDispatcher) {
        val deflatedBytes = "Deflated Image Content 123456789".toByteArray()
        val storedBytes = "Stored Image Content 987654321".toByteArray()
        val zipBytes = createZipArchive(
            TestZipEntry("page01.png", deflatedBytes, method = ZipEntry.DEFLATED),
            TestZipEntry("page02.jpg", storedBytes, method = ZipEntry.STORED)
        )

        val source = ByteArrayRandomAccessSource(zipBytes)
        val reader = factory.create(source, ComicFormat.CBZ, "test.cbz")

        val pages = reader.getPages()
        assertEquals(2, pages.size)
        assertEquals("page01.png", pages[0].entryName)
        assertEquals("page02.jpg", pages[1].entryName)
        assertEquals(0, pages[0].index)
        assertEquals(1, pages[1].index)
        assertEquals(deflatedBytes.size.toLong(), pages[0].uncompressedSize)
        assertEquals(storedBytes.size.toLong(), pages[1].uncompressedSize)

        assertArrayEquals(deflatedBytes, reader.openPageBytes(0))
        assertArrayEquals(storedBytes, reader.openPageBytes(1))

        reader.close()
        assertTrue(source.isClosed)
    }

    @Test
    fun filtersOutNonImagesHiddenFilesAndDirectories() = runTest(testDispatcher) {
        val imageBytes = "Image payload".toByteArray()
        val zipBytes = createZipArchive(
            // Directories
            TestZipEntry("images/", ByteArray(0)),
            TestZipEntry("sub/dir/", ByteArray(0)),
            // macOS metadata
            TestZipEntry("__MACOSX/._01.jpg", "metadata".toByteArray()),
            TestZipEntry("sub/__MACOSX/._02.jpg", "metadata".toByteArray()),
            // Dot files / hidden files
            TestZipEntry(".DS_Store", "ds".toByteArray()),
            TestZipEntry(".hidden_page.jpg", imageBytes),
            TestZipEntry("dir/.DS_Store", "ds".toByteArray()),
            TestZipEntry("dir/.dotfile.png", imageBytes),
            // Non-image files
            TestZipEntry("ComicInfo.xml", "<comic/>".toByteArray()),
            TestZipEntry("notes.txt", "notes".toByteArray()),
            TestZipEntry("audio.mp3", "audio".toByteArray()),
            // Supported image formats (case-insensitive)
            TestZipEntry("01.jpg", imageBytes),
            TestZipEntry("02.JPEG", imageBytes),
            TestZipEntry("03.png", imageBytes),
            TestZipEntry("04.webp", imageBytes),
            TestZipEntry("05.gif", imageBytes),
            TestZipEntry("06.BMP", imageBytes)
        )

        val source = ByteArrayRandomAccessSource(zipBytes)
        val reader = factory.create(source, ComicFormat.CBZ)

        val pages = reader.getPages()
        val names = pages.map { it.entryName }
        assertEquals(
            listOf("01.jpg", "02.JPEG", "03.png", "04.webp", "05.gif", "06.BMP"),
            names
        )
        assertEquals(6, pages.size)
        for (i in 0 until 6) {
            assertEquals(i, pages[i].index)
        }

        reader.close()
    }

    @Test
    fun sortsPagesInNaturalOrderAndAssignsZeroBasedIndices() = runTest(testDispatcher) {
        val dummy = "img".toByteArray()
        val zipBytes = createZipArchive(
            TestZipEntry("page10.jpg", dummy),
            TestZipEntry("page2.jpg", dummy),
            TestZipEntry("page1.jpg", dummy),
            TestZipEntry("page20.jpg", dummy)
        )

        val source = ByteArrayRandomAccessSource(zipBytes)
        val reader = factory.create(source, ComicFormat.CBZ)

        val pages = reader.getPages()
        assertEquals(4, pages.size)
        assertEquals("page1.jpg", pages[0].entryName)
        assertEquals(0, pages[0].index)
        assertEquals("page2.jpg", pages[1].entryName)
        assertEquals(1, pages[1].index)
        assertEquals("page10.jpg", pages[2].entryName)
        assertEquals(2, pages[2].index)
        assertEquals("page20.jpg", pages[3].entryName)
        assertEquals(3, pages[3].index)

        reader.close()
    }

    @Test
    fun emptyZeroByteSourceThrowsEmptyArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val source = ByteArrayRandomAccessSource(ByteArray(0))
        try {
            factory.create(source, ComicFormat.CBZ)
            fail("Expected EmptyArchiveException for 0-byte source")
        } catch (expected: EmptyArchiveException) {
            assertTrue(source.isClosed)
        }
    }

    @Test
    fun validZipWithOnlyNonImageFilesThrowsEmptyArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val zipBytes = createZipArchive(
            TestZipEntry("ComicInfo.xml", "<comic/>".toByteArray()),
            TestZipEntry("notes.txt", "Some text".toByteArray())
        )
        val source = ByteArrayRandomAccessSource(zipBytes)
        try {
            factory.create(source, ComicFormat.CBZ)
            fail("Expected EmptyArchiveException when archive contains no supported image entries")
        } catch (expected: EmptyArchiveException) {
            assertTrue(source.isClosed)
        }
    }

    @Test
    fun corruptArchiveMissingEocdThrowsCorruptArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val randomBytes = ByteArray(100) { (it * 7).toByte() }
        val source = ByteArrayRandomAccessSource(randomBytes)
        try {
            factory.create(source, ComicFormat.CBZ)
            fail("Expected CorruptArchiveException for non-ZIP random bytes")
        } catch (expected: CorruptArchiveException) {
            assertTrue(source.isClosed)
        }
    }

    @Test
    fun archiveSmallerThanMinimumEocdThrowsCorruptArchiveExceptionAndClosesSource() = runTest(testDispatcher) {
        val tinyBytes = ByteArray(10) { 0x42.toByte() }
        val source = ByteArrayRandomAccessSource(tinyBytes)
        try {
            factory.create(source, ComicFormat.CBZ)
            fail("Expected CorruptArchiveException for tiny archive < 22 bytes")
        } catch (expected: CorruptArchiveException) {
            assertTrue(source.isClosed)
        }
    }

    @Test
    fun corruptedDeflatedPayloadThrowsCorruptArchiveException() = runTest(testDispatcher) {
        val originalBytes = "A".repeat(500).toByteArray()
        val zipBytes = createZipArchive(
            TestZipEntry("page1.png", originalBytes, method = ZipEntry.DEFLATED)
        )
        // Local header for page1.png is 30 bytes + 9 bytes name = 39 bytes.
        // Mutate deflated payload starting at byte 42.
        val corruptedZip = zipBytes.copyOf()
        for (i in 42 until minOf(60, corruptedZip.size - 22)) {
            corruptedZip[i] = 0xFF.toByte()
        }

        val source = ByteArrayRandomAccessSource(corruptedZip)
        val reader = factory.create(source, ComicFormat.CBZ)
        try {
            reader.openPageBytes(0)
            fail("Expected CorruptArchiveException for corrupted deflated stream")
        } catch (expected: CorruptArchiveException) {
            // Expected
        } finally {
            reader.close()
        }
    }

    @Test
    fun entryDataExceedingFileSizeThrowsCorruptArchiveException() = runTest(testDispatcher) {
        val zipBytes = createZipArchive(
            TestZipEntry("page1.png", "image data".toByteArray(), method = ZipEntry.STORED)
        )
        // Mutate compressed size in Central Directory entry to exceed total file bounds
        val mutatedZip = zipBytes.copyOf()
        for (i in 0 until mutatedZip.size - 4) {
            if (mutatedZip[i] == 0x50.toByte() &&
                mutatedZip[i + 1] == 0x4b.toByte() &&
                mutatedZip[i + 2] == 0x01.toByte() &&
                mutatedZip[i + 3] == 0x02.toByte()
            ) {
                // Central Directory entry: compressed size is at offset 20 (4 bytes LE)
                mutatedZip[i + 20] = 0x00.toByte()
                mutatedZip[i + 21] = 0x00.toByte()
                mutatedZip[i + 22] = 0x05.toByte()
                mutatedZip[i + 23] = 0x00.toByte() // 0x00050000 = 327,680 bytes
                break
            }
        }

        val source = ByteArrayRandomAccessSource(mutatedZip)
        val reader = factory.create(source, ComicFormat.CBZ)
        try {
            reader.openPageBytes(0)
            fail("Expected CorruptArchiveException when entry data exceeds file bounds")
        } catch (expected: CorruptArchiveException) {
            // Expected
        } finally {
            reader.close()
        }
    }

    @Test
    fun corruptLocalHeaderSignatureThrowsCorruptArchiveException() = runTest(testDispatcher) {
        val zipBytes = createZipArchive(
            TestZipEntry("page1.jpg", "image1".toByteArray(), method = ZipEntry.STORED)
        )
        val mutatedZip = zipBytes.copyOf()
        mutatedZip[0] = 0x00.toByte()

        val source = ByteArrayRandomAccessSource(mutatedZip)
        val reader = factory.create(source, ComicFormat.CBZ)
        try {
            reader.openPageBytes(0)
            fail("Expected CorruptArchiveException for invalid local file header signature")
        } catch (expected: CorruptArchiveException) {
            // Expected
        } finally {
            reader.close()
        }
    }

    @Test
    fun outOfBoundsPageIndexThrowsIndexOutOfBoundsException() = runTest(testDispatcher) {
        val zipBytes = createZipArchive(
            TestZipEntry("page1.jpg", "image1".toByteArray()),
            TestZipEntry("page2.jpg", "image2".toByteArray())
        )
        val source = ByteArrayRandomAccessSource(zipBytes)
        val reader = factory.create(source, ComicFormat.CBZ)

        try {
            reader.openPageBytes(-1)
            fail("Expected IndexOutOfBoundsException for negative pageIndex")
        } catch (expected: IndexOutOfBoundsException) {
            // Expected
        }

        try {
            reader.openPageBytes(2)
            fail("Expected IndexOutOfBoundsException for pageIndex >= pageCount")
        } catch (expected: IndexOutOfBoundsException) {
            // Expected
        }

        reader.close()
    }

    @Test
    fun unsupportedFormatThrowsIllegalArgumentExceptionAndClosesSource() = runTest(testDispatcher) {
        val source = ByteArrayRandomAccessSource(ByteArray(100))
        try {
            factory.create(source, ComicFormat.CBR)
            fail("Expected IllegalArgumentException for unsupported format")
        } catch (expected: IllegalArgumentException) {
            assertTrue(source.isClosed)
        }
    }

    @Test
    fun extractMetadataParsesComicInfoXmlWhenPresentAndReturnsNullWhenAbsent() = runTest(testDispatcher) {
        val comicInfoXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo>
                <Title>Chapter 1: The Beginning</Title>
                <Series>Saga</Series>
                <Number>1</Number>
                <Writer>Brian K. Vaughan</Writer>
                <Penciller>Fiona Staples</Penciller>
                <Publisher>Image Comics</Publisher>
                <Year>2012</Year>
                <Month>3</Month>
            </ComicInfo>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val imageBytes = "Image binary content".toByteArray()

        val zipWithMetadata = createZipArchive(
            TestZipEntry("page01.jpg", imageBytes),
            TestZipEntry("page02.jpg", imageBytes),
            TestZipEntry("ComicInfo.xml", comicInfoXml)
        )

        val sourceWithMetadata = ByteArrayRandomAccessSource(zipWithMetadata)
        val readerWithMetadata = factory.create(sourceWithMetadata, ComicFormat.CBZ, "saga_01.cbz")

        val metadata = readerWithMetadata.extractMetadata()
        assertNotNull(metadata)
        assertEquals("Chapter 1: The Beginning", metadata?.title)
        assertEquals("Saga", metadata?.series)
        assertEquals("1", metadata?.number)
        assertEquals("Brian K. Vaughan", metadata?.writer)
        assertEquals("Fiona Staples", metadata?.penciller)
        assertEquals("Image Comics", metadata?.publisher)
        assertEquals(2012, metadata?.releaseYear)
        assertEquals(3, metadata?.releaseMonth)
        assertEquals("Saga #1", metadata?.formattedSeriesAndNumber)
        assertEquals("2012-03", metadata?.formattedReleaseDate)

        readerWithMetadata.close()

        val zipWithoutMetadata = createZipArchive(
            TestZipEntry("page01.jpg", imageBytes),
            TestZipEntry("page02.jpg", imageBytes)
        )

        val sourceWithoutMetadata = ByteArrayRandomAccessSource(zipWithoutMetadata)
        val readerWithoutMetadata = factory.create(sourceWithoutMetadata, ComicFormat.CBZ, "no_meta.cbz")

        val absentMetadata = readerWithoutMetadata.extractMetadata()
        assertNull(absentMetadata)

        readerWithoutMetadata.close()
    }
}

/**
 * Descriptor for a synthetic ZIP entry used in archive tests.
 *
 * @param name Archive path for the entry.
 * @param content Raw uncompressed bytes of the entry payload.
 * @param method Storage method, either [ZipEntry.DEFLATED] or [ZipEntry.STORED].
 */
data class TestZipEntry(
    val name: String,
    val content: ByteArray,
    val method: Int = ZipEntry.DEFLATED
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestZipEntry

        if (name != other.name) return false
        if (!content.contentEquals(other.content)) return false
        if (method != other.method) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + method
        return result
    }
}

/**
 * Generates an in-memory ZIP archive from a sequence of [TestZipEntry] specifications.
 */
private fun createZipArchive(vararg entries: TestZipEntry): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
        for (entry in entries) {
            val zipEntry = ZipEntry(entry.name)
            zipEntry.method = entry.method
            if (entry.method == ZipEntry.STORED) {
                zipEntry.size = entry.content.size.toLong()
                zipEntry.compressedSize = entry.content.size.toLong()
                val crc = CRC32()
                crc.update(entry.content)
                zipEntry.crc = crc.value
            }
            zos.putNextEntry(zipEntry)
            zos.write(entry.content)
            zos.closeEntry()
        }
    }
    return baos.toByteArray()
}
