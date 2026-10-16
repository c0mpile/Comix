package com.c0mpile.comix.di

import com.c0mpile.comix.data.repository.LibraryRepositoryImpl
import com.c0mpile.comix.data.repository.ReaderRepositoryImpl
import com.c0mpile.comix.domain.repository.LibraryRepository
import com.c0mpile.comix.domain.repository.ReaderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding domain repository interfaces to their data-layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        impl: LibraryRepositoryImpl
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindReaderRepository(
        impl: ReaderRepositoryImpl
    ): ReaderRepository
}
