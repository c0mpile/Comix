package com.c0mpile.comix.data.repository

import app.cash.turbine.test
import com.c0mpile.comix.data.archive.ComicArchiveReader
import com.c0mpile.comix.data.archive.ComicArchiveReaderFactory
import com.c0mpile.comix.data.archive.CorruptArchiveException
import com.c0mpile.comix.data.archive.EmptyArchiveException
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.DriveFolderDao
import com.c0mpile.comix.data.local.dao.LibraryDao
import com.c0mpile.comix.data.local.dao.LibraryFolderDao
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.ComicWithProgressProjection
import com.c0mpile.comix.data.local.entity.LibraryEntity
import com.c0mpile.comix.data.local.entity.LibraryFolderEntity
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity
import com.c0mpile.comix.data.source.ComicKeyHasher
import com.c0mpile.comix.data.source.ComicSource
import com.c0mpile.comix.data.source.DiscoveredComicFile
import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.domain.model.BulkComicMetadataUpdate
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicPage
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.SingleComicMetadataUpdate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
 * Unit tests for [LibraryRepositoryImpl].
 */
class LibraryRepositoryImplTest {

    private val comicDao = mockk<ComicDao>(relaxed = true)
    private val libraryFolderDao = mockk<LibraryFolderDao>(relaxed = true)
    private val libraryDao = mockk<LibraryDao>(relaxed = true)
    private val driveFolderDao = mockk<DriveFolderDao>(relaxed = true)
    private val comicSource = mockk<ComicSource>()
    private val comicKeyHasher = mockk<ComicKeyHasher>()
    private val archiveReaderFactory = mockk<ComicArchiveReaderFactory>()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: LibraryRepositoryImpl

    @Before
    fun setUp() {
        repository = LibraryRepositoryImpl(
            comicDao = comicDao,
            libraryFolderDao = libraryFolderDao,
            comicSource = comicSource,
            comicKeyHasher = comicKeyHasher,
            archiveReaderFactory = archiveReaderFactory,
            ioDispatcher = testDispatcher,
            libraryDao = libraryDao,
            driveFolderDao = driveFolderDao
        )
    }

    @Test
    fun observeComicsMapsProjectionListToDomainComics() = runTest(testDispatcher) {
        val comicEntity = ComicEntity(
            comicKey = "comic1",
            title = "Spider-Man",
            fileName = "spiderman.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 1000L,
            lastModifiedEpochMillis = 5000L,
            pageCount = 20,
            localUri = "content://comix/spiderman.cbz",
            folderUri = "content://comix/folder",
            localSampleSha256 = "hash123",
            driveFileId = null,
            driveMd5 = null,
            coverCachePath = "/cache/covers/comic1.webp"
        )
        val progressEntity = ReadingProgressEntity(
            comicKey = "comic1",
            currentPage = 5,
            pageCount = 20,
            isFinished = false,
            updatedAtEpochMillis = 6000L
        )
        val projection = ComicWithProgressProjection(
            comic = comicEntity,
            progress = progressEntity
        )

        every { comicDao.observeAllComicsWithProgress() } returns flowOf(listOf(projection))

        repository.observeComics().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            val domain = result[0]
            assertEquals("comic1", domain.comicKey)
            assertEquals("Spider-Man", domain.title)
            assertEquals("spiderman.cbz", domain.fileName)
            assertEquals(ComicFormat.CBZ, domain.format)
            assertEquals(20, domain.pageCount)
            assertEquals("/cache/covers/comic1.webp", domain.coverCachePath)
            assertNotNull(domain.progress)
            assertEquals(5, domain.progress?.currentPage)
            assertEquals(20, domain.progress?.pageCount)
            assertFalse(domain.progress?.isFinished == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeFoldersMapsEntitiesAndPopulatesHasPersistedPermission() = runTest(testDispatcher) {
        val folder1 = LibraryFolderEntity(
            folderUri = "content://tree/folder1",
            displayName = "Marvel",
            addedAtEpochMillis = 1000L,
            lastScannedAtEpochMillis = 2000L
        )
        val folder2 = LibraryFolderEntity(
            folderUri = "content://tree/folder2",
            displayName = "DC",
            addedAtEpochMillis = 3000L,
            lastScannedAtEpochMillis = null
        )

        every { libraryFolderDao.observeFolders() } returns flowOf(listOf(folder1, folder2))
        coEvery { comicSource.hasPermission("content://tree/folder1") } returns true
        coEvery { comicSource.hasPermission("content://tree/folder2") } returns false

        repository.observeFolders().test {
            val folders = awaitItem()
            assertEquals(2, folders.size)

            assertEquals("content://tree/folder1", folders[0].folderUri)
            assertEquals("Marvel", folders[0].displayName)
            assertEquals(1000L, folders[0].addedAtEpochMillis)
            assertEquals(2000L, folders[0].lastScannedAtEpochMillis)
            assertTrue(folders[0].hasPersistedPermission)

            assertEquals("content://tree/folder2", folders[1].folderUri)
            assertEquals("DC", folders[1].displayName)
            assertNull(folders[1].lastScannedAtEpochMillis)
            assertFalse(folders[1].hasPersistedPermission)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addFolderAndScanInsertsFolderAndUpsertsNewComics() = runTest(testDispatcher) {
        val treeUri = "content://tree/comics"
        coEvery { comicSource.hasPermission(treeUri) } returns true
        coEvery { libraryFolderDao.getFolders() } returns emptyList()
        coEvery { comicSource.getFolderDisplayName(treeUri) } returns "Comics Root"
        coEvery { comicDao.getComicsByFolder(treeUri) } returns emptyList()

        val discovered = DiscoveredComicFile(
            uri = "content://tree/comics/issue1.cbz",
            folderUri = treeUri,
            fileName = "issue1.cbz",
            title = "Issue 1",
            format = ComicFormat.CBZ,
            sizeBytes = 50_000L,
            lastModifiedEpochMillis = 10_000L
        )
        coEvery { comicSource.listComicFiles(treeUri) } returns listOf(discovered)
        coEvery { comicSource.openRandomAccessSource("content://tree/comics/issue1.cbz") } answers {
            mockk<RandomAccessSource>(relaxed = true)
        }
        coEvery { comicKeyHasher.computeLocalSampleHash(any()) } returns "hashIssue1"

        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(24) { index ->
            ComicPage(index, "page_$index.jpg", 1000L, 2000L)
        }
        coEvery { archiveReaderFactory.create(any(), ComicFormat.CBZ, "issue1.cbz") } returns mockReader
        coEvery { comicDao.findByLocalSampleHash("hashIssue1") } returns null

        val result = repository.addFolderAndScan(treeUri)
        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()

        assertEquals(1, scanResult.addedOrUpdatedCount)
        assertEquals(0, scanResult.removedCount)
        assertEquals(0, scanResult.skippedCorruptCount)
        assertTrue(scanResult.errors.isEmpty())

        val folderSlot = slot<LibraryFolderEntity>()
        coVerify { libraryFolderDao.upsertFolder(capture(folderSlot)) }
        assertEquals(treeUri, folderSlot.captured.folderUri)
        assertEquals("Comics Root", folderSlot.captured.displayName)

        val comicSlot = slot<ComicEntity>()
        coVerify { comicDao.upsertComic(capture(comicSlot)) }
        assertEquals("hashIssue1", comicSlot.captured.comicKey)
        assertEquals("Issue 1", comicSlot.captured.title)
        assertEquals(24, comicSlot.captured.pageCount)
        assertEquals("hashIssue1", comicSlot.captured.localSampleSha256)
        assertEquals(treeUri, comicSlot.captured.folderUri)

        coVerify { libraryFolderDao.updateLastScanned(treeUri, any()) }
    }

    @Test
    fun scanUnchangedFileFastPathSkipsHashingAndArchiveReading() = runTest(testDispatcher) {
        val treeUri = "content://tree/comics"
        coEvery { comicSource.hasPermission(treeUri) } returns true
        coEvery { libraryFolderDao.getFolders() } returns emptyList()
        coEvery { comicSource.getFolderDisplayName(treeUri) } returns "Comics"

        val existingComic = ComicEntity(
            comicKey = "existingKey",
            title = "Existing Comic",
            fileName = "existing.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 50_000L,
            lastModifiedEpochMillis = 10_000L,
            pageCount = 10,
            localUri = "content://tree/comics/existing.cbz",
            folderUri = treeUri,
            localSampleSha256 = "existingKey"
        )
        coEvery { comicDao.getComicsByFolder(treeUri) } returns listOf(existingComic)

        val discovered = DiscoveredComicFile(
            uri = "content://tree/comics/existing.cbz",
            folderUri = treeUri,
            fileName = "existing.cbz",
            title = "Existing Comic",
            format = ComicFormat.CBZ,
            sizeBytes = 50_000L,
            lastModifiedEpochMillis = 10_000L
        )
        coEvery { comicSource.listComicFiles(treeUri) } returns listOf(discovered)

        val result = repository.addFolderAndScan(treeUri)
        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()

        assertEquals(0, scanResult.addedOrUpdatedCount)
        assertEquals(0, scanResult.removedCount)
        assertEquals(0, scanResult.skippedCorruptCount)

        coVerify(exactly = 0) { comicSource.openRandomAccessSource(any()) }
        coVerify(exactly = 0) { comicKeyHasher.computeLocalSampleHash(any()) }
        coVerify(exactly = 0) { archiveReaderFactory.create(any(), any(), any()) }
        coVerify(exactly = 0) { comicDao.upsertComic(any()) }
        coVerify(exactly = 0) { comicDao.deleteByKeys(any()) }
    }

    @Test
    fun scanMovedFilePreservesExistingComicKeyAndMetadata() = runTest(testDispatcher) {
        val treeUri = "content://tree/new_folder"
        coEvery { comicSource.hasPermission(treeUri) } returns true
        coEvery { libraryFolderDao.getFolders() } returns emptyList()
        coEvery { comicSource.getFolderDisplayName(treeUri) } returns "New Folder"
        coEvery { comicDao.getComicsByFolder(treeUri) } returns emptyList()

        val existingMovedComic = ComicEntity(
            comicKey = "originalMovedKey",
            title = "Old Title",
            fileName = "old.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 80_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 15,
            localUri = "content://tree/old_folder/old.cbz",
            folderUri = "content://tree/old_folder",
            localSampleSha256 = "sampleHashABC",
            driveFileId = "drive123",
            driveMd5 = "md5xyz",
            coverCachePath = "/cache/covers/originalMovedKey.webp"
        )
        coEvery { comicDao.findByLocalSampleHash("sampleHashABC") } returns existingMovedComic

        val discovered = DiscoveredComicFile(
            uri = "content://tree/new_folder/renamed.cbz",
            folderUri = treeUri,
            fileName = "renamed.cbz",
            title = "Renamed",
            format = ComicFormat.CBZ,
            sizeBytes = 80_000L,
            lastModifiedEpochMillis = 2000L
        )
        coEvery { comicSource.listComicFiles(treeUri) } returns listOf(discovered)
        coEvery { comicSource.openRandomAccessSource(any()) } answers {
            mockk<RandomAccessSource>(relaxed = true)
        }
        coEvery { comicKeyHasher.computeLocalSampleHash(any()) } returns "sampleHashABC"

        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(15) { index ->
            ComicPage(index, "p$index.jpg", 100L, 200L)
        }
        coEvery { archiveReaderFactory.create(any(), any(), any()) } returns mockReader

        val result = repository.addFolderAndScan(treeUri)
        assertTrue(result.isSuccess)

        val slot = slot<ComicEntity>()
        coVerify { comicDao.upsertComic(capture(slot)) }
        assertEquals("originalMovedKey", slot.captured.comicKey)
        assertEquals("drive123", slot.captured.driveFileId)
        assertEquals("md5xyz", slot.captured.driveMd5)
        assertEquals("/cache/covers/originalMovedKey.webp", slot.captured.coverCachePath)
        assertEquals("content://tree/new_folder/renamed.cbz", slot.captured.localUri)
        assertEquals(treeUri, slot.captured.folderUri)
    }

    @Test
    fun scanCorruptAndEmptyArchivesAreSkippedWithoutAbortingScan() = runTest(testDispatcher) {
        val treeUri = "content://tree/comics"
        coEvery { comicSource.hasPermission(treeUri) } returns true
        coEvery { libraryFolderDao.getFolders() } returns emptyList()
        coEvery { comicSource.getFolderDisplayName(treeUri) } returns "Comics"
        coEvery { comicDao.getComicsByFolder(treeUri) } returns emptyList()

        val corruptFile = DiscoveredComicFile(
            uri = "content://tree/comics/corrupt.cbz",
            folderUri = treeUri,
            fileName = "corrupt.cbz",
            title = "Corrupt",
            format = ComicFormat.CBZ,
            sizeBytes = 5000L,
            lastModifiedEpochMillis = 1000L
        )
        val emptyFile = DiscoveredComicFile(
            uri = "content://tree/comics/empty.cbz",
            folderUri = treeUri,
            fileName = "empty.cbz",
            title = "Empty",
            format = ComicFormat.CBZ,
            sizeBytes = 1000L,
            lastModifiedEpochMillis = 1000L
        )
        val validFile = DiscoveredComicFile(
            uri = "content://tree/comics/valid.cbz",
            folderUri = treeUri,
            fileName = "valid.cbz",
            title = "Valid",
            format = ComicFormat.CBZ,
            sizeBytes = 50_000L,
            lastModifiedEpochMillis = 1000L
        )
        coEvery { comicSource.listComicFiles(treeUri) } returns listOf(corruptFile, emptyFile, validFile)
        coEvery { comicSource.openRandomAccessSource(any()) } answers {
            mockk<RandomAccessSource>(relaxed = true)
        }
        coEvery { comicKeyHasher.computeLocalSampleHash(any()) } returns "validHash"

        coEvery {
            archiveReaderFactory.create(any(), any(), "corrupt.cbz")
        } throws CorruptArchiveException("corrupt.cbz", "Bad Central Directory")

        coEvery {
            archiveReaderFactory.create(any(), any(), "empty.cbz")
        } throws EmptyArchiveException("empty.cbz")

        val validReader = mockk<ComicArchiveReader>(relaxed = true)
        every { validReader.getPages() } returns List(5) { ComicPage(it, "$it.png", 50L, 100L) }
        coEvery { archiveReaderFactory.create(any(), any(), "valid.cbz") } returns validReader
        coEvery { comicDao.findByLocalSampleHash("validHash") } returns null

        val result = repository.addFolderAndScan(treeUri)
        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()

        assertEquals(1, scanResult.addedOrUpdatedCount)
        assertEquals(2, scanResult.skippedCorruptCount)
        assertEquals(2, scanResult.errors.size)

        val err1 = scanResult.errors[0]
        assertTrue(err1 is ComicError.CorruptArchive)
        assertEquals("corrupt.cbz", (err1 as ComicError.CorruptArchive).fileName)

        val err2 = scanResult.errors[1]
        assertTrue(err2 is ComicError.EmptyArchive)
        assertEquals("empty.cbz", (err2 as ComicError.EmptyArchive).fileName)

        coVerify(exactly = 1) { comicDao.upsertComic(any()) }
    }

    @Test
    fun scanDeletedLocalComicsPrunesMissingComicsWithoutTouchingDriveComics() = runTest(testDispatcher) {
        val treeUri = "content://tree/comics"
        coEvery { comicSource.hasPermission(treeUri) } returns true
        coEvery { libraryFolderDao.getFolders() } returns emptyList()
        coEvery { comicSource.getFolderDisplayName(treeUri) } returns "Comics"

        val localDeleted = ComicEntity(
            comicKey = "deletedKey",
            title = "Deleted Local",
            fileName = "deleted.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 10,
            localUri = "content://tree/comics/deleted.cbz",
            folderUri = treeUri,
            localSampleSha256 = "deletedKey",
            driveFileId = null
        )
        val driveComic = ComicEntity(
            comicKey = "driveKey",
            title = "Drive Comic",
            fileName = "drive.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.GOOGLE_DRIVE,
            availability = ComicAvailability.REMOTE,
            sizeBytes = 20_000L,
            lastModifiedEpochMillis = 2000L,
            pageCount = 12,
            localUri = "content://tree/comics/drive.cbz",
            folderUri = treeUri,
            localSampleSha256 = "driveKey",
            driveFileId = "driveFile456"
        )
        val keptLocal = ComicEntity(
            comicKey = "keptKey",
            title = "Kept Local",
            fileName = "kept.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 30_000L,
            lastModifiedEpochMillis = 3000L,
            pageCount = 8,
            localUri = "content://tree/comics/kept.cbz",
            folderUri = treeUri,
            localSampleSha256 = "keptKey",
            driveFileId = null
        )

        coEvery { comicDao.getComicsByFolder(treeUri) } returns listOf(localDeleted, driveComic, keptLocal)

        val discovered = DiscoveredComicFile(
            uri = "content://tree/comics/kept.cbz",
            folderUri = treeUri,
            fileName = "kept.cbz",
            title = "Kept Local",
            format = ComicFormat.CBZ,
            sizeBytes = 30_000L,
            lastModifiedEpochMillis = 3000L
        )
        coEvery { comicSource.listComicFiles(treeUri) } returns listOf(discovered)

        val result = repository.addFolderAndScan(treeUri)
        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()

        assertEquals(1, scanResult.removedCount)
        coVerify(exactly = 1) { comicDao.deleteByKeys(listOf("deletedKey")) }
    }

    @Test
    fun scanRevokedPermissionReportsErrorAndDoesNotDeleteComics() = runTest(testDispatcher) {
        val treeUri = "content://tree/revoked"
        coEvery { comicSource.hasPermission(treeUri) } returns false

        val result = repository.addFolderAndScan(treeUri)
        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()

        assertEquals(0, scanResult.addedOrUpdatedCount)
        assertEquals(0, scanResult.removedCount)
        assertEquals(1, scanResult.errors.size)
        assertTrue(scanResult.errors[0] is ComicError.PermissionRevoked)
        assertEquals(treeUri, (scanResult.errors[0] as ComicError.PermissionRevoked).uri)

        coVerify(exactly = 0) { comicDao.deleteByKeys(any()) }
        coVerify(exactly = 0) { comicDao.upsertComic(any()) }
    }

    @Test
    fun rescanAllFoldersScansEachFolderAndAggregatesResults() = runTest(testDispatcher) {
        val folder1 = LibraryFolderEntity("content://tree/f1", "Folder 1", 1000L, null)
        val folder2 = LibraryFolderEntity("content://tree/f2", "Folder 2", 2000L, null)
        coEvery { libraryFolderDao.getFolders() } returns listOf(folder1, folder2)

        coEvery { comicSource.hasPermission("content://tree/f1") } returns true
        coEvery { comicDao.getComicsByFolder("content://tree/f1") } returns emptyList()
        coEvery { comicSource.listComicFiles("content://tree/f1") } returns emptyList()

        coEvery { comicSource.hasPermission("content://tree/f2") } returns false

        val result = repository.rescanAllFolders()
        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()

        assertEquals(0, scanResult.addedOrUpdatedCount)
        assertEquals(0, scanResult.removedCount)
        assertEquals(1, scanResult.errors.size)
        assertTrue(scanResult.errors[0] is ComicError.PermissionRevoked)
    }

    @Test
    fun removeFolderDeletesComicsAndFolder() = runTest(testDispatcher) {
        val folderUri = "content://tree/remove_me"
        repository.removeFolder(folderUri)

        coVerify(exactly = 1) { comicDao.deleteLocalComicsInFolder(folderUri) }
        coVerify(exactly = 1) { libraryFolderDao.deleteFolder(folderUri) }
    }

    @Test
    fun observeLibrariesSeedsDefaultsWhenDatabaseIsEmpty() = runTest(testDispatcher) {
        val emptyFlow = MutableStateFlow<List<LibraryEntity>>(emptyList())
        every { libraryDao.observeLibraries() } returns emptyFlow
        coEvery { libraryDao.getLibraries() } returns emptyList() andThen listOf(
            LibraryEntity("comics", "Comics", ReadingDirection.LEFT_TO_RIGHT, 1000L, true),
            LibraryEntity("manga", "Manga", ReadingDirection.RIGHT_TO_LEFT, 1001L, true)
        )
        coEvery { libraryDao.upsertLibraries(any()) } returns Unit

        val emitted = repository.observeLibraries().first()
        assertEquals(2, emitted.size)
        assertEquals("comics", emitted[0].libraryId)
        assertEquals("manga", emitted[1].libraryId)
        coVerify(exactly = 1) { libraryDao.upsertLibraries(any()) }
    }

    @Test
    fun getLibraryReturnsDomainLibrary() = runTest(testDispatcher) {
        coEvery { libraryDao.getLibraries() } returns listOf(
            LibraryEntity("comics", "Comics", ReadingDirection.LEFT_TO_RIGHT, 1000L, true)
        )
        coEvery { libraryDao.getLibrary("comics") } returns LibraryEntity(
            "comics", "Comics", ReadingDirection.LEFT_TO_RIGHT, 1000L, true
        )

        val lib = repository.getLibrary("comics")
        assertNotNull(lib)
        assertEquals("comics", lib?.libraryId)
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, lib?.defaultReadingDirection)
    }

    @Test
    fun createLibraryTrimsNameAndGeneratesId() = runTest(testDispatcher) {
        coEvery { libraryDao.upsertLibrary(any()) } returns Unit

        val result = repository.createLibrary("  Webtoons  ", ReadingDirection.LEFT_TO_RIGHT)
        assertTrue(result.isSuccess)
        val lib = result.getOrThrow()
        assertEquals("Webtoons", lib.name)
        assertTrue(lib.libraryId.startsWith("webtoons_"))
        assertEquals(ReadingDirection.LEFT_TO_RIGHT, lib.defaultReadingDirection)
        assertFalse(lib.isBuiltIn)

        val blankResult = repository.createLibrary("   ", ReadingDirection.LEFT_TO_RIGHT)
        assertTrue(blankResult.isFailure)
    }

    @Test
    fun updateLibraryUpdatesExistingLibrary() = runTest(testDispatcher) {
        coEvery { libraryDao.getLibrary("custom_1") } returns LibraryEntity(
            "custom_1", "Old Name", ReadingDirection.LEFT_TO_RIGHT, 1000L, false
        )
        coEvery { libraryDao.upsertLibrary(any()) } returns Unit

        val result = repository.updateLibrary("custom_1", "New Name", ReadingDirection.RIGHT_TO_LEFT)
        assertTrue(result.isSuccess)
        coVerify {
            libraryDao.upsertLibrary(match {
                it.libraryId == "custom_1" && it.name == "New Name" && it.defaultReadingDirection == ReadingDirection.RIGHT_TO_LEFT
            })
        }
    }

    @Test
    fun deleteLibraryRejectsLastRemainingLibraryAndDeletesWhenMultiple() = runTest(testDispatcher) {
        coEvery { libraryDao.getLibraries() } returns listOf(
            LibraryEntity("comics", "Comics", ReadingDirection.LEFT_TO_RIGHT, 1000L, true)
        )
        val singleLibResult = repository.deleteLibrary("comics")
        assertTrue(singleLibResult.isFailure)

        coEvery { libraryDao.getLibraries() } returns listOf(
            LibraryEntity("comics", "Comics", ReadingDirection.LEFT_TO_RIGHT, 1000L, true),
            LibraryEntity("manga", "Manga", ReadingDirection.RIGHT_TO_LEFT, 1001L, true)
        )
        coEvery { comicDao.deleteComicsByLibrary("manga") } returns Unit
        coEvery { libraryFolderDao.deleteFoldersByLibrary("manga") } returns Unit
        coEvery { driveFolderDao.deleteFoldersByLibrary("manga") } returns Unit
        coEvery { libraryDao.deleteLibrary("manga") } returns Unit

        val deleteResult = repository.deleteLibrary("manga")
        assertTrue(deleteResult.isSuccess)
        coVerify { comicDao.deleteComicsByLibrary("manga") }
        coVerify { libraryFolderDao.deleteFoldersByLibrary("manga") }
        coVerify { driveFolderDao.deleteFoldersByLibrary("manga") }
        coVerify { libraryDao.deleteLibrary("manga") }
    }

    @Test
    fun updateComicMetadataTrimsValuesConvertsBlankToNullAndUpdatesTitleWhenNonBlank() = runTest(testDispatcher) {
        val existing = ComicEntity(
            comicKey = "c1",
            title = "Original Title",
            fileName = "orig.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 1000L,
            lastModifiedEpochMillis = 5000L,
            pageCount = 20,
            localUri = "content://comix/orig.cbz",
            folderUri = "content://comix/folder",
            localSampleSha256 = null,
            driveFileId = null,
            driveMd5 = null,
            coverCachePath = null,
            metaSeries = "Old Series",
            metaTitle = "Old Meta Title",
            metaNumber = "1",
            metaReleaseYear = 2020,
            metaWriter = "Old Writer",
            metaPenciller = "Old Penciller",
            metaPublisher = "Old Publisher",
            metaSummary = "Old Summary"
        )
        coEvery { comicDao.getComicByKey("c1") } returns existing
        val captured = slot<ComicEntity>()
        coEvery { comicDao.upsertComic(capture(captured)) } returns Unit

        val update = SingleComicMetadataUpdate(
            comicKey = "c1",
            series = "  New Series  ",
            title = "  New Title  ",
            number = "  2  ",
            releaseYear = 2024,
            writer = "   ",
            penciller = "  New Penciller  ",
            publisher = null,
            summary = "  New Summary  "
        )

        val result = repository.updateComicMetadata(update)
        assertTrue(result.isSuccess)
        val saved = captured.captured
        assertEquals("New Title", saved.title)
        assertEquals("New Title", saved.metaTitle)
        assertEquals("New Series", saved.metaSeries)
        assertEquals("2", saved.metaNumber)
        assertEquals(2024, saved.metaReleaseYear)
        assertNull(saved.metaWriter)
        assertEquals("New Penciller", saved.metaPenciller)
        assertNull(saved.metaPublisher)
        assertEquals("New Summary", saved.metaSummary)
    }

    @Test
    fun updateComicMetadataKeepsOriginalTitleWhenUpdateTitleIsBlankOrNull() = runTest(testDispatcher) {
        val existing = ComicEntity(
            comicKey = "c1",
            title = "Keep Me",
            fileName = "keep.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 1000L,
            lastModifiedEpochMillis = 5000L,
            pageCount = 20,
            localUri = "content://comix/keep.cbz",
            folderUri = "content://comix/folder",
            localSampleSha256 = null,
            driveFileId = null,
            driveMd5 = null,
            coverCachePath = null
        )
        coEvery { comicDao.getComicByKey("c1") } returns existing
        val captured = slot<ComicEntity>()
        coEvery { comicDao.upsertComic(capture(captured)) } returns Unit

        val update = SingleComicMetadataUpdate(
            comicKey = "c1",
            series = "Series",
            title = "   ",
            number = null,
            releaseYear = null,
            writer = null,
            penciller = null,
            publisher = null,
            summary = null
        )

        val result = repository.updateComicMetadata(update)
        assertTrue(result.isSuccess)
        assertEquals("Keep Me", captured.captured.title)
        assertNull(captured.captured.metaTitle)
    }

    @Test
    fun updateComicMetadataReturnsFailureWhenComicNotFound() = runTest(testDispatcher) {
        coEvery { comicDao.getComicByKey("missing") } returns null

        val update = SingleComicMetadataUpdate(
            comicKey = "missing",
            series = null,
            title = null,
            number = null,
            releaseYear = null,
            writer = null,
            penciller = null,
            publisher = null,
            summary = null
        )

        val result = repository.updateComicMetadata(update)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun bulkUpdateComicMetadataAppliesOnlyFieldsWithUpdateFlagTrueAcrossComics() = runTest(testDispatcher) {
        val comic1 = ComicEntity(
            comicKey = "c1",
            title = "Issue 1",
            fileName = "c1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 1000L,
            lastModifiedEpochMillis = 5000L,
            pageCount = 20,
            localUri = "content://comix/c1.cbz",
            folderUri = "content://comix/folder",
            localSampleSha256 = null,
            driveFileId = null,
            driveMd5 = null,
            coverCachePath = null,
            metaSeries = "Old Series 1",
            metaReleaseYear = 2010,
            metaWriter = "Original Writer 1",
            metaPenciller = "Artist 1",
            metaPublisher = "Publisher 1"
        )
        val comic2 = ComicEntity(
            comicKey = "c2",
            title = "Issue 2",
            fileName = "c2.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 1000L,
            lastModifiedEpochMillis = 5000L,
            pageCount = 20,
            localUri = "content://comix/c2.cbz",
            folderUri = "content://comix/folder",
            localSampleSha256 = null,
            driveFileId = null,
            driveMd5 = null,
            coverCachePath = null,
            metaSeries = "Old Series 2",
            metaReleaseYear = 2011,
            metaWriter = "Original Writer 2",
            metaPenciller = "Artist 2",
            metaPublisher = "Publisher 2"
        )

        coEvery { comicDao.getComicsByKeys(listOf("c1", "c2")) } returns listOf(comic1, comic2)
        val captured = slot<List<ComicEntity>>()
        coEvery { comicDao.upsertComics(capture(captured)) } returns Unit

        val bulkUpdate = BulkComicMetadataUpdate(
            comicKeys = listOf("c1", "c2"),
            series = "Ignored Series",
            updateSeries = false,
            releaseYear = 2099,
            updateReleaseYear = false,
            writer = "  Shared Writer  ",
            updateWriter = true,
            penciller = "Ignored Penciller",
            updatePenciller = false,
            publisher = "  Shared Publisher  ",
            updatePublisher = true
        )

        val result = repository.bulkUpdateComicMetadata(bulkUpdate)
        assertTrue(result.isSuccess)
        val saved = captured.captured
        assertEquals(2, saved.size)

        val saved1 = saved.first { it.comicKey == "c1" }
        assertEquals("Old Series 1", saved1.metaSeries)
        assertEquals(2010, saved1.metaReleaseYear)
        assertEquals("Shared Writer", saved1.metaWriter)
        assertEquals("Artist 1", saved1.metaPenciller)
        assertEquals("Shared Publisher", saved1.metaPublisher)

        val saved2 = saved.first { it.comicKey == "c2" }
        assertEquals("Old Series 2", saved2.metaSeries)
        assertEquals(2011, saved2.metaReleaseYear)
        assertEquals("Shared Writer", saved2.metaWriter)
        assertEquals("Artist 2", saved2.metaPenciller)
        assertEquals("Shared Publisher", saved2.metaPublisher)
    }
}
