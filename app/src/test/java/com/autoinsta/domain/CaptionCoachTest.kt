package com.autoinsta.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coach's rules, checked without an API key.
 *
 * What is worth pinning here is not phrasing but the things that make it a *coach*: that
 * the artist's own words go in first, that every suggestion is asked to carry its source,
 * and that nothing it returns can push a post over Instagram's hashtag cap.
 */
class CaptionCoachTest {

    // ── The artist's words come first ──────────────────────────────────────

    @Test
    fun `the artist's own answers lead the request`() {
        val prompt = CaptionCoach.userPrompt(
            CoachAnswers(
                whatWasHard = "the sky took four attempts",
                whatItsAbout = "leaving somewhere",
            )
        )

        assertTrue(prompt.contains("the sky took four attempts"))
        assertTrue(prompt.contains("leaving somewhere"))
        // Their words must appear before any voice samples or instructions about them.
        assertTrue(prompt.indexOf("artist's own words") < prompt.indexOf("the sky took"))
    }

    @Test
    fun `no answers is handled, not treated as an error`() {
        // Some days there is nothing to say yet; the coach should still work.
        val prompt = CaptionCoach.userPrompt(CoachAnswers())

        assertTrue(prompt.contains("did not add any notes"))
        assertTrue("it should be told to hold back", prompt.contains("tentative"))
    }

    @Test
    fun `blank answers count as no answers`() {
        assertFalse(CoachAnswers("   ", "").hasSomething)
        assertTrue(CoachAnswers("", "a storm").hasSomething)
    }

    @Test
    fun `past captions are sent for voice, and capped`() {
        val many = (1..20).map { "caption number $it" }
        val prompt = CaptionCoach.userPrompt(CoachAnswers(whatItsAbout = "x"), many)

        assertTrue(prompt.contains("voice only"))
        assertEquals(
            "sending every caption ever written would crowd out the actual request",
            CaptionCoach.VOICE_SAMPLE_SIZE,
            Regex("caption number \\d+").findAll(prompt).count(),
        )
    }

    @Test
    fun `blank past captions are not sent`() {
        val prompt = CaptionCoach.userPrompt(CoachAnswers(whatItsAbout = "x"), listOf("", "  "))

        assertFalse(prompt.contains("voice only"))
    }

    // ── The teaching is in the instructions, not optional ──────────────────

    @Test
    fun `the system prompt names all five title sources`() {
        val prompt = CaptionCoach.systemPrompt()

        CaptionCoach.TITLE_SOURCES.forEach { source ->
            assertTrue("missing title source: $source", prompt.contains(source))
        }
    }

    @Test
    fun `the system prompt carries the real hashtag limit`() {
        // If Instagram changes the cap again, the prompt must move with it rather than
        // teaching a number the app no longer enforces.
        val prompt = CaptionCoach.systemPrompt()

        assertTrue(prompt.contains("exactly ${PublishPolicy.MAX_HASHTAGS} per post"))
        assertFalse("the thirty-tag era is over", prompt.contains("30 per post"))
    }

    @Test
    fun `the system prompt forbids describing the image`() {
        val prompt = CaptionCoach.systemPrompt().lowercase()

        assertTrue(prompt.contains("not to describe"))
        assertTrue(prompt.contains("merely describes"))
    }

    // ── The response shape is where the labels are enforced ────────────────

    @Test
    fun `the schema requires a source on every title`() {
        val schema = CaptionCoach.responseSchema()

        assertTrue(schema.contains("\"required\": [\"text\", \"source\", \"why\"]"))
        CaptionCoach.TITLE_SOURCES.forEach {
            assertTrue("source enum is missing $it", schema.contains("\"$it\""))
        }
    }

    @Test
    fun `the schema requires a role on every hashtag`() {
        val schema = CaptionCoach.responseSchema()

        assertTrue(schema.contains("\"required\": [\"tag\", \"role\", \"note\"]"))
        CaptionCoach.TAG_ROLES.forEach {
            assertTrue("role enum is missing $it", schema.contains("\"$it\""))
        }
    }

    @Test
    fun `the schema asks for exactly three titles and five tags`() {
        val schema = CaptionCoach.responseSchema()

        assertTrue(schema.contains("\"minItems\": ${CaptionCoach.TITLE_COUNT}"))
        assertTrue(schema.contains("\"maxItems\": ${PublishPolicy.MAX_HASHTAGS}"))
    }

    // ── Nothing it returns may break the post ──────────────────────────────

    @Test
    fun `suggested tags are trimmed to the platform limit`() {
        // Trusting maxItems is not enough: a suggestion that silently puts the post over
        // the cap would undo the whole point of showing a count.
        val tooMany = (1..12).map { TagSuggestion("#tag$it", "topic", "note") }

        val text = CaptionCoach.tagsAsText(tooMany)

        assertEquals(PublishPolicy.MAX_HASHTAGS, HashtagSet.count(text))
    }

    @Test
    fun `duplicate suggestions do not waste a slot`() {
        val withDupes = listOf(
            TagSuggestion("#digitalart", "topic", ""),
            TagSuggestion("#DigitalArt", "topic", ""),
            TagSuggestion("#speedpaint", "niche community", ""),
        )

        assertEquals("#digitalart #speedpaint", CaptionCoach.tagsAsText(withDupes))
    }

    @Test
    fun `anything that is not a hashtag is dropped`() {
        val messy = listOf(
            TagSuggestion("digitalart", "topic", ""),
            TagSuggestion("#", "topic", ""),
            TagSuggestion("  #art  ", "topic", ""),
        )

        assertEquals("#art", CaptionCoach.tagsAsText(messy))
    }

    // ── The caption keeps its shape ────────────────────────────────────────

    @Test
    fun `a caption reads as three parts separated by blank lines`() {
        val draft = CaptionDraft(
            hook = "This one fought me for three weeks.",
            process = "The sky took four attempts.",
            invitation = "Which version would you keep?",
        )

        assertEquals(
            "This one fought me for three weeks.\n\n" +
                "The sky took four attempts.\n\n" +
                "Which version would you keep?",
            draft.asText(),
        )
    }

    @Test
    fun `an empty part does not leave a gap`() {
        val draft = CaptionDraft(hook = "A quiet one.", process = "", invitation = "Thoughts?")

        assertEquals("A quiet one.\n\nThoughts?", draft.asText())
    }
}
