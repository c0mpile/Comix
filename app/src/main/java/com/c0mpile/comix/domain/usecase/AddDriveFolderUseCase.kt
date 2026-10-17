package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Registers a Google Drive root folder in the library and performs an initial recursive scan.
 */
class AddDriveFolderUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke(
        folderId: String,
        displayName: String,
        driveId: String? = null,
        libraryId: String = ComicLibrary.DEFAULT_COMICS_ID
    ): Result<ScanResult> =
        driveRepository.addDriveFolder(folderId, displayName, driveId, libraryId)
}
