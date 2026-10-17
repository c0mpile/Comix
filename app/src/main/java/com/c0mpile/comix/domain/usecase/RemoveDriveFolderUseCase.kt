package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Removes a registered Google Drive root folder and its remote comic entries from the library.
 */
class RemoveDriveFolderUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke(folderId: String) =
        driveRepository.removeDriveFolder(folderId)
}
