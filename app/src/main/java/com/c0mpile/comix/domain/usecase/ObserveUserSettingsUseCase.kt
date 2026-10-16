package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the current [UserSettings] stream from preferences storage.
 */
class ObserveUserSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<UserSettings> = settingsRepository.observeUserSettings()
}
