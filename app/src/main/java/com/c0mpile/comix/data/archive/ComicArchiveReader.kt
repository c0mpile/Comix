package com.c0mpile.comix.data.archive

import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicMetadata
import com.c0mpile.comix.domain.model.ComicPage
import java.io.Closeable

/**
 * Reads page metadata and extracts individual page bytes from a comic archive.
 *
 * Implementations must operate exclusively through [RandomAccessSource] and never
 * through [java.io.File] or sequential [java.io.InputStream] scans.
 */
interface ComicArchiveReader : Closeable {
    /**
     * Returns the ordered list of image pages in the archive.
     */
    fun getPages(): List<ComicPage>

    /**
     * Extracts the raw encoded image bytes for the page at [pageIndex].
     */
    suspend fun openPageBytes(pageIndex: Int): ByteArray

    /**
     * Extracts embedded comic metadata (such as ComicInfo.xml in CBZ archives) if present.
     */
    suspend fun extractMetadata(): ComicMetadata? = null
}

/**
 * Factory for creating a [ComicArchiveReader] over a [RandomAccessSource].
 */
interface ComicArchiveReaderFactory {
    /**
     * Creates and initializes a [ComicArchiveReader] over [source] for [format].
     * Parses the archive directory immediately so [ComicArchiveReader.getPages] is ready.
     * Takes ownership of [source] and closes it when the reader is closed (or on parse failure).
     */
    suspend fun create(
        source: RandomAccessSource,
        format: ComicFormat,
        fileName: String? = null
    ): ComicArchiveReader
}
