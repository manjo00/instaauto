package com.autoinsta.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The connected Instagram Creator/Business account.
 * v1 supports exactly one account — id is always 1.
 *
 * The actual access token is stored in EncryptedSharedPreferences (TokenStore),
 * NOT here, to keep it out of Room's SQLite file.
 */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey
    val id: Int = 1,

    /** Instagram user ID (e.g. "17841400000000000"). Obtained via Graph API. */
    val igUserId: String,

    /** Instagram username (e.g. "your_art_account"). For display only. */
    val username: String,

    /** Profile picture URL. Nullable — not required for posting. */
    val profilePictureUrl: String? = null,

    /** When the account was connected — epoch milliseconds. */
    val connectedAt: Long,

    /**
     * When the long-lived token expires — epoch milliseconds.
     * App should refresh when within 7 days of this time.
     */
    val tokenExpiresAt: Long,
)
