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
        if (scheduledAtMillis <= nowMillis) {
            return PostValidation.Invalid(PostValidation.Reason.TIME_IN_PAST)
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
