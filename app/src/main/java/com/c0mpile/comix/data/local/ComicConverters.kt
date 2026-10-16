package com.c0mpile.comix.data.local

import androidx.room.TypeConverter
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType

/**
 * Room type converters for domain enum types stored as strings.
 */
class ComicConverters {
    @TypeConverter
    fun fromComicFormat(value: ComicFormat): String = value.name

    @TypeConverter
    fun toComicFormat(value: String): ComicFormat = ComicFormat.valueOf(value)

    @TypeConverter
    fun fromComicSourceType(value: ComicSourceType): String = value.name

    @TypeConverter
    fun toComicSourceType(value: String): ComicSourceType = ComicSourceType.valueOf(value)

    @TypeConverter
    fun fromComicAvailability(value: ComicAvailability): String = value.name

    @TypeConverter
    fun toComicAvailability(value: String): ComicAvailability = ComicAvailability.valueOf(value)
}
