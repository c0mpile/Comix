package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Removes a registered folder and its local comic entries from the library while preserving reading progress.
 */
class RemoveLibraryFolderUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(folderUri: String) =
        libraryRepository.removeFolder(folderUri)
}
