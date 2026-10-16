package com.c0mpile.comix.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Hilt module providing the singleton Jetpack DataStore for user preferences.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private const val SETTINGS_DATASTORE_NAME = "comix_settings"

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) }
        )
}
