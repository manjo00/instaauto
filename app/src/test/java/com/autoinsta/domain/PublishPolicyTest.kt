package com.autoinsta.domain

import com.autoinsta.data.remote.dto.ContainerStatusDto.State
import com.autoinsta.domain.PublishPolicy.CaptionVerdict
import com.autoinsta.domain.PublishPolicy.PollDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules only bite at publish time — post scheduled, owner asleep, media already
 * uploaded. Getting them wrong shows up as a post that silently never went out, so they
 * are pinned here instead.
 */
class PublishPolicyTest {

    // ── Waiting for a video to transcode ───────────────────────────────────

    @Test
    fun `a finished container is published`() {
        assertEquals(PollDecision.ReadyToPublish, PublishPolicy.decidePoll(State.FINISHED, attempt = 1))
    }

    @Test
    fun `an already-published container is treated as done, not published twice`() {
        // Retrying here would put a duplicate on the account.
        assertEquals(PollDecision.ReadyToPublish, PublishPolicy.decidePoll(State.PUBLISHED, attempt = 1))
    }

    @Test
    fun `a container still processing is retried after a minute`() {
        val decision = PublishPolicy.decidePoll(State.IN_PROGRESS, attempt = 1)
        assertTrue(decision is PollDecision.WaitAndRetry)
        assertEquals(60_000L, (decision as PollDecision.WaitAndRetry).delayMillis)
    }

    @Test
    fun `still processing on the last attempt gives up rather than looping forever`() {
        val decision = PublishPolicy.decidePoll(State.IN_PROGRESS, attempt = PublishPolicy.MAX_POLL_ATTEMPTS)
        assertTrue(decision is PollDecision.GiveUp)
        assertTrue((decision as PollDecision.GiveUp).reason.contains("still processing", ignoreCase = true))
    }

    @Test
    fun `an errored container gives up immediately, without burning the remaining attempts`() {
        val decision = PublishPolicy.decidePoll(State.ERROR, attempt = 1)
        assertTrue(decision is PollDecision.GiveUp)
    }

    @Test
    fun `an expired container gives up immediately`() {
        assertTrue(PublishPolicy.decidePoll(State.EXPIRED, attempt = 1) is PollDecision.GiveUp)
    }

    @Test
    fun `an unrecognised status is retried, not treated as failure`() {
        // Meta could add a state; assuming the worst would drop posts that were fine.
        assertTrue(PublishPolicy.decidePoll(State.UNKNOWN, attempt = 1) is PollDecision.WaitAndRetry)
    }

    @Test
    fun `polling never exceeds five minutes of waiting`() {
        val totalWaitMillis = PublishPolicy.POLL_INTERVAL_MILLIS * (PublishPolicy.MAX_POLL_ATTEMPTS - 1)
        assertTrue("Meta's guidance is no more than 5 minutes", totalWaitMillis <= 5 * 60_000L)
    }

    // ── The daily quota ────────────────────────────────────────────────────

    @Test
    fun `room below the limit means go ahead`() {
        assertTrue(PublishPolicy.hasQuotaRemaining(quotaUsage = 10))
    }

    @Test
    fun `at the limit means wait`() {
        assertFalse(PublishPolicy.hasQuotaRemaining(quotaUsage = PublishPolicy.ASSUMED_DAILY_QUOTA))
    }

    @Test
    fun `an unknown quota does not block posting`() {
        // A failed quota check must never become the reason a post doesn't go out.
        assertTrue(PublishPolicy.hasQuotaRemaining(quotaUsage = null))
    }

    @Test
    fun `a quota total reported by Instagram overrides our assumption`() {
        assertTrue(PublishPolicy.hasQuotaRemaining(quotaUsage = 60, quotaTotal = 100))
        assertFalse(PublishPolicy.hasQuotaRemaining(quotaUsage = 60, quotaTotal = 50))
    }

    // ── Captions ───────────────────────────────────────────────────────────

    @Test
    fun `caption and hashtags are joined the way Instagram receives them`() {
        assertEquals("My art\n\n#digitalart", PublishPolicy.combineCaption("My art", "#digitalart"))
    }

    @Test
    fun `an empty half does not leave stray blank lines`() {
        assertEquals("#digitalart", PublishPolicy.combineCaption("", "#digitalart"))
        assertEquals("My art", PublishPolicy.combineCaption("My art", "   "))
        assertEquals("", PublishPolicy.combineCaption("", ""))
    }

    @Test
    fun `an ordinary caption is fine`() {
        assertEquals(CaptionVerdict.Ok, PublishPolicy.checkCaption("New piece", "#digitalart #art"))
    }

    @Test
    fun `the limit applies to caption and hashtags combined, not each separately`() {
        // Each half is under 2200, but together they are over — exactly the case that
        // passes every in-app check and then fails at the API.
        val caption = "x".repeat(1500)
        val hashtags = "y".repeat(1000)
        assertTrue(PublishPolicy.checkCaption(caption, hashtags) is CaptionVerdict.TooLong)
    }

    @Test
    fun `exactly 2200 characters is accepted`() {
        assertEquals(CaptionVerdict.Ok, PublishPolicy.checkCaption("x".repeat(2200), ""))
    }

    @Test
    fun `thirty hashtags is allowed, thirty-one is not`() {
        val thirty = (1..30).joinToString(" ") { "#tag$it" }
        val thirtyOne = (1..31).joinToString(" ") { "#tag$it" }
        assertEquals(CaptionVerdict.Ok, PublishPolicy.checkCaption("", thirty))
        assertTrue(PublishPolicy.checkCaption("", thirtyOne) is CaptionVerdict.TooManyHashtags)
    }

    @Test
    fun `hashtags are counted across caption and hashtags together`() {
        val inCaption = (1..20).joinToString(" ") { "#a$it" }
        val inHashtags = (1..15).joinToString(" ") { "#b$it" }
        assertTrue(PublishPolicy.checkCaption(inCaption, inHashtags) is CaptionVerdict.TooManyHashtags)
    }

    @Test
    fun `twenty mentions is allowed, twenty-one is not`() {
        assertEquals(CaptionVerdict.Ok, PublishPolicy.checkCaption((1..20).joinToString(" ") { "@u$it" }, ""))
        assertTrue(
            PublishPolicy.checkCaption((1..21).joinToString(" ") { "@u$it" }, "")
                is CaptionVerdict.TooManyMentions
        )
    }

    @Test
    fun `a bare hash or at sign is not counted`() {
        assertEquals(CaptionVerdict.Ok, PublishPolicy.checkCaption("Cost # and email @ symbol", ""))
    }

    @Test
    fun `non-latin hashtags are counted - art accounts are not english-only`() {
        val arabic = (1..31).joinToString(" ") { "#فن$it" }
        assertTrue(PublishPolicy.checkCaption("", arabic) is CaptionVerdict.TooManyHashtags)
    }

    @Test
    fun `every rejection explains itself in plain words`() {
        listOf(
            PublishPolicy.checkCaption("x".repeat(2300), ""),
            PublishPolicy.checkCaption("", (1..31).joinToString(" ") { "#t$it" }),
            PublishPolicy.checkCaption((1..21).joinToString(" ") { "@u$it" }, ""),
        ).forEach { verdict ->
            val text = PublishPolicy.explain(verdict)
            assertTrue("no explanation for $verdict", !text.isNullOrBlank())
        }
        assertEquals(null, PublishPolicy.explain(CaptionVerdict.Ok))
    }

    // ── Carousels ──────────────────────────────────────────────────────────

    @Test
    fun `carousel counts follow Instagram's 2 to 10`() {
        assertFalse(PublishPolicy.carouselCountValid(1))
        assertTrue(PublishPolicy.carouselCountValid(2))
        assertTrue(PublishPolicy.carouselCountValid(10))
        assertFalse(PublishPolicy.carouselCountValid(11))
        assertFalse(PublishPolicy.carouselCountValid(0))
    }
}
