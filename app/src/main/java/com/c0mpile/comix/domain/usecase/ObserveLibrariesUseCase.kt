package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the ordered list of user libraries.
 */
class ObserveLibrariesUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(): Flow<List<ComicLibrary>> = libraryRepository.observeLibraries()
}
