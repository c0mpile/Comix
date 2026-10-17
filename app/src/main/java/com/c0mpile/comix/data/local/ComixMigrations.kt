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
}
