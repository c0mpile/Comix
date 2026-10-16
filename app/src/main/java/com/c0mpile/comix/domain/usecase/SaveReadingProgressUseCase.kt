package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.ReaderRepository
import javax.inject.Inject

/**
 * Persists reading progress for a comic identified by [comicKey].
 */
class SaveReadingProgressUseCase @Inject constructor(
    private val readerRepository: ReaderRepository
) {
    suspend operator fun invoke(comicKey: String, currentPage: Int, pageCount: Int) =
        readerRepository.saveReadingProgress(comicKey, currentPage, pageCount)
}
