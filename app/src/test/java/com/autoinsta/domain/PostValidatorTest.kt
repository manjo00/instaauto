package com.autoinsta.domain

import com.autoinsta.domain.model.PostType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for the scheduling rules. No emulator, no clock — [NOW] stands in
 * for "the current time", which is why these can assert on past/future without ever
 * being flaky.
 */
class PostValidatorTest {

    private companion object {
        /** An arbitrary fixed "now". Real value is irrelevant; only the ordering matters. */
        const val NOW = 1_700_000_000_000L
        const val ONE_HOUR = 60L * 60L * 1000L
    }

    private fun validate(
        postType: PostType,
        mediaCount: Int,
        scheduledAt: Long = NOW + ONE_HOUR,
    ) = PostValidator.validate(postType, mediaCount, scheduledAt, NOW)

    // ── Media count ────────────────────────────────────────────────────────

    @Test
    fun `a post with no media is rejected`() {
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.NO_MEDIA),
            validate(PostType.SINGLE_IMAGE, mediaCount = 0),
        )
    }

    @Test
    fun `a single image post accepts exactly one file`() {
        assertEquals(PostValidation.Valid, validate(PostType.SINGLE_IMAGE, mediaCount = 1))
    }

    @Test
    fun `a single image post rejects two files`() {
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.TOO_MANY_FOR_TYPE),
            validate(PostType.SINGLE_IMAGE, mediaCount = 2),
        )
    }

    @Test
    fun `a reel accepts exactly one file`() {
        assertEquals(PostValidation.Valid, validate(PostType.REEL, mediaCount = 1))
    }

    @Test
    fun `a reel rejects two files`() {
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.TOO_MANY_FOR_TYPE),
            validate(PostType.REEL, mediaCount = 2),
        )
    }

    @Test
    fun `a carousel rejects a single file`() {
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.CAROUSEL_TOO_FEW),
            validate(PostType.CAROUSEL, mediaCount = 1),
        )
    }

    @Test
    fun `a carousel accepts the minimum of two files`() {
        assertEquals(PostValidation.Valid, validate(PostType.CAROUSEL, mediaCount = 2))
    }

    @Test
    fun `a carousel accepts the maximum of ten files`() {
        assertEquals(PostValidation.Valid, validate(PostType.CAROUSEL, mediaCount = 10))
    }

    @Test
    fun `a carousel rejects eleven files`() {
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.CAROUSEL_TOO_MANY),
            validate(PostType.CAROUSEL, mediaCount = 11),
        )
    }

    // ── Scheduled time ─────────────────────────────────────────────────────

    @Test
    fun `a time in the past is rejected`() {
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.TIME_IN_PAST),
            validate(PostType.SINGLE_IMAGE, mediaCount = 1, scheduledAt = NOW - ONE_HOUR),
        )
    }

    @Test
    fun `this exact moment is rejected — a post must be scheduled ahead`() {
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.TIME_IN_PAST),
            validate(PostType.SINGLE_IMAGE, mediaCount = 1, scheduledAt = NOW),
        )
    }

    @Test
    fun `one millisecond into the future is accepted`() {
        assertEquals(
            PostValidation.Valid,
            validate(PostType.SINGLE_IMAGE, mediaCount = 1, scheduledAt = NOW + 1),
        )
    }

    @Test
    fun `media problems are reported before time problems`() {
        // Both wrong: no media AND a past time. The user should be told about the
        // media first, since that is the thing they most likely forgot.
        assertEquals(
            PostValidation.Invalid(PostValidation.Reason.NO_MEDIA),
            validate(PostType.CAROUSEL, mediaCount = 0, scheduledAt = NOW - ONE_HOUR),
        )
    }

    // ── Per-type limits ────────────────────────────────────────────────────

    @Test
    fun `media limits match Instagram's rules`() {
        assertEquals(1, PostValidator.maxMediaFor(PostType.SINGLE_IMAGE))
        assertEquals(1, PostValidator.maxMediaFor(PostType.REEL))
        assertEquals(10, PostValidator.maxMediaFor(PostType.CAROUSEL))
        assertEquals(2, PostValidator.minMediaFor(PostType.CAROUSEL))
    }
}
