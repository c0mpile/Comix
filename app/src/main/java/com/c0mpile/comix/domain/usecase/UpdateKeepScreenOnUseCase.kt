package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Persists the keep-screen-on preference for the comic reader.
 */
class UpdateKeepScreenOnUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean) = settingsRepository.setKeepScreenOn(enabled)
}
