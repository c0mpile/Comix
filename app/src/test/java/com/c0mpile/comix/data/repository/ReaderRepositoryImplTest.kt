package com.c0mpile.comix.data.repository

import com.c0mpile.comix.data.archive.ComicArchiveReader
import com.c0mpile.comix.data.archive.ComicArchiveReaderFactory
import com.c0mpile.comix.data.archive.CorruptArchiveException
import com.c0mpile.comix.data.archive.EmptyArchiveException
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.ReadingProgressDao
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.ComicWithProgressProjection
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity
import com.c0mpile.comix.data.source.ComicSource
import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.ReadingProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ReaderRepositoryImpl].
 */
class ReaderRepositoryImplTest {

    private val comicDao = mockk<ComicDao>()
    private val readingProgressDao = mockk<ReadingProgressDao>(relaxed = true)
    private val comicSource = mockk<ComicSource>()
    private val archiveReaderFactory = mockk<ComicArchiveReaderFactory>()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: ReaderRepositoryImpl

    @Before
    fun setUp() {
        repository = ReaderRepositoryImpl(
            comicDao = comicDao,
            readingProgressDao = readingProgressDao,
            comicSource = comicSource,
            archiveReaderFactory = archiveReaderFactory,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getComicReturnsMappedDomainComicWhenFound() = runTest(testDispatcher) {
        val comicEntity = createTestComicEntity(
            comicKey = "key123",
            title = "Batman",
            fileName = "batman.cbz",
            coverCachePath = "/covers/key123.webp"
        )
        val progressEntity = ReadingProgressEntity(
            comicKey = "key123",
            currentPage = 4,
            pageCount = 20,
            isFinished = false,
            updatedAtEpochMillis = 9999L
        )
        val projection = ComicWithProgressProjection(comicEntity, progressEntity)

        coEvery { comicDao.getComicWithProgress("key123") } returns projection

        val comic = repository.getComic("key123")
        assertNotNull(comic)
        assertEquals("key123", comic?.comicKey)
        assertEquals("Batman", comic?.title)
        assertEquals("batman.cbz", comic?.fileName)
        assertEquals("/covers/key123.webp", comic?.coverCachePath)
        assertNotNull(comic?.progress)
        assertEquals(4, comic?.progress?.currentPage)
        assertEquals(20, comic?.progress?.pageCount)
        assertFalse(comic?.progress?.isFinished == true)
        assertEquals(9999L, comic?.progress?.updatedAtEpochMillis)
    }

    @Test
    fun getComicReturnsNullWhenNotFound() = runTest(testDispatcher) {
        coEvery { comicDao.getComicWithProgress("nonExistent") } returns null

        val comic = repository.getComic("nonExistent")
        assertNull(comic)
    }

    @Test
    fun openComicReaderReturnsSuccessWhenPermissionValidAndArchiveOpens() = runTest(testDispatcher) {
        val comic = createTestComic()
        val mockSource = mockk<RandomAccessSource>(relaxed = true)
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)

        coEvery { comicSource.hasPermission("content://comix/folder") } returns true
        coEvery { comicSource.openRandomAccessSource("content://comix/comic.cbz") } returns mockSource
        coEvery { archiveReaderFactory.create(mockSource, ComicFormat.CBZ, "comic.cbz") } returns mockReader

        val result = repository.openComicReader(comic)
        assertTrue(result.isSuccess)
        assertEquals(mockReader, result.getOrNull())
    }

    @Test
    fun openComicReaderReturnsFileNotFoundWhenLocalUriIsNullOrBlank() = runTest(testDispatcher) {
        val nullUriComic = createTestComic(localUri = null)
        val nullResult = repository.openComicReader(nullUriComic)
        assertTrue(nullResult.isFailure)
        val nullException = nullResult.exceptionOrNull()
        assertTrue(nullException is ComicDomainException)
        assertTrue((nullException as ComicDomainException).comicError is ComicError.FileNotFound)

        val blankUriComic = createTestComic(localUri = "   ")
        val blankResult = repository.openComicReader(blankUriComic)
        assertTrue(blankResult.isFailure)
        val blankException = blankResult.exceptionOrNull()
        assertTrue(blankException is ComicDomainException)
        assertTrue((blankException as ComicDomainException).comicError is ComicError.FileNotFound)
    }

    @Test
    fun openComicReaderReturnsPermissionRevokedWhenFolderPermissionRevoked() = runTest(testDispatcher) {
        val comic = createTestComic(folderUri = "content://comix/folder")
        coEvery { comicSource.hasPermission("content://comix/folder") } returns false

        val result = repository.openComicReader(comic)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ComicDomainException)
        val error = (exception as ComicDomainException).comicError
        assertTrue(error is ComicError.PermissionRevoked)
        assertEquals("content://comix/folder", (error as ComicError.PermissionRevoked).uri)
    }

    @Test
    fun openComicReaderReturnsPermissionRevokedWhenOpenThrowsSecurityException() = runTest(testDispatcher) {
        val comic = createTestComic()
        coEvery { comicSource.hasPermission("content://comix/folder") } returns true
        coEvery {
            comicSource.openRandomAccessSource("content://comix/comic.cbz")
        } throws SecurityException("Permission revoked by OS")

        val result = repository.openComicReader(comic)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ComicDomainException)
        val error = (exception as ComicDomainException).comicError
        assertTrue(error is ComicError.PermissionRevoked)
        assertEquals("content://comix/comic.cbz", (error as ComicError.PermissionRevoked).uri)
    }

    @Test
    fun openComicReaderReturnsFileNotFoundWhenOpenThrowsFileNotFoundException() = runTest(testDispatcher) {
        val comic = createTestComic()
        coEvery { comicSource.hasPermission("content://comix/folder") } returns true
        coEvery {
            comicSource.openRandomAccessSource("content://comix/comic.cbz")
        } throws FileNotFoundException("File deleted")

        val result = repository.openComicReader(comic)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ComicDomainException)
        val error = (exception as ComicDomainException).comicError
        assertTrue(error is ComicError.FileNotFound)
        assertEquals("content://comix/comic.cbz", (error as ComicError.FileNotFound).uri)
    }

    @Test
    fun openComicReaderReturnsEmptyArchiveWhenFactoryThrowsEmptyArchiveException() = runTest(testDispatcher) {
        val comic = createTestComic()
        val mockSource = mockk<RandomAccessSource>(relaxed = true)
        coEvery { comicSource.hasPermission("content://comix/folder") } returns true
        coEvery { comicSource.openRandomAccessSource("content://comix/comic.cbz") } returns mockSource
        coEvery {
            archiveReaderFactory.create(mockSource, ComicFormat.CBZ, "comic.cbz")
        } throws EmptyArchiveException("comic.cbz")

        val result = repository.openComicReader(comic)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ComicDomainException)
        val error = (exception as ComicDomainException).comicError
        assertTrue(error is ComicError.EmptyArchive)
        assertEquals("comic.cbz", (error as ComicError.EmptyArchive).fileName)
    }

    @Test
    fun openComicReaderReturnsCorruptArchiveWhenFactoryThrowsCorruptArchiveException() = runTest(testDispatcher) {
        val comic = createTestComic()
        val mockSource = mockk<RandomAccessSource>(relaxed = true)
        coEvery { comicSource.hasPermission("content://comix/folder") } returns true
        coEvery { comicSource.openRandomAccessSource("content://comix/comic.cbz") } returns mockSource
        coEvery {
            archiveReaderFactory.create(mockSource, ComicFormat.CBZ, "comic.cbz")
        } throws CorruptArchiveException("comic.cbz", "Bad Central Directory")

        val result = repository.openComicReader(comic)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ComicDomainException)
        val error = (exception as ComicDomainException).comicError
        assertTrue(error is ComicError.CorruptArchive)
        assertEquals("comic.cbz", (error as ComicError.CorruptArchive).fileName)
        assertEquals("Bad Central Directory", error.reason)
    }

    @Test
    fun openComicReaderReturnsOutOfMemoryWhenFactoryThrowsOutOfMemoryError() = runTest(testDispatcher) {
        val comic = createTestComic()
        val mockSource = mockk<RandomAccessSource>(relaxed = true)
        coEvery { comicSource.hasPermission("content://comix/folder") } returns true
        coEvery { comicSource.openRandomAccessSource("content://comix/comic.cbz") } returns mockSource
        coEvery {
            archiveReaderFactory.create(mockSource, ComicFormat.CBZ, "comic.cbz")
        } throws OutOfMemoryError("Archive directory too large")

        val result = repository.openComicReader(comic)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ComicDomainException)
        val error = (exception as ComicDomainException).comicError
        assertTrue(error is ComicError.OutOfMemory)
    }

    @Test
    fun openComicReaderReturnsUnknownIoErrorWhenFactoryThrowsGenericException() = runTest(testDispatcher) {
        val comic = createTestComic()
        val mockSource = mockk<RandomAccessSource>(relaxed = true)
        coEvery { comicSource.hasPermission("content://comix/folder") } returns true
        coEvery { comicSource.openRandomAccessSource("content://comix/comic.cbz") } returns mockSource
        coEvery {
            archiveReaderFactory.create(mockSource, ComicFormat.CBZ, "comic.cbz")
        } throws IOException("I/O device failed")

        val result = repository.openComicReader(comic)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ComicDomainException)
        val error = (exception as ComicDomainException).comicError
        assertTrue(error is ComicError.UnknownIoError)
        assertEquals("I/O device failed", (error as ComicError.UnknownIoError).message)
    }

    @Test
    fun getReadingProgressReturnsMappedReadingProgressWhenFound() = runTest(testDispatcher) {
        val entity = ReadingProgressEntity(
            comicKey = "comicKey123",
            currentPage = 3,
            pageCount = 10,
            isFinished = false,
            updatedAtEpochMillis = 5555L
        )
        coEvery { readingProgressDao.getProgress("comicKey123") } returns entity

        val progress = repository.getReadingProgress("comicKey123")
        assertNotNull(progress)
        assertEquals("comicKey123", progress?.comicKey)
        assertEquals(3, progress?.currentPage)
        assertEquals(10, progress?.pageCount)
        assertFalse(progress?.isFinished == true)
        assertEquals(5555L, progress?.updatedAtEpochMillis)
    }

    @Test
    fun getReadingProgressReturnsNullWhenNotFound() = runTest(testDispatcher) {
        coEvery { readingProgressDao.getProgress("unknown") } returns null

        val progress = repository.getReadingProgress("unknown")
        assertNull(progress)
    }

    @Test
    fun saveReadingProgressClampsPageAndCalculatesIsFinished() = runTest(testDispatcher) {
        val slot = slot<ReadingProgressEntity>()
        coEvery { readingProgressDao.upsertProgress(capture(slot)) } returns Unit

        // Normal middle page
        repository.saveReadingProgress("key1", currentPage = 2, pageCount = 5)
        assertEquals("key1", slot.captured.comicKey)
        assertEquals(2, slot.captured.currentPage)
        assertEquals(5, slot.captured.pageCount)
        assertFalse(slot.captured.isFinished)

        // Exact last page
        repository.saveReadingProgress("key1", currentPage = 4, pageCount = 5)
        assertEquals(4, slot.captured.currentPage)
        assertTrue(slot.captured.isFinished)

        // Beyond last page clamped to last page
        repository.saveReadingProgress("key1", currentPage = 10, pageCount = 5)
        assertEquals(4, slot.captured.currentPage)
        assertTrue(slot.captured.isFinished)

        // Negative page clamped to 0
        repository.saveReadingProgress("key1", currentPage = -3, pageCount = 5)
        assertEquals(0, slot.captured.currentPage)
        assertFalse(slot.captured.isFinished)
    }

    @Test
    fun saveReadingProgressIgnoresCallWhenPageCountIsZeroOrNegative() = runTest(testDispatcher) {
        repository.saveReadingProgress("key1", currentPage = 0, pageCount = 0)
        repository.saveReadingProgress("key1", currentPage = 1, pageCount = -5)

        coVerify(exactly = 0) { readingProgressDao.upsertProgress(any()) }
    }

    private fun createTestComic(
        comicKey: String = "testComicKey",
        title: String = "Test Comic",
        fileName: String = "comic.cbz",
        format: ComicFormat = ComicFormat.CBZ,
        sourceType: ComicSourceType = ComicSourceType.LOCAL_SAF,
        availability: ComicAvailability = ComicAvailability.LOCAL,
        sizeBytes: Long = 1000L,
        lastModifiedEpochMillis: Long = 2000L,
        pageCount: Int = 10,
        localUri: String? = "content://comix/comic.cbz",
        folderUri: String? = "content://comix/folder",
        progress: ReadingProgress? = null
    ): Comic = Comic(
        comicKey = comicKey,
        title = title,
        fileName = fileName,
        format = format,
        sourceType = sourceType,
        availability = availability,
        sizeBytes = sizeBytes,
        lastModifiedEpochMillis = lastModifiedEpochMillis,
        pageCount = pageCount,
        localUri = localUri,
        folderUri = folderUri,
        progress = progress
    )

    private fun createTestComicEntity(
        comicKey: String = "testComicKey",
        title: String = "Test Comic",
        fileName: String = "comic.cbz",
        format: ComicFormat = ComicFormat.CBZ,
        sourceType: ComicSourceType = ComicSourceType.LOCAL_SAF,
        availability: ComicAvailability = ComicAvailability.LOCAL,
        sizeBytes: Long = 1000L,
        lastModifiedEpochMillis: Long = 2000L,
        pageCount: Int = 10,
        localUri: String? = "content://comix/comic.cbz",
        folderUri: String? = "content://comix/folder",
        coverCachePath: String? = "/covers/test.webp"
    ): ComicEntity = ComicEntity(
        comicKey = comicKey,
        title = title,
        fileName = fileName,
        format = format,
        sourceType = sourceType,
        availability = availability,
        sizeBytes = sizeBytes,
        lastModifiedEpochMillis = lastModifiedEpochMillis,
        pageCount = pageCount,
        localUri = localUri,
        folderUri = folderUri,
        localSampleSha256 = comicKey,
        coverCachePath = coverCachePath
    )
}
