package com.c0mpile.comix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.LibraryFolderDao
import com.c0mpile.comix.data.local.dao.ReadingProgressDao
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.LibraryFolderEntity
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity

/**
 * Primary Room database for the Comix application.
 */
@Database(
    entities = [
        ComicEntity::class,
        ReadingProgressEntity::class,
        LibraryFolderEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(ComicConverters::class)
abstract class ComixDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun libraryFolderDao(): LibraryFolderDao
}
