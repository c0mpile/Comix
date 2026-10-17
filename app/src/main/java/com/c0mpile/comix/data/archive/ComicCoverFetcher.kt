package com.c0mpile.comix.data.archive

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

/**
 * Model request representing a comic cover image load for Coil.
 */
data class ComicCoverRequest(
    val comicKey: String,
    val localUri: String?,
    val coverCachePath: String? = null,
    val driveFileId: String? = null,
    val sizeBytes: Long = 0L
)

/**
 * Coil Fetcher that obtains or extracts comic covers via [CoverExtractor].
 */
class ComicCoverFetcher(
    private val data: ComicCoverRequest,
    private val coverExtractor: CoverExtractor
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = coverExtractor.getOrExtractCoverFile(
            comicKey = data.comicKey,
            localUri = data.localUri,
            existingCachePath = data.coverCachePath,
            driveFileId = data.driveFileId,
            sizeBytes = data.sizeBytes
        ) ?: return null

        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = "image/webp",
            dataSource = DataSource.DISK
        )
    }

    /**
     * Factory for creating [ComicCoverFetcher] instances.
     */
    class Factory @Inject constructor(
        private val coverExtractor: CoverExtractor
    ) : Fetcher.Factory<ComicCoverRequest> {

        override fun create(
            data: ComicCoverRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return ComicCoverFetcher(data, coverExtractor)
        }
    }
}

/**
 * Memory cache keyer for [ComicCoverRequest].
 */
class ComicCoverKeyer @Inject constructor() : Keyer<ComicCoverRequest> {

    override fun key(data: ComicCoverRequest, options: Options): String {
        return "cover:${data.comicKey}"
    }
}
