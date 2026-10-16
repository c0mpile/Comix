package com.c0mpile.comix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Top-level ViewModel providing application-wide user settings for theme observation.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeUserSettingsUseCase: ObserveUserSettingsUseCase
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = observeUserSettingsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserSettings()
        )
}
