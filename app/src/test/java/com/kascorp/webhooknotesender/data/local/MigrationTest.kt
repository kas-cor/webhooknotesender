package com.kascorp.webhooknotesender.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @Test
    fun `migration 3 to 4 adds compress columns with correct defaults`() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration_test_v3"
        context.deleteDatabase(dbName)

        val factory = FrameworkSQLiteOpenHelperFactory()

        val v3Configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE profiles (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            type TEXT NOT NULL,
                            prompt TEXT NOT NULL,
                            url TEXT NOT NULL,
                            bearer_token TEXT
                        )
                    """)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val v3Helper = factory.create(v3Configuration)
        val db = v3Helper.writableDatabase

        // Verify DB is at version 3
        assertEquals("Database should be at version 3 before migration", 3, db.version)

        // Insert a profile matching v3 schema
        db.execSQL("""
            INSERT INTO profiles (id, name, type, prompt, url, bearer_token)
            VALUES (1, 'Test Profile', 'image', 'Test prompt', 'https://example.com', NULL)
        """)

        // Run the 3→4 migration
        AppDatabase.MIGRATION_3_4.migrate(db)

        // Verify new columns exist with correct default values
        val cursor = db.query("SELECT compress_enabled, compression_quality FROM profiles WHERE id = 1")
        cursor.moveToFirst()
        assertEquals("compress_enabled should default to 1 (true)", 1, cursor.getInt(0))
        assertEquals("compression_quality should default to 70", 70, cursor.getInt(1))
        cursor.close()
        db.close()
    }
}
