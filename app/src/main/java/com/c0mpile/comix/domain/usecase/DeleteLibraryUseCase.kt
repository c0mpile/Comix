package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Deletes a library and its associated folders and comics.
 */
class DeleteLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(libraryId: String): Result<Unit> =
        libraryRepository.deleteLibrary(libraryId)
}
