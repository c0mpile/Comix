package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Adds a SAF folder URI to the library and scans it for comic files.
 */
class AddLibraryFolderUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(
        treeUri: String,
        libraryId: String = ComicLibrary.DEFAULT_COMICS_ID
    ): Result<ScanResult> =
        libraryRepository.addFolderAndScan(treeUri, libraryId)
}
