package com.autoinsta.domain

import com.autoinsta.domain.model.PostType

/**
 * The rules Instagram enforces at publish time, as pure functions.
 *
 * Every one of these fails *after* a post is scheduled — the owner is asleep, the media
 * is already uploaded, and the only evidence is an API error. Checking first turns a
 * mystery into a sentence.
 */
object PublishPolicy {

    // ── Waiting for video to finish processing ─────────────────────────────

    /** Meta's guidance: query a container's status once per minute. */
    const val POLL_INTERVAL_MILLIS: Long = 60_000

    /** …for no more than five minutes. */
    const val MAX_POLL_ATTEMPTS: Int = 5

    /** What to do after one look at a container's status. */
    sealed interface PollDecision {
        /** Not ready. Wait [delayMillis] and look again. */
        data class WaitAndRetry(val delayMillis: Long) : PollDecision

        /** Ready — publish it. */
        data object ReadyToPublish : PollDecision

        /** Give up, with something worth telling the owner. */
        data class GiveUp(val reason: String) : PollDecision
    }

    /**
     * @param state Instagram's `status_code` for the container.
     * @param attempt 1-based; the first look is attempt 1.
     */
    fun decidePoll(
        state: com.autoinsta.data.remote.dto.ContainerStatusDto.State,
        attempt: Int,
    ): PollDecision {
        return when (state) {
            com.autoinsta.data.remote.dto.ContainerStatusDto.State.FINISHED ->
                PollDecision.ReadyToPublish

            // Already published — treat as success rather than trying again, which
            // would post a duplicate.
            com.autoinsta.data.remote.dto.ContainerStatusDto.State.PUBLISHED ->
                PollDecision.ReadyToPublish

            com.autoinsta.data.remote.dto.ContainerStatusDto.State.ERROR ->
                PollDecision.GiveUp("Instagram couldn't process this video.")

            com.autoinsta.data.remote.dto.ContainerStatusDto.State.EXPIRED ->
                PollDecision.GiveUp("Instagram discarded the upload before it was published.")

            com.autoinsta.data.remote.dto.ContainerStatusDto.State.IN_PROGRESS,
            com.autoinsta.data.remote.dto.ContainerStatusDto.State.UNKNOWN -> {
                if (attempt >= MAX_POLL_ATTEMPTS) {
                    PollDecision.GiveUp(
                        "Instagram was still processing this video after " +
                            "${MAX_POLL_ATTEMPTS} minutes."
                    )
                } else {
                    PollDecision.WaitAndRetry(POLL_INTERVAL_MILLIS)
                }
            }
        }
    }

    // ── The rolling publish quota ──────────────────────────────────────────

    /**
     * Meta's docs give 100/24h in one place and 50 in another. The lower number is
     * assumed: over-posting is refused by Instagram, while under-posting merely delays.
     */
    const val ASSUMED_DAILY_QUOTA: Int = 50

    /**
     * Is there room to publish right now?
     *
     * @param quotaUsage what Instagram reports as already used, or null if unknown.
     *        Unknown is treated as "go ahead" — a quota check failing must not become a
     *        reason a post never goes out.
     */
    fun hasQuotaRemaining(quotaUsage: Int?, quotaTotal: Int? = null): Boolean {
        val used = quotaUsage ?: return true
        val total = quotaTotal ?: ASSUMED_DAILY_QUOTA
        return used < total
    }

    // ── What Instagram accepts in a caption ────────────────────────────────

    const val MAX_CAPTION_CHARS = 2200
    const val MAX_HASHTAGS = 30
    const val MAX_MENTIONS = 20

    sealed interface CaptionVerdict {
        data object Ok : CaptionVerdict
        data class TooLong(val chars: Int) : CaptionVerdict
        data class TooManyHashtags(val count: Int) : CaptionVerdict
        data class TooManyMentions(val count: Int) : CaptionVerdict
    }

    /**
     * Caption and hashtags are stored separately but Instagram receives them as one
     * field, so the limits apply to the combination — which is exactly the kind of thing
     * that passes every check in the app and then fails at the API.
     */
    fun checkCaption(caption: String, hashtags: String): CaptionVerdict {
        val combined = combineCaption(caption, hashtags)

        if (combined.length > MAX_CAPTION_CHARS) return CaptionVerdict.TooLong(combined.length)

        val hashtagCount = HASHTAG.findAll(combined).count()
        if (hashtagCount > MAX_HASHTAGS) return CaptionVerdict.TooManyHashtags(hashtagCount)

        val mentionCount = MENTION.findAll(combined).count()
        if (mentionCount > MAX_MENTIONS) return CaptionVerdict.TooManyMentions(mentionCount)

        return CaptionVerdict.Ok
    }

    /** Exactly what gets sent as `caption`. Blank parts are dropped, not joined to whitespace. */
    fun combineCaption(caption: String, hashtags: String): String =
        listOf(caption.trim(), hashtags.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

    fun explain(verdict: CaptionVerdict): String? = when (verdict) {
        CaptionVerdict.Ok -> null
        is CaptionVerdict.TooLong ->
            "Caption and hashtags together are ${verdict.chars} characters. " +
                "Instagram allows $MAX_CAPTION_CHARS."
        is CaptionVerdict.TooManyHashtags ->
            "That's ${verdict.count} hashtags. Instagram allows $MAX_HASHTAGS."
        is CaptionVerdict.TooManyMentions ->
            "That's ${verdict.count} @mentions. Instagram allows $MAX_MENTIONS."
    }

    // ── Carousels ──────────────────────────────────────────────────────────

    /**
     * A carousel is built child-by-child and then assembled, so the count has to be right
     * before any of the uploads happen — discovering it after uploading nine files wastes
     * the owner's data and Instagram's quota.
     */
    fun carouselCountValid(itemCount: Int): Boolean =
        itemCount in PostValidator.CAROUSEL_MIN_ITEMS..PostValidator.CAROUSEL_MAX_ITEMS

    /** How many media items this post type sends to Instagram. */
    fun expectedItemCount(postType: PostType, actual: Int): Int =
        if (postType == PostType.CAROUSEL) actual else 1

    private val HASHTAG = Regex("""#[\p{L}\p{N}_]+""")
    private val MENTION = Regex("""@[A-Za-z0-9._]+""")
}
