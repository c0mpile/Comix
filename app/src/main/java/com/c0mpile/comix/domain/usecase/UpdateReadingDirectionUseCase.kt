package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Persists the user's default [ReadingDirection].
 */
class UpdateReadingDirectionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(direction: ReadingDirection) =
        settingsRepository.setReadingDirection(direction)
}
