package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Updates an existing library's display name and default reading direction.
 */
class UpdateLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(
        libraryId: String,
        name: String,
        defaultReadingDirection: ReadingDirection
    ): Result<Unit> =
        libraryRepository.updateLibrary(libraryId, name, defaultReadingDirection)
}
