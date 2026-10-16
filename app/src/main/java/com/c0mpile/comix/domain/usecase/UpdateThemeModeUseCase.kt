package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Persists the user's selected [AppThemeMode].
 */
class UpdateThemeModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(mode: AppThemeMode) = settingsRepository.setThemeMode(mode)
}
