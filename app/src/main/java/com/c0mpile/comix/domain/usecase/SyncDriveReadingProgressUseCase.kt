package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Synchronizes local per-comic reading progress with Google Drive `appDataFolder`
 * using a last-write-wins merge strategy.
 */
class SyncDriveReadingProgressUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke(): Result<Int> = driveRepository.syncReadingProgress()
}
