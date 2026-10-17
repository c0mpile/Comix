package com.c0mpile.comix.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Local storage comic source using the Storage Access Framework (SAF).
 *
 * Traverses directory trees off the main thread via [DocumentsContract] child queries.
 */
class LocalSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val randomAccessSourceFactory: RandomAccessSourceFactory,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ComicSource {

    override val sourceType: ComicSourceType = ComicSourceType.LOCAL_SAF

    override suspend fun hasPermission(folderUri: String): Boolean = withContext(ioDispatcher) {
        try {
            val parsedUri = Uri.parse(folderUri)
            context.contentResolver.persistedUriPermissions.any {
                it.uri == parsedUri && it.isReadPermission
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getFolderDisplayName(folderUri: String): String = withContext(ioDispatcher) {
        val treeUri = Uri.parse(folderUri)
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (index >= 0) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) {
                            return@withContext name
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            val name = docFile?.name
            if (!name.isNullOrBlank()) {
                return@withContext name
            }
        } catch (_: Exception) {}

        treeUri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() } ?: folderUri
    }

    override suspend fun listComicFiles(folderUri: String): List<DiscoveredComicFile> =
        withContext(ioDispatcher) {
            val treeUri = Uri.parse(folderUri)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val discoveredFiles = mutableListOf<DiscoveredComicFile>()

            val visitedDocIds = HashSet<String>()
            val queue = ArrayDeque<String>()

            visitedDocIds.add(rootDocId)
            queue.add(rootDocId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            while (queue.isNotEmpty()) {
                val currentDocId = queue.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    currentDocId
                )

                context.contentResolver.query(
                    childrenUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val childDocId = if (idIndex >= 0) cursor.getString(idIndex) else null
                        if (childDocId == null) continue
                        val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                        val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) ?: "" else ""
                        val sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                        val lastMod = if (modIndex >= 0 && !cursor.isNull(modIndex)) cursor.getLong(modIndex) else 0L

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (visitedDocIds.add(childDocId)) {
                                queue.add(childDocId)
                            }
                        } else {
                            val format = detectSupportedFormat(displayName)
                            if (format != null) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId).toString()
                                val title = displayName.substringBeforeLast('.')
                                discoveredFiles.add(
                                    DiscoveredComicFile(
                                        uri = fileUri,
                                        folderUri = folderUri,
                                        fileName = displayName,
                                        title = title,
                                        format = format,
                                        sizeBytes = maxOf(0L, sizeBytes),
                                        lastModifiedEpochMillis = maxOf(0L, lastMod)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            discoveredFiles
        }

    override suspend fun openRandomAccessSource(uri: String): RandomAccessSource =
        randomAccessSourceFactory.openLocal(uri)

    companion object {
        /**
         * Returns the supported [ComicFormat] for [fileName] based on its extension,
         * or null if the file is hidden (starts with '.') or of an unsupported format.
         */
        fun detectSupportedFormat(fileName: String): ComicFormat? {
            if (fileName.startsWith(".")) return null
            return when {
                fileName.endsWith(".cbz", ignoreCase = true) -> ComicFormat.CBZ
                fileName.endsWith(".pdf", ignoreCase = true) -> ComicFormat.PDF
                else -> null
            }
        }
    }
}
