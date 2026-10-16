package com.c0mpile.comix.di

import com.c0mpile.comix.data.source.ComicKeyHasher
import com.c0mpile.comix.data.source.ComicSource
import com.c0mpile.comix.data.source.LocalRandomAccessSourceFactory
import com.c0mpile.comix.data.source.LocalSource
import com.c0mpile.comix.data.source.RandomAccessSourceFactory
import com.c0mpile.comix.data.source.Sha256ComicKeyHasher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding comic source and random-access factory implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {

    @Binds
    @Singleton
    abstract fun bindRandomAccessSourceFactory(
        impl: LocalRandomAccessSourceFactory
    ): RandomAccessSourceFactory

    @Binds
    @Singleton
    abstract fun bindComicKeyHasher(
        impl: Sha256ComicKeyHasher
    ): ComicKeyHasher

    @Binds
    @Singleton
    abstract fun bindLocalComicSource(
        impl: LocalSource
    ): ComicSource
}
