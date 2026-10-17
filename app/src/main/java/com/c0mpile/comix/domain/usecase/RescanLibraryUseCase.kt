package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Rescans all registered folders in the library for new, modified, or removed comics.
 */
class RescanLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(libraryId: String? = null): Result<ScanResult> =
        if (libraryId != null) {
            libraryRepository.rescanLibraryFolders(libraryId)
        } else {
            libraryRepository.rescanAllFolders()
        }
}
