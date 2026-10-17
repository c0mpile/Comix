package com.c0mpile.comix.data.remote.drive

import com.c0mpile.comix.domain.model.ComicError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [OkHttpDriveApiClient] with MockWebServer simulating Google Drive REST v3 responses.
 */
class OkHttpDriveApiClientTest {

    private lateinit var server: MockWebServer
    private val authorizer = mockk<DriveAuthorizer>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var apiClient: OkHttpDriveApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        coEvery { authorizer.getAccessToken(forceRefresh = false) } returns Result.success("fake-token-1")
        coEvery { authorizer.getAccessToken(forceRefresh = true) } returns Result.success("fake-token-refreshed")

        apiClient = OkHttpDriveApiClient(
            okHttpClient = OkHttpClient(),
            authorizer = authorizer,
            retryPolicy = RetryPolicy(delayFn = {}, randomFn = { 0L }),
            ioDispatcher = testDispatcher,
            baseUrl = server.url("/")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listChildrenSendsExpectedHeadersAndQueryParamsAndParsesResponse() = runTest(testDispatcher) {
        val jsonResponse = """
            {
              "nextPageToken": "page-token-2",
              "files": [
                {
                  "id": "file-1",
                  "name": "Action Comics 01.cbz",
                  "mimeType": "application/vnd.comicbook+zip",
                  "size": "15000000",
                  "md5Checksum": "abc123md5",
                  "modifiedTime": "2024-01-01T12:00:00.000Z",
                  "trashed": false
                },
                {
                  "id": "file-2",
                  "name": "Detective Comics 27.cbz",
                  "mimeType": "application/vnd.comicbook+zip",
                  "size": 25000000,
                  "md5Checksum": "def456md5",
                  "trashed": false
                },
                {
                  "id": "shortcut-1",
                  "name": "Shortcut to Marvel",
                  "mimeType": "application/vnd.google-apps.shortcut",
                  "shortcutDetails": {
                    "targetId": "folder-marvel-target",
                    "targetMimeType": "application/vnd.google-apps.folder"
                  },
                  "trashed": false
                }
              ]
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val result = apiClient.listChildren("folder-root-id", "page-token-1")

        val recordedRequest = server.takeRequest()
        assertEquals("Bearer fake-token-1", recordedRequest.getHeader("Authorization"))
        val requestUrl = recordedRequest.requestUrl
        assertNotNull(requestUrl)
        assertEquals("true", requestUrl?.queryParameter("supportsAllDrives"))
        assertEquals("true", requestUrl?.queryParameter("includeItemsFromAllDrives"))
        assertEquals("page-token-1", requestUrl?.queryParameter("pageToken"))
        assertEquals("200", requestUrl?.queryParameter("pageSize"))
        assertTrue(requestUrl?.queryParameter("q")?.contains("'folder-root-id' in parents") == true)

        assertEquals("page-token-2", result.nextPageToken)
        assertEquals(3, result.files.size)

        val file1 = result.files[0]
        assertEquals("file-1", file1.id)
        assertEquals("Action Comics 01.cbz", file1.name)
        assertEquals(15000000L, file1.size)
        assertEquals("abc123md5", file1.md5Checksum)
        assertEquals("2024-01-01T12:00:00.000Z", file1.modifiedTime)
        assertFalse(file1.trashed)

        val file2 = result.files[1]
        assertEquals("file-2", file2.id)
        assertEquals("Detective Comics 27.cbz", file2.name)
        assertEquals(25000000L, file2.size)
        assertEquals("def456md5", file2.md5Checksum)

        val shortcut = result.files[2]
        assertEquals("shortcut-1", shortcut.id)
        assertEquals(MIME_TYPE_SHORTCUT, shortcut.mimeType)
        assertNotNull(shortcut.shortcutDetails)
        assertEquals("folder-marvel-target", shortcut.shortcutDetails?.targetId)
        assertEquals(MIME_TYPE_FOLDER, shortcut.shortcutDetails?.targetMimeType)
    }

    @Test
    fun listSubfoldersSendsFolderQueryAndParsesDriveFileList() = runTest(testDispatcher) {
        val jsonResponse = """
            {
              "files": [
                {
                  "id": "subfolder-1",
                  "name": "DC Comics",
                  "mimeType": "application/vnd.google-apps.folder",
                  "trashed": false
                }
              ]
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val result = apiClient.listSubfolders("root-folder-id")

        val recordedRequest = server.takeRequest()
        val requestUrl = recordedRequest.requestUrl
        assertNotNull(requestUrl)
        assertEquals("name", requestUrl?.queryParameter("orderBy"))
        assertEquals("100", requestUrl?.queryParameter("pageSize"))
        assertEquals("true", requestUrl?.queryParameter("supportsAllDrives"))
        assertEquals("true", requestUrl?.queryParameter("includeItemsFromAllDrives"))
        assertTrue(requestUrl?.queryParameter("q")?.contains("mimeType = '$MIME_TYPE_FOLDER'") == true)

        assertNull(result.nextPageToken)
        assertEquals(1, result.files.size)
        assertEquals("subfolder-1", result.files[0].id)
        assertEquals("DC Comics", result.files[0].name)
    }

    @Test
    fun getFileFetchesAndParsesSingleDriveFile() = runTest(testDispatcher) {
        val jsonResponse = """
            {
              "id": "single-file-id",
              "name": "Special Edition.cbz",
              "mimeType": "application/vnd.comicbook+zip",
              "size": "99999",
              "md5Checksum": "fedcba9876543210",
              "modifiedTime": "2024-02-01T08:30:00.000Z",
              "trashed": false
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val file = apiClient.getFile("single-file-id")

        val recordedRequest = server.takeRequest()
        assertEquals("/files/single-file-id", recordedRequest.path?.substringBefore("?"))
        assertEquals("true", recordedRequest.requestUrl?.queryParameter("supportsAllDrives"))

        assertEquals("single-file-id", file.id)
        assertEquals("Special Edition.cbz", file.name)
        assertEquals(99999L, file.size)
        assertEquals("fedcba9876543210", file.md5Checksum)
        assertFalse(file.trashed)
    }

    @Test
    fun getStartPageTokenAndListChangesParseCorrectly() = runTest(testDispatcher) {
        val startTokenResponse = """
            {
              "startPageToken": "start-token-12345"
            }
        """.trimIndent()

        val changesResponse = """
            {
              "nextPageToken": "changes-page-2",
              "newStartPageToken": "new-start-token-999",
              "changes": [
                {
                  "fileId": "deleted-file-id",
                  "removed": true
                },
                {
                  "fileId": "changed-file-id",
                  "removed": false,
                  "file": {
                    "id": "changed-file-id",
                    "name": "Updated Comic.cbz",
                    "mimeType": "application/vnd.comicbook+zip",
                    "md5Checksum": "newmd5checksum",
                    "size": "12345"
                  }
                }
              ]
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(startTokenResponse)
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(changesResponse)
        )

        val startToken = apiClient.getStartPageToken()
        val request1 = server.takeRequest()
        assertEquals("/changes/startPageToken", request1.path?.substringBefore("?"))
        assertEquals("start-token-12345", startToken)

        val changeList = apiClient.listChanges("start-token-12345")
        val request2 = server.takeRequest()
        assertEquals("/changes", request2.path?.substringBefore("?"))
        assertEquals("start-token-12345", request2.requestUrl?.queryParameter("pageToken"))
        assertEquals("true", request2.requestUrl?.queryParameter("supportsAllDrives"))
        assertEquals("true", request2.requestUrl?.queryParameter("includeItemsFromAllDrives"))

        assertEquals("changes-page-2", changeList.nextPageToken)
        assertEquals("new-start-token-999", changeList.newStartPageToken)
        assertEquals(2, changeList.changes.size)

        val change1 = changeList.changes[0]
        assertEquals("deleted-file-id", change1.fileId)
        assertTrue(change1.removed)
        assertNull(change1.file)

        val change2 = changeList.changes[1]
        assertEquals("changed-file-id", change2.fileId)
        assertFalse(change2.removed)
        assertNotNull(change2.file)
        assertEquals("Updated Comic.cbz", change2.file?.name)
        assertEquals("newmd5checksum", change2.file?.md5Checksum)
    }

    @Test
    fun http401ForcesTokenRefreshAndRetriesWithNewToken() = runTest(testDispatcher) {
        val error401 = """
            {
              "error": {
                "code": 401,
                "message": "Invalid Credentials"
              }
            }
        """.trimIndent()

        val success200 = """
            {
              "id": "file-refreshed",
              "name": "Refreshed.cbz",
              "mimeType": "application/vnd.comicbook+zip"
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(401).setBody(error401))
        server.enqueue(MockResponse().setResponseCode(200).setBody(success200))

        val file = apiClient.getFile("file-refreshed")

        val req1 = server.takeRequest()
        assertEquals("Bearer fake-token-1", req1.getHeader("Authorization"))

        val req2 = server.takeRequest()
        assertEquals("Bearer fake-token-refreshed", req2.getHeader("Authorization"))

        assertEquals("file-refreshed", file.id)
        assertEquals("Refreshed.cbz", file.name)

        coVerify(exactly = 1) { authorizer.getAccessToken(forceRefresh = true) }
        coVerify(exactly = 0) { authorizer.onAuthExpired(any()) }
    }

    @Test
    fun doubleHttp401CallsOnAuthExpiredAndThrowsAuthExpired() = runTest(testDispatcher) {
        val error401 = """
            {
              "error": {
                "code": 401,
                "message": "Invalid Credentials"
              }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(401).setBody(error401))
        server.enqueue(MockResponse().setResponseCode(401).setBody(error401))

        val exception = runCatching {
            apiClient.getFile("file-double-401")
        }.exceptionOrNull() as? DriveApiException

        assertNotNull(exception)
        assertTrue(exception?.comicError is ComicError.AuthExpired)
        coVerify(exactly = 1) { authorizer.getAccessToken(forceRefresh = true) }
        coVerify(exactly = 1) { authorizer.onAuthExpired("Access token expired or revoked") }
    }
}
