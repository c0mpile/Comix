package com.c0mpile.comix.domain.repository

import android.graphics.Bitmap
import com.c0mpile.comix.data.archive.ComicArchiveReader
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ReadingProgress

/**
 * Repository contract for opening comic archives, decoding downsampled pages, and persisting reading progress.
 */
interface ReaderRepository {
    suspend fun getComic(comicKey: String): Comic?
    suspend fun openComicReader(comic: Comic): Result<ComicArchiveReader>
    suspend fun decodePageBitmap(
        reader: ComicArchiveReader,
        pageIndex: Int,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): Result<Bitmap>
    suspend fun getReadingProgress(comicKey: String): ReadingProgress?
    suspend fun saveReadingProgress(comicKey: String, currentPage: Int, pageCount: Int)
}
