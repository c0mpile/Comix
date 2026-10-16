package com.c0mpile.comix.di

import com.c0mpile.comix.data.archive.CbzArchiveReaderFactory
import com.c0mpile.comix.data.archive.ComicArchiveReaderFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding comic archive reader factories.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ArchiveModule {

    @Binds
    @Singleton
    abstract fun bindComicArchiveReaderFactory(
        impl: CbzArchiveReaderFactory
    ): ComicArchiveReaderFactory
}
