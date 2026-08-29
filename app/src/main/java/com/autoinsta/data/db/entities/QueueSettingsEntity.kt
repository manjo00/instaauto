package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How the queue behaves. Exactly one row, id = 1 — the same single-row pattern as
 * [AccountEntity], for the same reason: these are settings, not a collection.
 */
@Entity(tableName = "queue_settings")
data class QueueSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,

    /**
     * How long a slot stays open after it has passed.
     *
     * An open slot can be filled either by a post the phone failed to publish, or by one
     * added afterwards when the pool was empty at the time. Past the window the slot is
     * gone and everything waits for the next one.
     */
    val catchUpWindowMinutes: Int = 120,

    /** Nothing fires while paused; the pool is left exactly as it is. */
    val paused: Boolean = false,

    /**
     * When the queue was last resumed.
     *
     * Slots that passed *during* a pause must never be caught up afterwards — pausing
     * means "do not post", and honouring one of those retroactively would betray the
     * toggle at the worst possible moment.
     */
    val resumedAtMillis: Long = 0L,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
