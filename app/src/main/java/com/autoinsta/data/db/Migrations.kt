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

/**
 * v2 → v3: media items remember their pixel size and how the owner wants them fitted.
 *
 * Existing rows get `widthPx`/`heightPx` of 0, meaning "not measured" — the fitting code
 * treats that as Unknown and falls back to a plain width cap rather than guessing. They
 * also get PAD at centre, which is exactly what Phase 5a did for everything anyway, so
 * nothing already scheduled changes behaviour.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN widthPx INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN heightPx INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN fitMode TEXT NOT NULL DEFAULT 'PAD'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN cropOffset REAL NOT NULL DEFAULT 0.5")
    }
}

/**
 * v3 → v4: the posting queue.
 *
 * Posts gain a timing mode, a place in the pool, and an optional "not before" hold.
 * `DEFAULT 'FIXED'` is the important part — every post that existed before the queue
 * owns its own time, which is exactly how it already behaved, so an upgrade changes
 * nothing for anyone mid-schedule.
 *
 * The settings row is seeded here rather than left to the first write, so the queue has
 * a defined shape (2-hour catch-up, not paused) from the moment the app opens.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE scheduled_posts ADD COLUMN timingMode TEXT NOT NULL DEFAULT 'FIXED'"
        )
        db.execSQL("ALTER TABLE scheduled_posts ADD COLUMN queuePosition INTEGER")
        db.execSQL("ALTER TABLE scheduled_posts ADD COLUMN notBeforeMillis INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `posting_slots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dayOfWeek` INTEGER NOT NULL,
                `hourOfDay` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `queue_settings` (
                `id` INTEGER NOT NULL,
                `catchUpWindowMinutes` INTEGER NOT NULL,
                `paused` INTEGER NOT NULL,
                `resumedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            "INSERT OR IGNORE INTO queue_settings " +
                "(id, catchUpWindowMinutes, paused, resumedAtMillis) VALUES (1, 120, 0, 0)"
        )

        // No slots are seeded. An empty schedule means the queue is simply inert, which
        // is the right state for someone who has not opted into it yet.
    }
}

/** Every migration, in order. Passed to the Room builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
