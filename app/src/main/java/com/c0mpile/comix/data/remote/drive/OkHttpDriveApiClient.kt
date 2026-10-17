package com.c0mpile.comix.data.remote.drive

import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicError
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Implementation of [DriveApiClient] calling the Google Drive REST v3 API via OkHttp.
 */
@Singleton
class OkHttpDriveApiClient(
    private val okHttpClient: OkHttpClient,
    private val authorizer: DriveAuthorizer,
    private val retryPolicy: RetryPolicy,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val json: Json = defaultJson
) : DriveApiClient {

    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        authorizer: DriveAuthorizer,
        retryPolicy: RetryPolicy,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        okHttpClient = okHttpClient,
        authorizer = authorizer,
        retryPolicy = retryPolicy,
        ioDispatcher = ioDispatcher,
        baseUrl = DEFAULT_BASE_URL.toHttpUrl()
    )

    companion object {
        const val DEFAULT_BASE_URL: String = "https://www.googleapis.com/drive/v3/"
        const val PROGRESS_SYNC_FILE_NAME: String = "comix_reading_progress.json"

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private const val STANDARD_FILE_FIELDS =
            "id,name,mimeType,md5Checksum,size,modifiedTime,parents,shortcutDetails(targetId,targetMimeType),driveId,trashed"
        private const val LIST_FILES_FIELDS =
            "nextPageToken,files($STANDARD_FILE_FIELDS)"
        private const val LIST_CHANGES_FIELDS =
            "nextPageToken,newStartPageToken,changes(fileId,removed,file($STANDARD_FILE_FIELDS))"
    }

    override suspend fun listChildren(parentId: String, pageToken: String?): DriveFileList {
        val escapedParent = escapeQuery(parentId)
        val query = "'$escapedParent' in parents and trashed = false and " +
                "(mimeType = '$MIME_TYPE_FOLDER' or mimeType = '$MIME_TYPE_SHORTCUT' or " +
                "name contains '.cbz' or name contains '.CBZ' or name contains '.pdf' or name contains '.PDF')"

        return executeAndDecode { urlBuilder ->
            urlBuilder.addPathSegment("files")
                .addQueryParameter("q", query)
                .addQueryParameter("fields", LIST_FILES_FIELDS)
                .addQueryParameter("pageSize", "200")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .apply {
                    if (!pageToken.isNullOrBlank()) {
                        addQueryParameter("pageToken", pageToken)
                    }
                }
        }
    }

    override suspend fun listSubfolders(parentId: String, pageToken: String?): DriveFileList {
        val escapedParent = escapeQuery(parentId)
        val query = "'$escapedParent' in parents and trashed = false and " +
                "(mimeType = '$MIME_TYPE_FOLDER' or " +
                "(mimeType = '$MIME_TYPE_SHORTCUT' and shortcutDetails.targetMimeType = '$MIME_TYPE_FOLDER'))"

        return executeAndDecode { urlBuilder ->
            urlBuilder.addPathSegment("files")
                .addQueryParameter("q", query)
                .addQueryParameter("orderBy", "name")
                .addQueryParameter("fields", LIST_FILES_FIELDS)
                .addQueryParameter("pageSize", "100")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .apply {
                    if (!pageToken.isNullOrBlank()) {
                        addQueryParameter("pageToken", pageToken)
                    }
                }
        }
    }

    override suspend fun getFile(fileId: String): DriveFile {
        return executeAndDecode { urlBuilder ->
            urlBuilder.addPathSegment("files")
                .addPathSegment(fileId)
                .addQueryParameter("fields", STANDARD_FILE_FIELDS)
                .addQueryParameter("supportsAllDrives", "true")
        }
    }

    override suspend fun getStartPageToken(): String {
        val response = executeAndDecode<StartPageTokenResponse> { urlBuilder ->
            urlBuilder.addPathSegment("changes")
                .addPathSegment("startPageToken")
                .addQueryParameter("supportsAllDrives", "true")
        }
        return response.startPageToken
    }

    override suspend fun listChanges(pageToken: String): DriveChangeList {
        return executeAndDecode { urlBuilder ->
            urlBuilder.addPathSegment("changes")
                .addQueryParameter("pageToken", pageToken)
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .addQueryParameter("fields", LIST_CHANGES_FIELDS)
        }
    }

    override suspend fun downloadRange(
        fileId: String,
        startByte: Long,
        endByteInclusive: Long
    ): ByteArray = withContext(ioDispatcher) {
        require(startByte >= 0L && endByteInclusive >= startByte) {
            "Invalid byte range: startByte=$startByte, endByteInclusive=$endByteInclusive"
        }
        val response = executeRequest { targetBaseUrl ->
            val url = targetBaseUrl.newBuilder()
                .addPathSegment("files")
                .addPathSegment(fileId)
                .addQueryParameter("alt", "media")
                .addQueryParameter("supportsAllDrives", "true")
                .build()
            Request.Builder()
                .url(url)
                .header("Range", "bytes=$startByte-$endByteInclusive")
        }
        if (!response.isSuccessful) {
            val code = response.code
            val message = response.message
            response.close()
            throw DriveApiException(ComicError.DriveApiError(code, message))
        }
        response.use { it.body.bytes() }
    }

    override suspend fun readProgressSyncPayload(): String? = withContext(ioDispatcher) {
        val fileList = executeAndDecode<DriveFileList> { urlBuilder ->
            urlBuilder.addPathSegment("files")
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "name = '$PROGRESS_SYNC_FILE_NAME' and trashed = false")
                .addQueryParameter("fields", LIST_FILES_FIELDS)
                .addQueryParameter("pageSize", "1")
        }
        val file = fileList.files.firstOrNull() ?: return@withContext null
        val response = executeRequest { targetBaseUrl ->
            val url = targetBaseUrl.newBuilder()
                .addPathSegment("files")
                .addPathSegment(file.id)
                .addQueryParameter("alt", "media")
                .build()
            Request.Builder().url(url)
        }
        if (!response.isSuccessful) {
            val code = response.code
            val message = response.message
            response.close()
            throw DriveApiException(ComicError.DriveApiError(code, message))
        }
        response.use { it.body.string() }
    }

    override suspend fun writeProgressSyncPayload(jsonPayload: String): Unit = withContext(ioDispatcher) {
        val fileList = executeAndDecode<DriveFileList> { urlBuilder ->
            urlBuilder.addPathSegment("files")
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "name = '$PROGRESS_SYNC_FILE_NAME' and trashed = false")
                .addQueryParameter("fields", LIST_FILES_FIELDS)
                .addQueryParameter("pageSize", "1")
        }

        val targetFileId = fileList.files.firstOrNull()?.id ?: run {
            val createResponse = executeRequest { targetBaseUrl ->
                val url = targetBaseUrl.newBuilder()
                    .addPathSegment("files")
                    .addQueryParameter("fields", "id")
                    .build()
                val body = "{\"name\":\"$PROGRESS_SYNC_FILE_NAME\",\"parents\":[\"appDataFolder\"]}"
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                Request.Builder().url(url).post(body)
            }
            if (!createResponse.isSuccessful) {
                val code = createResponse.code
                val message = createResponse.message
                createResponse.close()
                throw DriveApiException(ComicError.DriveApiError(code, message))
            }
            val responseText = createResponse.use { it.body.string() }
            val created = json.decodeFromString<CreateFileResponse>(responseText)
            created.id
        }

        val patchResponse = executeRequest { targetBaseUrl ->
            val uploadUrl = if (targetBaseUrl.encodedPath.contains("/drive/v3")) {
                targetBaseUrl.newBuilder()
                    .encodedPath("/upload/drive/v3/files/$targetFileId")
                    .addQueryParameter("uploadType", "media")
                    .build()
            } else {
                targetBaseUrl.newBuilder()
                    .addPathSegment("upload")
                    .addPathSegment("files")
                    .addPathSegment(targetFileId)
                    .addQueryParameter("uploadType", "media")
                    .build()
            }
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            Request.Builder().url(uploadUrl).patch(body)
        }
        if (!patchResponse.isSuccessful) {
            val code = patchResponse.code
            val message = patchResponse.message
            patchResponse.close()
            throw DriveApiException(ComicError.DriveApiError(code, message))
        }
        patchResponse.close()
    }

    private suspend inline fun <reified T> executeAndDecode(
        crossinline buildUrl: (HttpUrl.Builder) -> HttpUrl.Builder
    ): T = withContext(ioDispatcher) {
        val response = executeRequest { targetBaseUrl ->
            val url = buildUrl(targetBaseUrl.newBuilder()).build()
            Request.Builder().url(url)
        }

        val bodyString = response.body.string()
        try {
            json.decodeFromString<T>(bodyString)
        } catch (e: Exception) {
            throw DriveApiException(
                ComicError.DriveApiError(response.code, e.message),
                cause = e
            )
        }
    }

    private suspend fun executeRequest(
        requestBuilderAction: (HttpUrl) -> Request.Builder
    ): Response = withContext(ioDispatcher) {
        retryPolicy.execute {
            val initialToken = authorizer.getAccessToken(forceRefresh = false)
                .getOrElse { throwable ->
                    throw (throwable as? DriveApiException)
                        ?: DriveApiException(ComicError.AuthExpired, cause = throwable)
                }

            val request = requestBuilderAction(baseUrl)
                .header("Authorization", "Bearer $initialToken")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.code == 401) {
                response.close()
                val refreshedToken = authorizer.getAccessToken(forceRefresh = true)
                    .getOrElse { throwable ->
                        authorizer.onAuthExpired("Access token refresh failed")
                        throw (throwable as? DriveApiException)
                            ?: DriveApiException(ComicError.AuthExpired, cause = throwable)
                    }

                val retryRequest = requestBuilderAction(baseUrl)
                    .header("Authorization", "Bearer $refreshedToken")
                    .build()

                val retryResponse = okHttpClient.newCall(retryRequest).execute()
                if (retryResponse.code == 401) {
                    retryResponse.close()
                    authorizer.onAuthExpired("Access token expired or revoked")
                    throw DriveApiException(ComicError.AuthExpired)
                }
                retryResponse
            } else {
                response
            }
        }
    }

    private fun escapeQuery(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    @Serializable
    private data class StartPageTokenResponse(val startPageToken: String)

    @Serializable
    private data class CreateFileResponse(val id: String)
}
