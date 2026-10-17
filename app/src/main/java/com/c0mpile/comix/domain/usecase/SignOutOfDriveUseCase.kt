package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Signs out of Google Drive and clears persisted Drive folders and remote comic entries.
 */
class SignOutOfDriveUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke() = driveRepository.signOut()
}
