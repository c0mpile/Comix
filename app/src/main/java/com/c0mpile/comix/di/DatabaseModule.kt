package com.c0mpile.comix.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.c0mpile.comix.data.local.ComixDatabase
import com.c0mpile.comix.data.local.ComixMigrations
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.DriveAccountDao
import com.c0mpile.comix.data.local.dao.DriveFolderDao
import com.c0mpile.comix.data.local.dao.LibraryDao
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
            .addMigrations(
                ComixMigrations.MIGRATION_1_2,
                ComixMigrations.MIGRATION_2_3,
                ComixMigrations.MIGRATION_3_4
            )
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ComixMigrations.seedDefaultLibraries(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        ComixMigrations.seedDefaultLibraries(db)
                    }
                }
            )
            .build()

    @Provides
    fun provideComicDao(db: ComixDatabase): ComicDao = db.comicDao()

    @Provides
    fun provideReadingProgressDao(db: ComixDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideLibraryFolderDao(db: ComixDatabase): LibraryFolderDao = db.libraryFolderDao()

    @Provides
    fun provideDriveAccountDao(db: ComixDatabase): DriveAccountDao = db.driveAccountDao()

    @Provides
    fun provideDriveFolderDao(db: ComixDatabase): DriveFolderDao = db.driveFolderDao()

    @Provides
    fun provideLibraryDao(db: ComixDatabase): LibraryDao = db.libraryDao()
}
