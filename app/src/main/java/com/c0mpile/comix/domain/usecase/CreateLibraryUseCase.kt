package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Creates a new named user library with a default reading direction.
 */
class CreateLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(
        name: String,
        defaultReadingDirection: ReadingDirection
    ): Result<ComicLibrary> =
        libraryRepository.createLibrary(name, defaultReadingDirection)
}
