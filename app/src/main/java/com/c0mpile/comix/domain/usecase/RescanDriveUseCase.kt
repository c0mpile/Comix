package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Runs an incremental Google Drive sync (falling back to a full rescan if no valid page token exists).
 */
class RescanDriveUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke(libraryId: String? = null): Result<ScanResult> =
        if (libraryId != null) {
            driveRepository.rescanDriveFoldersForLibrary(libraryId)
        } else {
            driveRepository.incrementalSync()
        }
}
