package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.LibraryLayoutMode
import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Updates the user's preferred layout mode (grid or list) for displaying comics.
 */
class UpdateLibraryLayoutModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(layoutMode: LibraryLayoutMode) =
        settingsRepository.setLibraryLayoutMode(layoutMode)
}
