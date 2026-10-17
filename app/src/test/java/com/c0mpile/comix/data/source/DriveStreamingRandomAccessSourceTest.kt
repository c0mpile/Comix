package com.c0mpile.comix.data.source

import com.c0mpile.comix.data.remote.drive.DriveApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DriveStreamingRandomAccessSource] validating chunked Range downloads,
 * boundary spanning, in-memory LRU caching, disk cache persistence, and error handling.
 */
class DriveStreamingRandomAccessSourceTest {

    private val apiClient = mockk<DriveApiClient>()
    private lateinit var tempDir: File
    private val fullData = ByteArray(150) { it.toByte() }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("drive_stream_test").toFile()

        coEvery { apiClient.downloadRange("file-1", 0L, 63L) } returns fullData.copyOfRange(0, 64)
        coEvery { apiClient.downloadRange("file-1", 64L, 127L) } returns fullData.copyOfRange(64, 128)
        coEvery { apiClient.downloadRange("file-1", 128L, 149L) } returns fullData.copyOfRange(128, 150)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun readAtWithinSingleChunkDownloadsExpectedRangeAndReturnsBytes() {
        val source = DriveStreamingRandomAccessSource(
            fileId = "file-1",
            sourceSize = 150L,
            apiClient = apiClient,
            cacheRootDir = tempDir,
            chunkSizeBytes = 64
        )

        val buffer = ByteArray(20)
        val bytesRead = source.readAt(offset = 10L, buffer = buffer, bufferOffset = 0, length = 20)

        assertEquals(20, bytesRead)
        assertArrayEquals(fullData.copyOfRange(10, 30), buffer)
        coVerify(exactly = 1) { apiClient.downloadRange("file-1", 0L, 63L) }

        source.close()
    }

    @Test
    fun readAtSpanningChunkBoundariesDownloadsBothChunks() {
        val source = DriveStreamingRandomAccessSource(
            fileId = "file-1",
            sourceSize = 150L,
            apiClient = apiClient,
            cacheRootDir = tempDir,
            chunkSizeBytes = 64
        )

        val buffer = ByteArray(30)
        val bytesRead = source.readAt(offset = 50L, buffer = buffer, bufferOffset = 0, length = 30)

        assertEquals(30, bytesRead)
        assertArrayEquals(fullData.copyOfRange(50, 80), buffer)
        coVerify(exactly = 1) { apiClient.downloadRange("file-1", 0L, 63L) }
        coVerify(exactly = 1) { apiClient.downloadRange("file-1", 64L, 127L) }

        source.close()
    }

    @Test
    fun repeatedReadsWithinCachedChunkHitCacheWithoutDuplicateDownloads() {
        val source = DriveStreamingRandomAccessSource(
            fileId = "file-1",
            sourceSize = 150L,
            apiClient = apiClient,
            cacheRootDir = tempDir,
            chunkSizeBytes = 64
        )

        val buffer1 = ByteArray(10)
        val read1 = source.readAt(offset = 0L, buffer = buffer1, bufferOffset = 0, length = 10)
        assertEquals(10, read1)
        assertArrayEquals(fullData.copyOfRange(0, 10), buffer1)

        val buffer2 = ByteArray(15)
        val read2 = source.readAt(offset = 20L, buffer = buffer2, bufferOffset = 0, length = 15)
        assertEquals(15, read2)
        assertArrayEquals(fullData.copyOfRange(20, 35), buffer2)

        coVerify(exactly = 1) { apiClient.downloadRange("file-1", 0L, 63L) }

        source.close()
    }

    @Test
    fun newSourceInstanceReadsFromPersistedDiskCacheWithoutCallingNetwork() {
        val source1 = DriveStreamingRandomAccessSource(
            fileId = "file-1",
            sourceSize = 150L,
            apiClient = apiClient,
            cacheRootDir = tempDir,
            chunkSizeBytes = 64
        )

        val buffer1 = ByteArray(20)
        source1.readAt(offset = 0L, buffer = buffer1, bufferOffset = 0, length = 20)
        source1.close()

        coVerify(exactly = 1) { apiClient.downloadRange("file-1", 0L, 63L) }

        // Second instance with same cacheRootDir and fileId should read from disk
        val source2 = DriveStreamingRandomAccessSource(
            fileId = "file-1",
            sourceSize = 150L,
            apiClient = apiClient,
            cacheRootDir = tempDir,
            chunkSizeBytes = 64
        )

        val buffer2 = ByteArray(25)
        val read2 = source2.readAt(offset = 5L, buffer = buffer2, bufferOffset = 0, length = 25)
        assertEquals(25, read2)
        assertArrayEquals(fullData.copyOfRange(5, 30), buffer2)

        // No additional network call should have occurred
        coVerify(exactly = 1) { apiClient.downloadRange("file-1", 0L, 63L) }

        source2.close()
    }

    @Test
    fun readAtBeyondSizeReturnsNegativeOneAndReadAtZeroLengthReturnsZero() {
        val source = DriveStreamingRandomAccessSource(
            fileId = "file-1",
            sourceSize = 150L,
            apiClient = apiClient,
            cacheRootDir = tempDir,
            chunkSizeBytes = 64
        )

        val buffer = ByteArray(10)
        assertEquals(0, source.readAt(0L, buffer, 0, 0))
        assertEquals(-1, source.readAt(150L, buffer, 0, 10))
        assertEquals(-1, source.readAt(200L, buffer, 0, 10))

        source.close()
    }

    @Test
    fun readAtAfterCloseThrowsIOException() {
        val source = DriveStreamingRandomAccessSource(
            fileId = "file-1",
            sourceSize = 150L,
            apiClient = apiClient,
            cacheRootDir = tempDir,
            chunkSizeBytes = 64
        )

        source.close()

        val buffer = ByteArray(10)
        assertThrows(IOException::class.java) {
            source.readAt(0L, buffer, 0, 10)
        }
    }
}
