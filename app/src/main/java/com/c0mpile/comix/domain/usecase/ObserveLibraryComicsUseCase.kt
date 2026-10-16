package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the list of comics in the user's library along with their reading progress.
 */
class ObserveLibraryComicsUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(): Flow<List<Comic>> = libraryRepository.observeComics()
}
