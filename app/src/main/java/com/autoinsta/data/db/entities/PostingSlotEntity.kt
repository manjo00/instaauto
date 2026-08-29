package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recurring opening in the week — "Monday at 7:00 PM".
 *
 * A flat list of these, rather than a days-by-times grid, because a grid cannot express
 * "Saturday, but at 11am" without giving every other day that time too. The owner sets a
 * rhythm once here; [com.autoinsta.domain.QueuePlanner] turns it into actual dates.
 *
 * Times are **local** — a slot means 7pm wherever the phone is, not a fixed instant.
 */
@Entity(tableName = "posting_slots")
data class PostingSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Matches `java.time.DayOfWeek.getValue()`: 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int,

    /** 0–23, local time. */
    val hourOfDay: Int,

    /** 0–59. */
    val minute: Int,

    /**
     * A slot can be switched off without losing it. Deleting a slot you post at every
     * week just to skip it once, then rebuilding it from memory, is a bad trade.
     */
    val enabled: Boolean = true,
)
