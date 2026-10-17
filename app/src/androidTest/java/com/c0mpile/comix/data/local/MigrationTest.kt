package com.c0mpile.comix.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for verifying Room schema migrations for [ComixDatabase].
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ComixDatabase::class.java
    )

    @Test
    fun migrate1To2PreservesExistingDataAndCreatesDriveTables() {
        val dbV1 = helper.createDatabase(TEST_DB, 1)

        dbV1.execSQL(
            """
            INSERT INTO comics (
                comic_key, title, file_name, format, source_type, availability,
                size_bytes, last_modified_epoch_millis, page_count, local_uri, folder_uri
            ) VALUES (
                'local:comic1', 'Amazing Spider-Man #1', 'spiderman_01.cbz', 'CBZ',
                'LOCAL_SAF', 'LOCAL', 1048576, 1700000000000, 24,
                'content://com.android.externalstorage.documents/tree/comics/spiderman_01.cbz',
                'content://com.android.externalstorage.documents/tree/comics'
            )
            """.trimIndent()
        )

        dbV1.execSQL(
            """
            INSERT INTO reading_progress (
                comic_key, current_page, page_count, is_finished, updated_at_epoch_millis
            ) VALUES (
                'local:comic1', 5, 24, 0, 1700000050000
            )
            """.trimIndent()
        )

        dbV1.execSQL(
            """
            INSERT INTO library_folders (
                folder_uri, display_name, added_at_epoch_millis, last_scanned_at_epoch_millis
            ) VALUES (
                'content://com.android.externalstorage.documents/tree/comics', 'Local Comics', 1700000000000, 1700000010000
            )
            """.trimIndent()
        )

        dbV1.close()

        val dbV2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, ComixMigrations.MIGRATION_1_2)

        val comicCursor = dbV2.query("SELECT comic_key, title, file_name, format, source_type, availability, size_bytes, page_count, local_uri, folder_uri FROM comics WHERE comic_key = 'local:comic1'")
        assertTrue(comicCursor.moveToFirst())
        assertEquals("local:comic1", comicCursor.getString(0))
        assertEquals("Amazing Spider-Man #1", comicCursor.getString(1))
        assertEquals("spiderman_01.cbz", comicCursor.getString(2))
        assertEquals("CBZ", comicCursor.getString(3))
        assertEquals("LOCAL_SAF", comicCursor.getString(4))
        assertEquals("LOCAL", comicCursor.getString(5))
        assertEquals(1048576L, comicCursor.getLong(6))
        assertEquals(24, comicCursor.getInt(7))
        assertEquals("content://com.android.externalstorage.documents/tree/comics/spiderman_01.cbz", comicCursor.getString(8))
        assertEquals("content://com.android.externalstorage.documents/tree/comics", comicCursor.getString(9))
        comicCursor.close()

        val progressCursor = dbV2.query("SELECT comic_key, current_page, page_count, is_finished, updated_at_epoch_millis FROM reading_progress WHERE comic_key = 'local:comic1'")
        assertTrue(progressCursor.moveToFirst())
        assertEquals("local:comic1", progressCursor.getString(0))
        assertEquals(5, progressCursor.getInt(1))
        assertEquals(24, progressCursor.getInt(2))
        assertEquals(0, progressCursor.getInt(3))
        assertEquals(1700000050000L, progressCursor.getLong(4))
        progressCursor.close()

        val folderCursor = dbV2.query("SELECT folder_uri, display_name, added_at_epoch_millis, last_scanned_at_epoch_millis FROM library_folders WHERE folder_uri = 'content://com.android.externalstorage.documents/tree/comics'")
        assertTrue(folderCursor.moveToFirst())
        assertEquals("content://com.android.externalstorage.documents/tree/comics", folderCursor.getString(0))
        assertEquals("Local Comics", folderCursor.getString(1))
        assertEquals(1700000000000L, folderCursor.getLong(2))
        assertEquals(1700000010000L, folderCursor.getLong(3))
        folderCursor.close()

        dbV2.execSQL(
            """
            INSERT INTO drive_account (id, email, signed_in_at_epoch_millis, changes_page_token)
            VALUES (1, 'reader@example.com', 1700000000000, 'start-token-xyz')
            """.trimIndent()
        )
        val accountCursor = dbV2.query("SELECT id, email, signed_in_at_epoch_millis, changes_page_token FROM drive_account WHERE id = 1")
        assertTrue(accountCursor.moveToFirst())
        assertEquals(1, accountCursor.getInt(0))
        assertEquals("reader@example.com", accountCursor.getString(1))
        assertEquals(1700000000000L, accountCursor.getLong(2))
        assertEquals("start-token-xyz", accountCursor.getString(3))
        accountCursor.close()

        dbV2.execSQL(
            """
            INSERT INTO drive_folders (folder_id, display_name, added_at_epoch_millis, last_scanned_at_epoch_millis, drive_id)
            VALUES ('drive-folder-123', 'Drive Comics', 1700000000000, 1700000020000, 'shared-drive-abc')
            """.trimIndent()
        )
        val driveFolderCursor = dbV2.query("SELECT folder_id, display_name, added_at_epoch_millis, last_scanned_at_epoch_millis, drive_id FROM drive_folders WHERE folder_id = 'drive-folder-123'")
        assertTrue(driveFolderCursor.moveToFirst())
        assertEquals("drive-folder-123", driveFolderCursor.getString(0))
        assertEquals("Drive Comics", driveFolderCursor.getString(1))
        assertEquals(1700000000000L, driveFolderCursor.getLong(2))
        assertEquals(1700000020000L, driveFolderCursor.getLong(3))
        assertEquals("shared-drive-abc", driveFolderCursor.getString(4))
        driveFolderCursor.close()

        dbV2.close()
    }

    @Test
    fun migrate2To3PreservesExistingDataSeedsDefaultLibrariesAndAddsLibraryIdColumns() {
        val dbV2 = helper.createDatabase(TEST_DB, 2)

        dbV2.execSQL(
            """
            INSERT INTO comics (
                comic_key, title, file_name, format, source_type, availability,
                size_bytes, last_modified_epoch_millis, page_count, local_uri, folder_uri
            ) VALUES (
                'local:comic2', 'Batman #1', 'batman_01.cbz', 'CBZ',
                'LOCAL_SAF', 'LOCAL', 2048576, 1700000000000, 32,
                'content://com.android.externalstorage.documents/tree/comics/batman_01.cbz',
                'content://com.android.externalstorage.documents/tree/comics'
            )
            """.trimIndent()
        )

        dbV2.execSQL(
            """
            INSERT INTO library_folders (
                folder_uri, display_name, added_at_epoch_millis, last_scanned_at_epoch_millis
            ) VALUES (
                'content://com.android.externalstorage.documents/tree/comics', 'Local Comics', 1700000000000, 1700000010000
            )
            """.trimIndent()
        )

        dbV2.execSQL(
            """
            INSERT INTO drive_folders (
                folder_id, display_name, added_at_epoch_millis, last_scanned_at_epoch_millis, drive_id
            ) VALUES (
                'drive-folder-456', 'Drive Comics', 1700000000000, 1700000020000, 'shared-drive-xyz'
            )
            """.trimIndent()
        )

        dbV2.close()

        val dbV3 = helper.runMigrationsAndValidate(TEST_DB, 3, true, ComixMigrations.MIGRATION_2_3)

        val libraryCursor = dbV3.query("SELECT library_id, name, default_reading_direction, created_at_epoch_millis, is_built_in FROM libraries ORDER BY created_at_epoch_millis ASC")
        assertTrue(libraryCursor.moveToFirst())
        assertEquals("comics", libraryCursor.getString(0))
        assertEquals("Comics", libraryCursor.getString(1))
        assertEquals("LEFT_TO_RIGHT", libraryCursor.getString(2))
        assertEquals(0L, libraryCursor.getLong(3))
        assertEquals(1, libraryCursor.getInt(4))

        assertTrue(libraryCursor.moveToNext())
        assertEquals("manga", libraryCursor.getString(0))
        assertEquals("Manga", libraryCursor.getString(1))
        assertEquals("RIGHT_TO_LEFT", libraryCursor.getString(2))
        assertEquals(1L, libraryCursor.getLong(3))
        assertEquals(1, libraryCursor.getInt(4))
        libraryCursor.close()

        val comicCursor = dbV3.query("SELECT comic_key, title, library_id FROM comics WHERE comic_key = 'local:comic2'")
        assertTrue(comicCursor.moveToFirst())
        assertEquals("local:comic2", comicCursor.getString(0))
        assertEquals("Batman #1", comicCursor.getString(1))
        assertEquals("comics", comicCursor.getString(2))
        comicCursor.close()

        val folderCursor = dbV3.query("SELECT folder_uri, display_name, library_id FROM library_folders WHERE folder_uri = 'content://com.android.externalstorage.documents/tree/comics'")
        assertTrue(folderCursor.moveToFirst())
        assertEquals("content://com.android.externalstorage.documents/tree/comics", folderCursor.getString(0))
        assertEquals("Local Comics", folderCursor.getString(1))
        assertEquals("comics", folderCursor.getString(2))
        folderCursor.close()

        val driveFolderCursor = dbV3.query("SELECT folder_id, display_name, library_id FROM drive_folders WHERE folder_id = 'drive-folder-456'")
        assertTrue(driveFolderCursor.moveToFirst())
        assertEquals("drive-folder-456", driveFolderCursor.getString(0))
        assertEquals("Drive Comics", driveFolderCursor.getString(1))
        assertEquals("comics", driveFolderCursor.getString(2))
        driveFolderCursor.close()

        dbV3.close()
    }

    @Test
    fun migrate1To3ChainSucceeds() {
        val dbV1 = helper.createDatabase(TEST_DB, 1)

        dbV1.execSQL(
            """
            INSERT INTO comics (
                comic_key, title, file_name, format, source_type, availability,
                size_bytes, last_modified_epoch_millis, page_count, local_uri, folder_uri
            ) VALUES (
                'local:comic1', 'Amazing Spider-Man #1', 'spiderman_01.cbz', 'CBZ',
                'LOCAL_SAF', 'LOCAL', 1048576, 1700000000000, 24,
                'content://com.android.externalstorage.documents/tree/comics/spiderman_01.cbz',
                'content://com.android.externalstorage.documents/tree/comics'
            )
            """.trimIndent()
        )

        dbV1.close()

        val dbV3 = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            ComixMigrations.MIGRATION_1_2,
            ComixMigrations.MIGRATION_2_3
        )

        val comicCursor = dbV3.query("SELECT comic_key, title, library_id FROM comics WHERE comic_key = 'local:comic1'")
        assertTrue(comicCursor.moveToFirst())
        assertEquals("local:comic1", comicCursor.getString(0))
        assertEquals("Amazing Spider-Man #1", comicCursor.getString(1))
        assertEquals("comics", comicCursor.getString(2))
        comicCursor.close()

        val libraryCursor = dbV3.query("SELECT COUNT(*) FROM libraries")
        assertTrue(libraryCursor.moveToFirst())
        assertEquals(2, libraryCursor.getInt(0))
        libraryCursor.close()

        dbV3.close()
    }
}
