package com.autoinsta.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun migrate2To3_keepsMediaAndDefaultsTheFittingColumns() {
        val scheduledAt = 1_700_000_000_000L

        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO scheduled_posts
                    (postType, status, caption, hashtags, presetId, scheduledAt, createdAt,
                     workRequestId, missedPolicy)
                VALUES
                    ('CAROUSEL', 'SCHEDULED', 'my art', '#art', NULL, $scheduledAt,
                     $scheduledAt, NULL, 'POST_ANYWAY')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO media_items (postId, mediaType, localUri, cloudinaryUrl, orderIndex)
                VALUES (1, 'IMAGE', '/data/media/one.jpg', NULL, 0),
                       (1, 'IMAGE', '/data/media/two.jpg', NULL, 1)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query(
            "SELECT localUri, orderIndex, widthPx, heightPx, fitMode, cropOffset " +
                "FROM media_items ORDER BY orderIndex"
        ).use { c ->
            assertEquals("both media rows must survive", 2, c.count)
            c.moveToFirst()
            assertEquals("/data/media/one.jpg", c.getString(0))
            assertEquals(0, c.getInt(1))
            // 0x0 means "not measured" — MediaFit reads that as Unknown and falls back to
            // a plain width cap rather than guessing at a shape it cannot see.
            assertEquals(0, c.getInt(2))
            assertEquals(0, c.getInt(3))
            // PAD at centre is exactly what Phase 5a did for everything, so nothing
            // already scheduled changes behaviour on upgrade.
            assertEquals("PAD", c.getString(4))
            assertEquals(0.5f, c.getFloat(5), 0.0001f)
        }

        db.query("SELECT missedPolicy FROM scheduled_posts").use { c ->
            c.moveToFirst()
            assertEquals("the v2 column must be untouched", "POST_ANYWAY", c.getString(0))
        }
    }

    @Test
    fun migrate3To4_keepsPostsAndDefaultsThemToFixedTiming() {
        val scheduledAt = 1_700_000_000_000L

        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO scheduled_posts
                    (postType, status, caption, hashtags, presetId, scheduledAt, createdAt,
                     workRequestId, missedPolicy)
                VALUES
                    ('SINGLE_IMAGE', 'SCHEDULED', 'my art', '#digitalart', NULL, $scheduledAt,
                     $scheduledAt, NULL, 'POST_IF_RECENT')
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query(
            "SELECT caption, scheduledAt, timingMode, queuePosition, notBeforeMillis " +
                "FROM scheduled_posts"
        ).use { c ->
            assertEquals("the existing post must survive the upgrade", 1, c.count)
            c.moveToFirst()
            assertEquals("my art", c.getString(0))
            assertEquals(scheduledAt, c.getLong(1))
            // Everything that existed before the queue owns its own time — which is
            // exactly how it already behaved, so the upgrade changes nothing.
            assertEquals("FIXED", c.getString(2))
            assertTrue("a fixed post is not in the pool", c.isNull(3))
            assertTrue("and holds nothing back", c.isNull(4))
        }
    }

    @Test
    fun migrate3To4_seedsTheQueueSettingsRow() {
        helper.createDatabase(TEST_DB, 3).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT id, catchUpWindowMinutes, paused, resumedAtMillis FROM queue_settings")
            .use { c ->
                assertEquals("the queue must have a defined shape from first launch", 1, c.count)
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
                assertEquals(120, c.getInt(1))
                assertEquals(0, c.getInt(2))
                assertEquals(0L, c.getLong(3))
            }

        db.query("SELECT COUNT(*) FROM posting_slots").use { c ->
            c.moveToFirst()
            assertEquals("no slots are seeded — an empty schedule is simply inert", 0, c.getInt(0))
        }
    }

    @Test
    fun migrate3To4_leavesTheV2AndV3ColumnsAlone() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO scheduled_posts
                    (postType, status, caption, hashtags, presetId, scheduledAt, createdAt,
                     workRequestId, missedPolicy)
                VALUES ('CAROUSEL', 'SCHEDULED', 'c', '#a', NULL, 1, 1, NULL, 'POST_ANYWAY')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO media_items
                    (postId, mediaType, localUri, cloudinaryUrl, orderIndex,
                     widthPx, heightPx, fitMode, cropOffset)
                VALUES (1, 'IMAGE', '/data/media/one.jpg', NULL, 0, 1080, 1350, 'CROP', 0.25)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT missedPolicy FROM scheduled_posts").use { c ->
            c.moveToFirst()
            assertEquals("POST_ANYWAY", c.getString(0))
        }
        db.query("SELECT widthPx, fitMode, cropOffset FROM media_items").use { c ->
            c.moveToFirst()
            assertEquals(1080, c.getInt(0))
            assertEquals("CROP", c.getString(1))
            assertEquals(0.25f, c.getFloat(2), 0.0001f)
        }
    }

    @Test
    fun migrate1To4_worksAsAChain() {
        // Someone upgrading from the very first release skips no steps.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO scheduled_posts
                    (postType, status, caption, hashtags, presetId, scheduledAt, createdAt, workRequestId)
                VALUES ('REEL', 'SCHEDULED', 'oldest post', '#old', NULL, 1, 1, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        )

        db.query("SELECT caption, missedPolicy, timingMode FROM scheduled_posts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("oldest post", c.getString(0))
            assertEquals("POST_IF_RECENT", c.getString(1))
            assertEquals("FIXED", c.getString(2))
        }
        db.query("SELECT COUNT(*) FROM queue_settings").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun migrate1To3_worksAsAChain() {
        // Someone upgrading from the very first release skips no steps.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO scheduled_posts
                    (postType, status, caption, hashtags, presetId, scheduledAt, createdAt, workRequestId)
                VALUES ('SINGLE_IMAGE', 'SCHEDULED', 'old post', '#old', NULL, 1, 1, NULL)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT caption, missedPolicy FROM scheduled_posts").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("old post", c.getString(0))
            assertEquals("POST_IF_RECENT", c.getString(1))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
