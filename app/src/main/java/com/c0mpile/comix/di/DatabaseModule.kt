package com.c0mpile.comix.di

import android.content.Context
import androidx.room.Room
import com.c0mpile.comix.data.local.ComixDatabase
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.LibraryFolderDao
import com.c0mpile.comix.data.local.dao.ReadingProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room database singleton and its DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ComixDatabase =
        Room.databaseBuilder(context, ComixDatabase::class.java, "comix.db")
            .build()

    @Provides
    fun provideComicDao(db: ComixDatabase): ComicDao = db.comicDao()

    @Provides
    fun provideReadingProgressDao(db: ComixDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideLibraryFolderDao(db: ComixDatabase): LibraryFolderDao = db.libraryFolderDao()
}
