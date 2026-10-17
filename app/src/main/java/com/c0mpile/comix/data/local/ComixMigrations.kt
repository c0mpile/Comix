package com.c0mpile.comix.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room schema migrations for [ComixDatabase].
 */
object ComixMigrations {
    /**
     * Migrates from schema version 1 to version 2 by adding the `drive_account`
     * and `drive_folders` tables for Google Drive integration.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drive_account` (`id` INTEGER NOT NULL, `email` TEXT NOT NULL, `signed_in_at_epoch_millis` INTEGER NOT NULL, `changes_page_token` TEXT, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drive_folders` (`folder_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, `added_at_epoch_millis` INTEGER NOT NULL, `last_scanned_at_epoch_millis` INTEGER, `drive_id` TEXT, PRIMARY KEY(`folder_id`))"
            )
        }
    }

    /**
     * Migrates from schema version 2 to version 3 by adding the `libraries` table,
     * seeding the built-in Comics and Manga libraries, and adding `library_id` columns
     * and indices to `comics`, `library_folders`, and `drive_folders`.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `libraries` (`library_id` TEXT NOT NULL, `name` TEXT NOT NULL, `default_reading_direction` TEXT NOT NULL, `created_at_epoch_millis` INTEGER NOT NULL, `is_built_in` INTEGER NOT NULL, PRIMARY KEY(`library_id`))"
            )
            seedDefaultLibraries(db)
            db.execSQL(
                "ALTER TABLE `comics` ADD COLUMN `library_id` TEXT NOT NULL DEFAULT 'comics'"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_comics_library_id` ON `comics` (`library_id`)"
            )
            db.execSQL(
                "ALTER TABLE `library_folders` ADD COLUMN `library_id` TEXT NOT NULL DEFAULT 'comics'"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_library_folders_library_id` ON `library_folders` (`library_id`)"
            )
            db.execSQL(
                "ALTER TABLE `drive_folders` ADD COLUMN `library_id` TEXT NOT NULL DEFAULT 'comics'"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_drive_folders_library_id` ON `drive_folders` (`library_id`)"
            )
        }
    }

    /**
     * Migrates from schema version 3 to version 4 by adding nullable embedded metadata
     * columns to the `comics` table.
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_title` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_series` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_number` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_volume` INTEGER")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_summary` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_writer` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_penciller` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_publisher` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_genre` TEXT")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_release_year` INTEGER")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_release_month` INTEGER")
            db.execSQL("ALTER TABLE `comics` ADD COLUMN `meta_release_day` INTEGER")
        }
    }

    /**
     * Seeds the built-in Comics (`LEFT_TO_RIGHT`) and Manga (`RIGHT_TO_LEFT`) libraries
     * when the `libraries` table is empty.
     */
    fun seedDefaultLibraries(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO `libraries` (`library_id`, `name`, `default_reading_direction`, `created_at_epoch_millis`, `is_built_in`)
            SELECT 'comics', 'Comics', 'LEFT_TO_RIGHT', 0, 1
            WHERE NOT EXISTS (SELECT 1 FROM `libraries`)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `libraries` (`library_id`, `name`, `default_reading_direction`, `created_at_epoch_millis`, `is_built_in`)
            SELECT 'manga', 'Manga', 'RIGHT_TO_LEFT', 1, 1
            WHERE (SELECT COUNT(*) FROM `libraries` WHERE `library_id` = 'comics' AND `created_at_epoch_millis` = 0) = 1
              AND (SELECT COUNT(*) FROM `libraries`) = 1
            """.trimIndent()
        )
    }
}
