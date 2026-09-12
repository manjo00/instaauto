package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing the app did, kept so it can be explained afterwards.
 *
 * ## Why this exists
 * Working out why the queue misbehaved on 2026-09-09 took a database pull and three
 * `dumpsys` calls, and only worked because the tablet happened to be to hand and the
 * evidence happened to survive. Nothing the app did while nobody was watching was
 * recorded anywhere — logcat is a ring buffer that a reboot empties.
 *
 * This is the opposite: a durable, ordered account of alarms, decisions, leases and
 * publishes that can be read back days later.
 *
 * Deliberately not a foreign key to anything. A diagnostic that disappears when the thing
 * it describes is deleted is no use precisely when it is needed.
 */
@Entity(
    tableName = "app_events",
    // Every read is "recent events, newest first", and pruning is by age.
    indices = [Index(value = ["atMillis"])],
)
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val atMillis: Long,

    /** Coarse grouping — see `EventLog.Category`. Stored as text so a new one is additive. */
    val category: String,

    /** What happened, in SCREAMING_SNAKE. Stable enough to grep for. */
    val event: String,

    /** The post it concerns, when it concerns one. */
    val postId: Long? = null,

    /** Free text: the reason, the id, the count. Whatever the next question will need. */
    val detail: String? = null,
)
