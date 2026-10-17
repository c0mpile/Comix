package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Updates the currently active library identifier in user settings.
 */
class UpdateActiveLibraryUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(libraryId: String) {
        settingsRepository.setActiveLibraryId(libraryId)
    }
}
