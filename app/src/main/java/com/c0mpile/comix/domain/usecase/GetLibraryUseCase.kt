package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Retrieves a single library by its identifier.
 */
class GetLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(libraryId: String): ComicLibrary? =
        libraryRepository.getLibrary(libraryId)
}
