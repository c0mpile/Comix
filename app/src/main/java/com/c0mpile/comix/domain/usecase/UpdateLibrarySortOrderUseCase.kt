package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Persists the selected [LibrarySortOrder] for the comic library grid.
 */
class UpdateLibrarySortOrderUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(sortOrder: LibrarySortOrder) =
        settingsRepository.setLibrarySortOrder(sortOrder)
}
