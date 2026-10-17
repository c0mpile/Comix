package com.c0mpile.comix.data.source

import com.c0mpile.comix.data.remote.drive.DriveApiClient
import com.c0mpile.comix.data.remote.drive.DriveAuthorizer
import com.c0mpile.comix.data.remote.drive.DriveFile
import com.c0mpile.comix.data.remote.drive.DriveFileList
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_FOLDER
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_SHORTCUT
import com.c0mpile.comix.data.remote.drive.ShortcutDetails
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DriveSource] validating recursive BFS scanning, shortcut resolution,
 * cycle detection, format filtering, and metadata extraction.
 */
class DriveSourceTest {

    private val apiClient = mockk<DriveApiClient>()
    private val authorizer = mockk<DriveAuthorizer>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var driveSource: DriveSource

    @Before
    fun setUp() {
        driveSource = DriveSource(
            apiClient = apiClient,
            authorizer = authorizer,
            ioDispatcher = testDispatcher
        )
    }

    private fun createDriveFile(
        id: String,
        name: String,
        mimeType: String = "application/vnd.comicbook+zip",
        md5Checksum: String? = "1234567890abcdef",
        size: Long? = 5000L,
        modifiedTime: String? = "2024-01-01T00:00:00Z",
        shortcutDetails: ShortcutDetails? = null,
        trashed: Boolean = false
    ) = DriveFile(
        id = id,
        name = name,
        mimeType = mimeType,
        md5Checksum = md5Checksum,
        size = size,
        modifiedTime = modifiedTime,
        shortcutDetails = shortcutDetails,
        trashed = trashed
    )

    @Test
    fun recursiveBfsScanAcrossRootSubfolderAndPaginatedResponses() = runTest(testDispatcher) {
        val subfolder = createDriveFile(
            id = "subfolder-1",
            name = "Subfolder",
            mimeType = MIME_TYPE_FOLDER,
            md5Checksum = null
        )
        val comic1 = createDriveFile(id = "c1", name = "Comic 1.cbz", md5Checksum = "md5-1")
        val comic2 = createDriveFile(id = "c2", name = "Comic 2.cbz", md5Checksum = "md5-2")

        coEvery { apiClient.listChildren("root-id", null) } returns DriveFileList(
            files = listOf(subfolder),
            nextPageToken = "page-2"
        )
        coEvery { apiClient.listChildren("root-id", "page-2") } returns DriveFileList(
            files = listOf(comic1),
            nextPageToken = null
        )
        coEvery { apiClient.listChildren("subfolder-1", null) } returns DriveFileList(
            files = listOf(comic2),
            nextPageToken = null
        )

        val discovered = driveSource.scanDriveFiles("root-id")

        assertEquals(2, discovered.size)
        assertEquals(listOf("c1", "c2"), discovered.map { it.id })
    }

    @Test
    fun folderShortcutFollowedToTargetId() = runTest(testDispatcher) {
        val folderShortcut = createDriveFile(
            id = "shortcut-folder",
            name = "Manga Shortcut",
            mimeType = MIME_TYPE_SHORTCUT,
            md5Checksum = null,
            shortcutDetails = ShortcutDetails(
                targetId = "target-folder-id",
                targetMimeType = MIME_TYPE_FOLDER
            )
        )
        val comic = createDriveFile(id = "c-target", name = "One Piece.cbz", md5Checksum = "md5-target")

        coEvery { apiClient.listChildren("root-id", null) } returns DriveFileList(
            files = listOf(folderShortcut),
            nextPageToken = null
        )
        coEvery { apiClient.listChildren("target-folder-id", null) } returns DriveFileList(
            files = listOf(comic),
            nextPageToken = null
        )

        val discovered = driveSource.scanDriveFiles("root-id")

        assertEquals(1, discovered.size)
        assertEquals("c-target", discovered[0].id)
        assertEquals("One Piece.cbz", discovered[0].name)
    }

    @Test
    fun fileShortcutResolvesViaGetFileAndIsIncluded() = runTest(testDispatcher) {
        val fileShortcut = createDriveFile(
            id = "shortcut-file",
            name = "Spidey Shortcut",
            mimeType = MIME_TYPE_SHORTCUT,
            md5Checksum = null,
            shortcutDetails = ShortcutDetails(
                targetId = "target-file-id",
                targetMimeType = "application/vnd.comicbook+zip"
            )
        )
        val resolvedTarget = createDriveFile(
            id = "target-file-id",
            name = "Spider-Man 01.cbz",
            md5Checksum = "md5-resolved"
        )

        coEvery { apiClient.listChildren("root-id", null) } returns DriveFileList(
            files = listOf(fileShortcut),
            nextPageToken = null
        )
        coEvery { apiClient.getFile("target-file-id") } returns resolvedTarget

        val discovered = driveSource.scanDriveFiles("root-id")

        assertEquals(1, discovered.size)
        assertEquals("target-file-id", discovered[0].id)
        assertEquals("Spider-Man 01.cbz", discovered[0].name)
    }

    @Test
    fun cycleDetectionTwoFoldersWithShortcutsPointingToEachOtherTerminatesCleanly() = runTest(testDispatcher) {
        val shortcutToB = createDriveFile(
            id = "s-b",
            name = "To B",
            mimeType = MIME_TYPE_SHORTCUT,
            md5Checksum = null,
            shortcutDetails = ShortcutDetails(
                targetId = "folder-b",
                targetMimeType = MIME_TYPE_FOLDER
            )
        )
        val shortcutToA = createDriveFile(
            id = "s-a",
            name = "To A",
            mimeType = MIME_TYPE_SHORTCUT,
            md5Checksum = null,
            shortcutDetails = ShortcutDetails(
                targetId = "folder-a",
                targetMimeType = MIME_TYPE_FOLDER
            )
        )
        val comicB = createDriveFile(id = "c-b", name = "Comic B.cbz", md5Checksum = "md5-b")

        coEvery { apiClient.listChildren("folder-a", null) } returns DriveFileList(
            files = listOf(shortcutToB),
            nextPageToken = null
        )
        coEvery { apiClient.listChildren("folder-b", null) } returns DriveFileList(
            files = listOf(shortcutToA, comicB),
            nextPageToken = null
        )

        val discovered = driveSource.scanDriveFiles("folder-a")

        assertEquals(1, discovered.size)
        assertEquals("c-b", discovered[0].id)
    }

    @Test
    fun nonCbzDotFilesTrashedAndMissingMd5FilteredOut() = runTest(testDispatcher) {
        val validCbz = createDriveFile("1", "Valid.cbz", md5Checksum = "md5-1")
        val validUpperCase = createDriveFile("2", "Valid.CBZ", md5Checksum = "md5-2")
        val imageFile = createDriveFile("3", "Cover.png", md5Checksum = "md5-3")
        val dotFile = createDriveFile("4", ".Hidden.cbz", md5Checksum = "md5-4")
        val trashedCbz = createDriveFile("5", "Trashed.cbz", md5Checksum = "md5-5", trashed = true)
        val noMd5Cbz = createDriveFile("6", "NoMd5.cbz", md5Checksum = null)
        val emptyMd5Cbz = createDriveFile("7", "EmptyMd5.cbz", md5Checksum = "")

        coEvery { apiClient.listChildren("root-id", null) } returns DriveFileList(
            files = listOf(validCbz, validUpperCase, imageFile, dotFile, trashedCbz, noMd5Cbz, emptyMd5Cbz),
            nextPageToken = null
        )

        val discovered = driveSource.scanDriveFiles("root-id")

        assertEquals(2, discovered.size)
        assertEquals(listOf("1", "2"), discovered.map { it.id })
    }

    @Test
    fun openRandomAccessSourceThrowsUnsupportedOperationException() = runTest(testDispatcher) {
        val thrown = runCatching {
            driveSource.openRandomAccessSource("any-uri")
        }.exceptionOrNull()
        assertTrue(thrown is UnsupportedOperationException)
    }

    @Test
    fun hasPermissionReflectsAccessTokenStatus() = runTest(testDispatcher) {
        coEvery { authorizer.getAccessToken(forceRefresh = false) } returns Result.success("fake-token-1")
        assertTrue(driveSource.hasPermission("folder-id"))

        coEvery { authorizer.getAccessToken(forceRefresh = false) } returns Result.failure(Exception("Not authorized"))
        assertFalse(driveSource.hasPermission("folder-id"))
    }

    @Test
    fun getFolderDisplayNameResolvesSpecialRootAndApiTitlesWithFallback() = runTest(testDispatcher) {
        assertEquals("My Drive", driveSource.getFolderDisplayName(DriveSource.ROOT_FOLDER_ID))

        val folderFile = createDriveFile("custom-folder-id", "Custom Collection", mimeType = MIME_TYPE_FOLDER)
        coEvery { apiClient.getFile("custom-folder-id") } returns folderFile
        assertEquals("Custom Collection", driveSource.getFolderDisplayName("custom-folder-id"))

        coEvery { apiClient.getFile("error-folder-id") } throws RuntimeException("Network error")
        assertEquals("error-folder-id", driveSource.getFolderDisplayName("error-folder-id"))
    }

    @Test
    fun listComicFilesMapsDriveFilesToDiscoveredComicFiles() = runTest(testDispatcher) {
        val comic = createDriveFile(
            id = "file-101",
            name = "Batman Year One.cbz",
            md5Checksum = "batman-md5",
            size = 2048000L,
            modifiedTime = "2024-01-01T00:00:00Z"
        )
        coEvery { apiClient.listChildren("folder-id", null) } returns DriveFileList(
            files = listOf(comic),
            nextPageToken = null
        )

        val discoveredList = driveSource.listComicFiles("folder-id")

        assertEquals(1, discoveredList.size)
        val discovered = discoveredList[0]
        assertEquals("file-101", discovered.uri)
        assertEquals("folder-id", discovered.folderUri)
        assertEquals("Batman Year One.cbz", discovered.fileName)
        assertEquals("Batman Year One", discovered.title)
        assertEquals(ComicFormat.CBZ, discovered.format)
        assertEquals(2048000L, discovered.sizeBytes)
        assertEquals(1704067200000L, discovered.lastModifiedEpochMillis)
        assertEquals(ComicSourceType.GOOGLE_DRIVE, driveSource.sourceType)
    }

    @Test
    fun detectSupportedFormatIdentifiesCbzAndPdf() {
        assertEquals(ComicFormat.CBZ, DriveSource.detectSupportedFormat("comic.cbz", "md5"))
        assertEquals(ComicFormat.CBZ, DriveSource.detectSupportedFormat("comic.CBZ", "md5"))
        assertEquals(ComicFormat.PDF, DriveSource.detectSupportedFormat("comic.pdf", "md5"))
        assertEquals(ComicFormat.PDF, DriveSource.detectSupportedFormat("comic.PDF", "md5"))

        assertEquals(null, DriveSource.detectSupportedFormat("comic.cbr", "md5"))
        assertEquals(null, DriveSource.detectSupportedFormat(".hidden.pdf", "md5"))
        assertEquals(null, DriveSource.detectSupportedFormat("comic.pdf", null))
        assertEquals(null, DriveSource.detectSupportedFormat("comic.pdf", ""))
    }

    @Test
    fun scanDriveFilesDiscoversBothCbzAndPdfFiles() = runTest(testDispatcher) {
        val cbzFile = createDriveFile("1", "comic.cbz")
        val pdfFile = createDriveFile("2", "graphic_novel.pdf")
        val cbrFile = createDriveFile("3", "unsupported.cbr")

        coEvery { apiClient.listChildren("root-id", null) } returns DriveFileList(
            files = listOf(cbzFile, pdfFile, cbrFile),
            nextPageToken = null
        )

        val discovered = driveSource.scanDriveFiles("root-id")

        assertEquals(2, discovered.size)
        assertEquals(listOf("1", "2"), discovered.map { it.id })
    }

    @Test
    fun listComicFilesMapsPdfFilesCorrectly() = runTest(testDispatcher) {
        val pdfFile = createDriveFile(
            id = "pdf-1",
            name = "Saga Chapter 1.pdf",
            md5Checksum = "saga-md5"
        )
        coEvery { apiClient.listChildren("folder-id", null) } returns DriveFileList(
            files = listOf(pdfFile),
            nextPageToken = null
        )

        val discoveredList = driveSource.listComicFiles("folder-id")

        assertEquals(1, discoveredList.size)
        assertEquals(ComicFormat.PDF, discoveredList[0].format)
        assertEquals("Saga Chapter 1", discoveredList[0].title)
    }
}
