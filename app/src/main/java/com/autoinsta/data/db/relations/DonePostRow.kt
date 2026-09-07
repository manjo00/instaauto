package com.autoinsta.data.db.relations

import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType

/**
 * One finished post, as the **Done** tab shows it: what it was, what happened to it last,
 * and whether it took more than one go.
 *
 * Deliberately one row per *post*, not per attempt. A post that failed on Tuesday and
 * succeeded on Wednesday is one piece of art with a slightly bumpy history, and reading it
 * as two entries makes a list of your work look like a log file. [failedAttempts] carries
 * the bumps without spending a row on them.
 */
data class DonePostRow(
    val id: Long,
    val postType: PostType,
    val status: PostStatus,
    val caption: String,
    /** The time it was aiming for. */
    val scheduledAt: Long,
    /** When the last attempt finished — null if somehow nothing was ever recorded. */
    val postedAt: Long?,
    /** Instagram's id for the published post; null when the last attempt failed. */
    val instagramMediaId: String?,
    /** Why the last attempt failed; null when it succeeded. */
    val errorMessage: String?,
    /** How many attempts failed, including ones before an eventual success. */
    val failedAttempts: Int,
    /** First media item, for the thumbnail. Null only if the media rows are gone. */
    val localUri: String?,
    val mediaType: MediaType?,
)
