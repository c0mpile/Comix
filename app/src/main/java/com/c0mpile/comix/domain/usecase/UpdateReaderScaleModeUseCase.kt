package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Persists the user's preferred [ReaderScaleMode].
 */
class UpdateReaderScaleModeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(scaleMode: ReaderScaleMode) =
        settingsRepository.setReaderScaleMode(scaleMode)
}
