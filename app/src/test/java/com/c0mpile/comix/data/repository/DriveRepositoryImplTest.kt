package com.c0mpile.comix.data.repository

import android.app.Activity
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.DriveAccountDao
import com.c0mpile.comix.data.local.dao.DriveFolderDao
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.DriveAccountEntity
import com.c0mpile.comix.data.local.entity.DriveFolderEntity
import com.c0mpile.comix.data.remote.drive.DriveApiClient
import com.c0mpile.comix.data.remote.drive.DriveApiException
import com.c0mpile.comix.data.remote.drive.DriveAuthorizer
import com.c0mpile.comix.data.remote.drive.DriveChange
import com.c0mpile.comix.data.remote.drive.DriveChangeList
import com.c0mpile.comix.data.remote.drive.DriveFile
import com.c0mpile.comix.data.remote.drive.DriveFileList
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_FOLDER
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_SHORTCUT
import com.c0mpile.comix.data.remote.drive.ShortcutDetails
import com.c0mpile.comix.data.source.DriveSource
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveBrowseItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DriveRepositoryImpl] validating account authentication flows,
 * folder browsing, comic indexing, and incremental change synchronization.
 */
class DriveRepositoryImplTest {

    private val authorizer = mockk<DriveAuthorizer>()
    private val apiClient = mockk<DriveApiClient>()
    private val driveSource = mockk<DriveSource>()
    private val driveAccountDao = mockk<DriveAccountDao>()
    private val driveFolderDao = mockk<DriveFolderDao>()
    private val comicDao = mockk<ComicDao>()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: DriveRepositoryImpl

    @Before
    fun setUp() {
        repository = DriveRepositoryImpl(
            authorizer = authorizer,
            apiClient = apiClient,
            driveSource = driveSource,
            driveAccountDao = driveAccountDao,
            driveFolderDao = driveFolderDao,
            comicDao = comicDao,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun signInSuccessPersistsDriveAccountEntity() = runTest(testDispatcher) {
        val activity = mockk<Activity>()
        val account = DriveAccount(email = "reader@example.com", signedInAtEpochMillis = 1700000000000L)
        coEvery { authorizer.signIn(activity) } returns Result.success(account)
        coEvery { driveAccountDao.upsertAccount(any()) } returns Unit

        val result = repository.signIn(activity)

        assertTrue(result.isSuccess)
        val slot = slot<DriveAccountEntity>()
        coVerify { driveAccountDao.upsertAccount(capture(slot)) }
        assertEquals("reader@example.com", slot.captured.email)
        assertEquals(1700000000000L, slot.captured.signedInAtEpochMillis)
    }

    @Test
    fun signOutClearsAuthorizerComicsFoldersAndAccount() = runTest(testDispatcher) {
        coEvery { authorizer.signOut() } returns Unit
        coEvery { comicDao.deleteAllDriveComics() } returns Unit
        coEvery { driveFolderDao.deleteAllFolders() } returns Unit
        coEvery { driveAccountDao.clearAccount() } returns Unit

        repository.signOut()

        coVerifyOrder {
            authorizer.signOut()
            comicDao.deleteAllDriveComics()
            driveFolderDao.deleteAllFolders()
            driveAccountDao.clearAccount()
        }
    }

    @Test
    fun browseSubfoldersMapsDirectFoldersAndFolderShortcuts() = runTest(testDispatcher) {
        val directFolder = DriveFile(
            id = "f1",
            name = "Comics",
            mimeType = MIME_TYPE_FOLDER,
            driveId = "shared-drive-1"
        )
        val folderShortcut = DriveFile(
            id = "s1",
            name = "Manga Shortcut",
            mimeType = MIME_TYPE_SHORTCUT,
            shortcutDetails = ShortcutDetails(
                targetId = "target-f2",
                targetMimeType = MIME_TYPE_FOLDER
            ),
            driveId = null
        )
        val fileShortcut = DriveFile(
            id = "s2",
            name = "File Shortcut",
            mimeType = MIME_TYPE_SHORTCUT,
            shortcutDetails = ShortcutDetails(
                targetId = "target-file",
                targetMimeType = "application/pdf"
            )
        )
        val trashedFolder = DriveFile(
            id = "f3",
            name = "Old",
            mimeType = MIME_TYPE_FOLDER,
            trashed = true
        )

        coEvery { apiClient.listSubfolders("parent-f1", null) } returns DriveFileList(
            files = listOf(directFolder, folderShortcut, fileShortcut, trashedFolder),
            nextPageToken = null
        )

        val result = repository.browseSubfolders("parent-f1")

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(2, items.size)
        assertEquals(
            DriveBrowseItem(id = "f1", name = "Comics", driveId = "shared-drive-1", isShortcut = false),
            items[0]
        )
        assertEquals(
            DriveBrowseItem(id = "target-f2", name = "Manga Shortcut", driveId = null, isShortcut = true),
            items[1]
        )
    }

    @Test
    fun addDriveFolderInitializesStartTokenIfMissingAndRescans() = runTest(testDispatcher) {
        coEvery { driveFolderDao.upsertFolder(any()) } returns Unit
        coEvery { driveAccountDao.getAccount() } returns DriveAccountEntity(
            email = "reader@example.com",
            signedInAtEpochMillis = 1000L,
            changesPageToken = null
        )
        coEvery { apiClient.getStartPageToken() } returns "start-token-1"
        coEvery { driveAccountDao.updateChangesPageToken("start-token-1") } returns Unit
        coEvery { driveSource.scanDriveFiles("folder-1") } returns emptyList()
        coEvery { comicDao.getComicsByDriveFolder("folder-1") } returns emptyList()
        coEvery { driveFolderDao.updateLastScanned("folder-1", any()) } returns Unit

        val result = repository.addDriveFolder("folder-1", "My Comics", "shared-drive-id")

        assertTrue(result.isSuccess)
        coVerify {
            driveFolderDao.upsertFolder(
                match { it.folderId == "folder-1" && it.displayName == "My Comics" && it.driveId == "shared-drive-id" }
            )
        }
        coVerify { driveAccountDao.updateChangesPageToken("start-token-1") }
        coVerify { driveSource.scanDriveFiles("folder-1") }
    }

    @Test
    fun rescanFolderIndexesCbzFilesWithMd5KeyAndRemovesStaleComics() = runTest(testDispatcher) {
        val file1 = DriveFile(
            id = "df1",
            name = "Spider-Man 01.cbz",
            mimeType = "application/vnd.comicbook+zip",
            md5Checksum = "ABC123DEF456",
            size = 1000L,
            modifiedTime = "2024-01-01T00:00:00Z"
        )
        val file2 = DriveFile(
            id = "df2",
            name = "Spider-Man 02.cbz",
            mimeType = "application/vnd.comicbook+zip",
            md5Checksum = "FEDCBA654321",
            size = 2000L,
            modifiedTime = "2024-01-02T00:00:00Z"
        )

        val existingComic = ComicEntity(
            comicKey = "md5:abc123def456",
            title = "Spider-Man 01",
            fileName = "Spider-Man 01.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.GOOGLE_DRIVE,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 1000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 32,
            localUri = "content://cache/file1.cbz",
            folderUri = "folder-1",
            localSampleSha256 = null,
            driveFileId = "df1",
            driveMd5 = "abc123def456"
        )
        val staleComic = ComicEntity(
            comicKey = "md5:stalecomic999",
            title = "Stale Comic",
            fileName = "Stale.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.GOOGLE_DRIVE,
            availability = ComicAvailability.REMOTE,
            sizeBytes = 500L,
            lastModifiedEpochMillis = 500L,
            pageCount = 10,
            localUri = null,
            folderUri = "folder-1",
            localSampleSha256 = null,
            driveFileId = "df-stale",
            driveMd5 = "stalecomic999"
        )

        coEvery { driveSource.scanDriveFiles("folder-1") } returns listOf(file1, file2)
        coEvery { comicDao.getComicsByDriveFolder("folder-1") } returns listOf(existingComic, staleComic)
        coEvery { comicDao.deleteByKeys(any()) } returns Unit
        coEvery { comicDao.upsertComics(any()) } returns Unit
        coEvery { driveFolderDao.updateLastScanned("folder-1", any()) } returns Unit

        val result = repository.rescanFolder("folder-1")

        assertTrue(result.isSuccess)
        val scanResult = result.getOrThrow()
        assertEquals(2, scanResult.addedOrUpdatedCount)
        assertEquals(1, scanResult.removedCount)

        coVerify { comicDao.deleteByKeys(listOf("md5:stalecomic999")) }

        val upsertSlot = slot<List<ComicEntity>>()
        coVerify { comicDao.upsertComics(capture(upsertSlot)) }
        val upserted = upsertSlot.captured
        assertEquals(2, upserted.size)

        val updatedComic = upserted.first { it.comicKey == "md5:abc123def456" }
        assertEquals("Spider-Man 01", updatedComic.title)
        assertEquals("Spider-Man 01.cbz", updatedComic.fileName)
        assertEquals(ComicSourceType.GOOGLE_DRIVE, updatedComic.sourceType)
        assertEquals(ComicAvailability.LOCAL, updatedComic.availability)
        assertEquals(32, updatedComic.pageCount)
        assertEquals("content://cache/file1.cbz", updatedComic.localUri)
        assertEquals("abc123def456", updatedComic.driveMd5)

        val newComic = upserted.first { it.comicKey == "md5:fedcba654321" }
        assertEquals("Spider-Man 02", newComic.title)
        assertEquals(ComicAvailability.REMOTE, newComic.availability)
        assertEquals(0, newComic.pageCount)
        assertNull(newComic.localUri)
        assertEquals("fedcba654321", newComic.driveMd5)

        coVerify { driveFolderDao.updateLastScanned("folder-1", any()) }
    }

    @Test
    fun incrementalSyncDeletesRemovedComicsAndUpdatesPageToken() = runTest(testDispatcher) {
        val registeredFolder = DriveFolderEntity(
            folderId = "f1",
            displayName = "Comics",
            addedAtEpochMillis = 1000L,
            lastScannedAtEpochMillis = 2000L
        )
        coEvery { driveFolderDao.getAllFolders() } returns listOf(registeredFolder)
        coEvery { driveAccountDao.getAccount() } returns DriveAccountEntity(
            email = "reader@example.com",
            signedInAtEpochMillis = 1000L,
            changesPageToken = "token-1"
        )

        val changes = listOf(
            DriveChange(fileId = "df-deleted", removed = true),
            DriveChange(
                fileId = "df-doc",
                removed = false,
                file = DriveFile(id = "df-doc", name = "readme.txt", mimeType = "text/plain")
            )
        )
        coEvery { apiClient.listChanges("token-1") } returns DriveChangeList(
            changes = changes,
            nextPageToken = null,
            newStartPageToken = "token-2"
        )
        coEvery { comicDao.deleteByDriveFileIds(listOf("df-deleted")) } returns Unit
        coEvery { driveAccountDao.updateChangesPageToken("token-2") } returns Unit

        val result = repository.incrementalSync()

        assertTrue(result.isSuccess)
        val scan = result.getOrThrow()
        assertEquals(0, scan.addedOrUpdatedCount)
        assertEquals(1, scan.removedCount)

        coVerify { comicDao.deleteByDriveFileIds(listOf("df-deleted")) }
        coVerify { driveAccountDao.updateChangesPageToken("token-2") }
        coVerify(exactly = 0) { driveSource.scanDriveFiles(any()) }
    }

    @Test
    fun incrementalSyncWithRelevantCbzChangeTriggersFolderRescan() = runTest(testDispatcher) {
        val registeredFolder = DriveFolderEntity(
            folderId = "f1",
            displayName = "Comics",
            addedAtEpochMillis = 1000L,
            lastScannedAtEpochMillis = 2000L
        )
        coEvery { driveFolderDao.getAllFolders() } returns listOf(registeredFolder)
        coEvery { driveAccountDao.getAccount() } returns DriveAccountEntity(
            email = "reader@example.com",
            signedInAtEpochMillis = 1000L,
            changesPageToken = "token-1"
        )

        val changedCbz = DriveFile(
            id = "df-new",
            name = "Batman 500.cbz",
            mimeType = "application/vnd.comicbook+zip",
            md5Checksum = "newmd5checksum",
            size = 3000L
        )
        coEvery { apiClient.listChanges("token-1") } returns DriveChangeList(
            changes = listOf(DriveChange(fileId = "df-new", removed = false, file = changedCbz)),
            nextPageToken = null,
            newStartPageToken = "token-2"
        )

        coEvery { driveSource.scanDriveFiles("f1") } returns listOf(changedCbz)
        coEvery { comicDao.getComicsByDriveFolder("f1") } returns emptyList()
        coEvery { comicDao.deleteByKeys(any()) } returns Unit
        coEvery { comicDao.upsertComics(any()) } returns Unit
        coEvery { driveFolderDao.updateLastScanned("f1", any()) } returns Unit
        coEvery { driveAccountDao.updateChangesPageToken("token-2") } returns Unit

        val result = repository.incrementalSync()

        assertTrue(result.isSuccess)
        val scan = result.getOrThrow()
        assertEquals(1, scan.addedOrUpdatedCount)
        assertEquals(0, scan.removedCount)

        coVerify { driveSource.scanDriveFiles("f1") }
        coVerify { driveAccountDao.updateChangesPageToken("token-2") }
    }

    @Test
    fun incrementalSyncExpiredToken410FallsBackToFullRescan() = runTest(testDispatcher) {
        val registeredFolder = DriveFolderEntity(
            folderId = "f1",
            displayName = "Comics",
            addedAtEpochMillis = 1000L,
            lastScannedAtEpochMillis = 2000L
        )
        coEvery { driveFolderDao.getAllFolders() } returns listOf(registeredFolder)
        coEvery { driveAccountDao.getAccount() } returns DriveAccountEntity(
            email = "reader@example.com",
            signedInAtEpochMillis = 1000L,
            changesPageToken = "token-expired"
        )

        coEvery { apiClient.listChanges("token-expired") } throws DriveApiException(
            ComicError.DriveApiError(410, "Page token expired")
        )
        coEvery { apiClient.getStartPageToken() } returns "fresh-start-token"
        coEvery { driveSource.scanDriveFiles("f1") } returns emptyList()
        coEvery { comicDao.getComicsByDriveFolder("f1") } returns emptyList()
        coEvery { driveFolderDao.updateLastScanned("f1", any()) } returns Unit
        coEvery { driveAccountDao.updateChangesPageToken("fresh-start-token") } returns Unit

        val result = repository.incrementalSync()

        assertTrue(result.isSuccess)
        coVerify { apiClient.getStartPageToken() }
        coVerify { driveAccountDao.updateChangesPageToken("fresh-start-token") }
        coVerify { driveSource.scanDriveFiles("f1") }
    }

    @Test
    fun incrementalSyncWithNoRegisteredFoldersReturnsZeroResult() = runTest(testDispatcher) {
        coEvery { driveFolderDao.getAllFolders() } returns emptyList()

        val result = repository.incrementalSync()

        assertTrue(result.isSuccess)
        val scan = result.getOrThrow()
        assertEquals(0, scan.addedOrUpdatedCount)
        assertEquals(0, scan.removedCount)
        coVerify(exactly = 0) { apiClient.listChanges(any()) }
    }
}
