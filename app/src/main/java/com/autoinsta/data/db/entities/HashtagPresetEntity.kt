package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved, reusable set of hashtags.
 * The user names the preset (e.g. "Digital Art", "Portraits") and can attach it to
 * any scheduled post instead of retyping hashtags each time.
 */
@Entity(tableName = "hashtag_presets")
data class HashtagPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Display name, e.g. "Digital Art". */
    val name: String,

    /**
     * Hashtags as a space-separated string, e.g. "#digitalart #illustration #artwork".
     * Stored raw to preserve the user's exact formatting.
     */
    val hashtags: String,

    /** Row creation time — epoch milliseconds. */
    val createdAt: Long,
)
