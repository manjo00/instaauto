package com.autoinsta.domain

import com.autoinsta.domain.model.PostType

/**
 * The rules that decide whether a drafted post is allowed to be scheduled.
 *
 * This is deliberately a **pure** object: no Android imports, no database, no clock
 * of its own. `nowMillis` is passed in rather than read from `System.currentTimeMillis()`
 * so a test can say "pretend it is 3pm" and get a predictable answer. That is what
 * makes these rules verifiable on a laptop in milliseconds instead of on a device.
 */
object PostValidator {

    /** Instagram allows 2–10 items in a carousel. */
    const val CAROUSEL_MIN_ITEMS = 2
    const val CAROUSEL_MAX_ITEMS = 10

    /** How many media files this post type accepts. */
    fun maxMediaFor(postType: PostType): Int = when (postType) {
        PostType.CAROUSEL -> CAROUSEL_MAX_ITEMS
        PostType.SINGLE_IMAGE, PostType.REEL -> 1
    }

    fun minMediaFor(postType: PostType): Int = when (postType) {
        PostType.CAROUSEL -> CAROUSEL_MIN_ITEMS
        PostType.SINGLE_IMAGE, PostType.REEL -> 1
    }

    fun validate(
        postType: PostType,
        mediaCount: Int,
        scheduledAtMillis: Long,
        nowMillis: Long,
    ): PostValidation {
        val media = validateMedia(postType, mediaCount)
        if (media is PostValidation.Invalid) return media

        if (scheduledAtMillis <= nowMillis) {
            return PostValidation.Invalid(PostValidation.Reason.TIME_IN_PAST)
        }
        return PostValidation.Valid
    }

    /**
     * The media rules on their own, for a **queued** post.
     *
     * A queued post has no time to check: it holds a place, and
     * [com.autoinsta.domain.QueuePlanner] supplies the moment. Asking "is this time in the
     * future" of a post that has not been given one yet has no meaningful answer, so the
     * question is not asked rather than being answered with a fudged value.
     */
    fun validateMedia(postType: PostType, mediaCount: Int): PostValidation {
        if (mediaCount == 0) {
            return PostValidation.Invalid(PostValidation.Reason.NO_MEDIA)
        }
        if (mediaCount < minMediaFor(postType)) {
            return PostValidation.Invalid(PostValidation.Reason.CAROUSEL_TOO_FEW)
        }
        if (mediaCount > maxMediaFor(postType)) {
            return PostValidation.Invalid(
                if (postType == PostType.CAROUSEL) PostValidation.Reason.CAROUSEL_TOO_MANY
                else PostValidation.Reason.TOO_MANY_FOR_TYPE
            )
        }
        return PostValidation.Valid
    }
}

/**
 * Outcome of [PostValidator.validate].
 *
 * The failure carries a [Reason] rather than a sentence so the wording stays in the
 * UI layer — domain code shouldn't own user-facing copy (it can't be translated there).
 */
sealed interface PostValidation {

    data object Valid : PostValidation

    data class Invalid(val reason: Reason) : PostValidation

    enum class Reason {
        NO_MEDIA,
        CAROUSEL_TOO_FEW,
        CAROUSEL_TOO_MANY,
        TOO_MANY_FOR_TYPE,
        TIME_IN_PAST,
    }
}
