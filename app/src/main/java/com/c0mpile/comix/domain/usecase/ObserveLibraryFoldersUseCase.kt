package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the registered root folders in the library.
 */
class ObserveLibraryFoldersUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(libraryId: String? = null): Flow<List<LibraryFolder>> =
        if (libraryId != null) {
            libraryRepository.observeFoldersByLibrary(libraryId)
        } else {
            libraryRepository.observeFolders()
        }
}
