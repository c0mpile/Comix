package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.DriveBrowseItem
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Lists subfolders and folder shortcuts inside a Google Drive parent folder.
 */
class BrowseDriveFoldersUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke(parentFolderId: String): Result<List<DriveBrowseItem>> =
        driveRepository.browseSubfolders(parentFolderId)
}
