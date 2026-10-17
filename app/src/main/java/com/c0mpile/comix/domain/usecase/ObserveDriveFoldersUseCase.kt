package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the list of registered Google Drive root folders in the library.
 */
class ObserveDriveFoldersUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    operator fun invoke(): Flow<List<DriveFolder>> = driveRepository.observeDriveFolders()
}
