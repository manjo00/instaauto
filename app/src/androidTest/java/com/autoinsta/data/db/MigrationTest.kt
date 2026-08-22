package com.autoinsta.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the v1 → v2 upgrade **keeps the user's queue**.
 *
 * Until v2 the database used `fallbackToDestructiveMigration()`, which deletes every row
 * on a version bump. Adding `missedPolicy` would therefore have wiped a real queue. This
 * test is the guarantee that it doesn't — and the template for every future migration.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_keepsExistingPostsAndDefaultsTheNewColumn() {
        val scheduledAt = 1_700_000_000_000L

        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO scheduled_posts
                    (postType, status, caption, hashtags, presetId, scheduledAt, createdAt, workRequestId)
                VALUES
                    ('SINGLE_IMAGE', 'SCHEDULED', 'my art', '#digitalart', NULL, $scheduledAt, $scheduledAt, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT caption, hashtags, scheduledAt, missedPolicy FROM scheduled_posts").use { c ->
            assertEquals("the existing post must survive the upgrade", 1, c.count)
            c.moveToFirst()
            assertEquals("my art", c.getString(0))
            assertEquals("#digitalart", c.getString(1))
            assertEquals(scheduledAt, c.getLong(2))
            assertEquals(
                "pre-existing posts should get the same default a new post gets",
                "POST_IF_RECENT",
                c.getString(3),
            )
        }
    }

    @Test
    fun migrate1To2_handlesAnEmptyQueue() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM scheduled_posts").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
