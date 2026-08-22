package com.autoinsta.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real schema migrations.
 *
 * Until v2 the database used `fallbackToDestructiveMigration()`, which quietly **deletes
 * every row** whenever the schema version changes. That is fine while the only data is
 * throwaway, and a data-loss incident the moment it isn't. Adding [MIGRATION_1_2] is
 * what let that setting be removed.
 *
 * Every future schema change needs a migration here plus a test in `MigrationTest`.
 */

/**
 * v1 → v2: posts gained a per-post rule for what to do when their time passed while the
 * device was off.
 *
 * `DEFAULT 'POST_IF_RECENT'` gives every pre-existing row the same sensible behaviour a
 * newly-created post gets, so the upgrade is invisible to anyone with a queue already.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE scheduled_posts " +
                "ADD COLUMN missedPolicy TEXT NOT NULL DEFAULT 'POST_IF_RECENT'"
        )
    }
}

/** Every migration, in order. Passed to the Room builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
