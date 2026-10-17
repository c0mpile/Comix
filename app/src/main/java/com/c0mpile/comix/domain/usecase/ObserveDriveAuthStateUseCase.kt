package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.DriveAuthState
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the current [DriveAuthState] for Google Drive integration.
 */
class ObserveDriveAuthStateUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    operator fun invoke(): Flow<DriveAuthState> = driveRepository.observeAuthState()
}
