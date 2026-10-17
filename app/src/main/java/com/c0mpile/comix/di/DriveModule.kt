package com.c0mpile.comix.di

import com.c0mpile.comix.data.remote.drive.DriveApiClient
import com.c0mpile.comix.data.remote.drive.DriveAuthorizer
import com.c0mpile.comix.data.remote.drive.DriveAuthorizerImpl
import com.c0mpile.comix.data.remote.drive.OkHttpDriveApiClient
import com.c0mpile.comix.data.repository.DriveRepositoryImpl
import com.c0mpile.comix.domain.repository.DriveRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding Google Drive remote client, authorizer, and repository implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DriveModule {

    @Binds
    @Singleton
    abstract fun bindDriveAuthorizer(
        impl: DriveAuthorizerImpl
    ): DriveAuthorizer

    @Binds
    @Singleton
    abstract fun bindDriveApiClient(
        impl: OkHttpDriveApiClient
    ): DriveApiClient

    @Binds
    @Singleton
    abstract fun bindDriveRepository(
        impl: DriveRepositoryImpl
    ): DriveRepository
}
