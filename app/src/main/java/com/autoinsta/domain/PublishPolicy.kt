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

    /** Meta's guidance for video: query a container's status once per minute. */
    const val POLL_INTERVAL_MILLIS: Long = 60_000

    /** …for no more than five minutes. */
    const val MAX_POLL_ATTEMPTS: Int = 5

    /**
     * An image container only has to be *fetched*, not transcoded — usually a second or
     * two. Polling it once a minute would add a needless minute to every photo.
     */
    const val IMAGE_POLL_INTERVAL_MILLIS: Long = 2_000
    const val IMAGE_MAX_POLL_ATTEMPTS: Int = 15

    /**
     * How often to look at a container, and what running out of looks means.
     *
     * The second part is the important one. For **video**, silence after five minutes is
     * a real failure: transcoding should have finished. For an **image** it is not —
     * publishing anyway is the better bet, because the container is almost certainly fine
     * and a genuine rejection comes back as a retryable error anyway. Treating an
     * unhelpful status endpoint as fatal would turn a working post into a lost one.
     */
    data class PollCadence(
        val intervalMillis: Long,
        val maxAttempts: Int,
        val exhaustionIsFailure: Boolean,
    ) {
        companion object {
            val VIDEO = PollCadence(POLL_INTERVAL_MILLIS, MAX_POLL_ATTEMPTS, exhaustionIsFailure = true)
            val IMAGE = PollCadence(IMAGE_POLL_INTERVAL_MILLIS, IMAGE_MAX_POLL_ATTEMPTS, exhaustionIsFailure = false)
        }
    }

    /** What to do after one look at a container's status. */
    sealed interface PollDecision {
        /** Not ready. Wait [delayMillis] and look again. */
        data class WaitAndRetry(val delayMillis: Long) : PollDecision

        /** Ready — publish it. */
        data object ReadyToPublish : PollDecision

        /**
         * Never confirmed ready, but worth publishing anyway — see [PollCadence].
         * Distinct from [ReadyToPublish] so the difference stays honest in logs and tests.
         */
        data class PublishUnverified(val reason: String) : PollDecision

        /** Give up, with something worth telling the owner. */
        data class GiveUp(val reason: String) : PollDecision
    }

    /**
     * @param state Instagram's `status_code` for the container.
     * @param attempt 1-based; the first look is attempt 1.
     * @param cadence how patient to be, and what running out means.
     */
    fun decidePoll(
        state: com.autoinsta.data.remote.dto.ContainerStatusDto.State,
        attempt: Int,
        cadence: PollCadence = PollCadence.VIDEO,
    ): PollDecision {
        return when (state) {
            com.autoinsta.data.remote.dto.ContainerStatusDto.State.FINISHED ->
                PollDecision.ReadyToPublish

            // Already published — treat as success rather than trying again, which
            // would post a duplicate.
            com.autoinsta.data.remote.dto.ContainerStatusDto.State.PUBLISHED ->
                PollDecision.ReadyToPublish

            com.autoinsta.data.remote.dto.ContainerStatusDto.State.ERROR ->
                PollDecision.GiveUp("Instagram couldn't process this media.")

            com.autoinsta.data.remote.dto.ContainerStatusDto.State.EXPIRED ->
                PollDecision.GiveUp("Instagram discarded the upload before it was published.")

            com.autoinsta.data.remote.dto.ContainerStatusDto.State.IN_PROGRESS,
            com.autoinsta.data.remote.dto.ContainerStatusDto.State.UNKNOWN -> {
                if (attempt < cadence.maxAttempts) {
                    PollDecision.WaitAndRetry(cadence.intervalMillis)
                } else if (cadence.exhaustionIsFailure) {
                    val minutes = cadence.intervalMillis * cadence.maxAttempts / 60_000
                    PollDecision.GiveUp(
                        "Instagram was still processing this media after $minutes minutes."
                    )
                } else {
                    PollDecision.PublishUnverified(
                        "Instagram never confirmed the upload was ready; publishing anyway."
                    )
                }
            }
        }
    }

    /**
     * Does this Meta error body mean "not yet" rather than "never"?
     *
     * This exists because of a real lost post. Instagram answered `media_publish` with
     * **"Media ID is not available"** — its wording for *the container is not ready yet* —
     * as an HTTP 400. Treating every 4xx as permanent meant the worker gave up instead of
     * retrying a few seconds later, and a finished piece silently dropped out of the queue.
     */
    fun isTransientRejection(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        if (TRANSIENT_FLAG.containsMatchIn(body)) return true
        return TRANSIENT_MESSAGES.any { body.contains(it, ignoreCase = true) }
    }

    /** Meta's own marker, when it bothers to send one. */
    private val TRANSIENT_FLAG = Regex(""""is_transient"\s*:\s*true""")

    /** …and the wordings seen in the wild when it doesn't. */
    private val TRANSIENT_MESSAGES = listOf(
        "Media ID is not available",
        "not ready to be published",
        "Please wait a moment",
    )

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
