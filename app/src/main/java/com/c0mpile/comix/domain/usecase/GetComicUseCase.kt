package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.repository.ReaderRepository
import javax.inject.Inject

/**
 * Retrieves a single comic and its reading progress by its content-based [comicKey].
 */
class GetComicUseCase @Inject constructor(
    private val readerRepository: ReaderRepository
) {
    suspend operator fun invoke(comicKey: String): Comic? =
        readerRepository.getComic(comicKey)
}
