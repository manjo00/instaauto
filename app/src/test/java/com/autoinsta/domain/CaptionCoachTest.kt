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
    fun `the system prompt asks for short titles`() {
        // Without a length rule it produced "No idea just the pen" — a remark, not a name.
        val prompt = CaptionCoach.systemPrompt()

        assertTrue(prompt.contains("one to four words"))
    }

    @Test
    fun `the artist's words must be transformed — neither quoted nor ignored`() {
        // Two failures, one on each side of this rule, both seen for real:
        //   quoted  → "No idea just the pen"
        //   ignored → "Wand Up" / "Second Volley" for a piece whose whole subject was
        //             an unnamed relationship the owner had described in the answers.
        // The first version of this rule only banned quoting, and bought the second bug.
        assertTrue(flatPrompt().contains("TRANSFORMED, NEVER QUOTED"))
        assertTrue(
            "ignoring their answers must be called out as the worse failure",
            flatPrompt().contains("Ignoring what they said is the worse failure"),
        )
    }

    /**
     * The prompt with its line wrapping flattened.
     *
     * A phrase that happens to wrap contains a newline, not a space, so asserting on it
     * raw fails the moment a sentence is rewrapped — which tests the formatting rather
     * than the rule. Both of the assertions above were written that way first and failed
     * for exactly that reason.
     */
    private fun flatPrompt(): String =
        CaptionCoach.systemPrompt().replace(Regex("\\s+"), " ")

    @Test
    fun `the prompt names both ways of being dull`() {
        // Vague and literal are different faults and the prompt has to forbid both — a
        // rule against only one of them just pushes suggestions into the other.
        val prompt = CaptionCoach.systemPrompt()

        assertTrue(prompt.contains("VAGUE"))
        assertTrue(prompt.contains("LITERAL"))
        assertTrue("the hundred-pictures test is the one that matters", prompt.contains("hundred other pictures"))
    }

    @Test
    fun `the system prompt rejects mood pairs, not just colour-noun`() {
        // "Empty Yet Longing" is the same placeholder shape as "Blue Mountain" — it names
        // the mood instead of finding an image for it.
        val prompt = CaptionCoach.systemPrompt()

        assertTrue(prompt.contains("Blue Mountain"))
        assertTrue(prompt.contains("Empty Yet Longing"))
    }

    @Test
    fun `the system prompt forbids describing the image`() {
        val prompt = flatPrompt().lowercase()

        assertTrue(prompt.contains("not to describe"))
        // Labelling something in the frame is describing with fewer words — the "literal"
        // half of the dullness rule, and how "Wand Up" got through.
        assertTrue(prompt.contains("naming something visible in the frame"))
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
    fun `the schema does not try to constrain array length`() {
        // Both of these come back HTTP 400 from structured output, with no suggestion at
        // all — verified against the live API on 2026-09-08:
        //   "minItems" above 1 → "values other than 0 or 1 are not supported"
        //   "maxItems" at all  → "property 'maxItems' is not supported"
        // The counts are asked for in the system prompt and enforced by titlesToOffer and
        // tagsAsText instead.
        //
        // This replaces a test asserting minItems == TITLE_COUNT and maxItems == the tag
        // limit — it pinned a schema the API rejects outright.
        val schema = CaptionCoach.responseSchema()

        assertFalse("maxItems is rejected outright", schema.contains("maxItems"))
        assertEquals(
            "minItems must be 0 or 1",
            emptyList<Int>(),
            Regex("\"minItems\": (\\d+)")
                .findAll(schema)
                .map { it.groupValues[1].toInt() }
                .filter { it > 1 }
                .toList(),
        )
    }

    @Test
    fun `the count the schema cannot enforce is enforced in code`() {
        val tooMany = (1..9).map { TitleSuggestion("Title $it", "the feeling", "why") }

        assertEquals(CaptionCoach.TITLE_COUNT, CaptionCoach.titlesToOffer(tooMany).size)
    }

    @Test
    fun `a repeated title does not use up one of the three choices`() {
        val withDupe = listOf(
            TitleSuggestion("First Light", "the feeling", ""),
            TitleSuggestion("first light", "a specific detail", ""),
            TitleSuggestion("", "time or place", ""),
            TitleSuggestion("The 5:40", "time or place", ""),
        )

        assertEquals(
            listOf("First Light", "The 5:40"),
            CaptionCoach.titlesToOffer(withDupe).map { it.text },
        )
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
            materials = "Procreate, ~4 hours.",
            process = "Three passes on the light before it stopped looking lit from behind.",
            decision = "The second ridge was a line I couldn't undo, so it stayed.",
        )

        assertEquals(
            "Procreate, ~4 hours.\n\n" +
                "Three passes on the light before it stopped looking lit from behind.\n\n" +
                "The second ridge was a line I couldn't undo, so it stayed.",
            draft.asText(),
        )
    }

    @Test
    fun `an empty part does not leave a gap`() {
        // The common case: no tool or duration was given, so materials is deliberately blank.
        val draft = CaptionDraft(materials = "", process = "Four attempts.", decision = "Kept the ridge.")

        assertEquals("Four attempts.\n\nKept the ridge.", draft.asText())
    }

    // ── The register the owner rejected, pinned so it cannot drift back ────

    @Test
    fun `the caption is specified as a wall label, not a diary`() {
        // "am not the type who talk in social media... i hate talking like this its just
        // not me." The confessional hook is gone and the ban is explicit.
        val prompt = CaptionCoach.systemPrompt()

        assertTrue(prompt.contains("GALLERY LABEL, NOT A DIARY"))
        assertFalse("the confessional hook is what they rejected", prompt.contains("a confession"))
    }

    @Test
    fun `the prompt bans engagement bait and mood talk by name`() {
        val prompt = CaptionCoach.systemPrompt()

        assertTrue(prompt.contains("woke up and felt like painting"))
        assertTrue(prompt.contains("questions to the reader"))
    }

    @Test
    fun `materials are never invented when the artist did not say`() {
        // A guessed tool or duration is a fabricated fact printed under their name.
        val prompt = CaptionCoach.userPrompt(CoachAnswers(whatWasHard = "the light"))

        assertTrue(prompt.contains("leave `materials` EMPTY"))
    }

    @Test
    fun `materials reach the request when the artist does say`() {
        val prompt = CaptionCoach.userPrompt(CoachAnswers(madeWith = "Procreate, 4 hours"))

        assertTrue(prompt.contains("Procreate, 4 hours"))
        assertFalse(prompt.contains("leave `materials` EMPTY"))
    }

    @Test
    fun `saying only what it was made with still counts as saying something`() {
        assertTrue(CoachAnswers(madeWith = "Procreate").hasSomething)
        assertFalse(CoachAnswers(madeWith = "   ").hasSomething)
    }

    // ── Accepting a suggestion must never eat what was typed ───────────────

    @Test
    fun `what is already written survives accepting a suggestion`() {
        // The same lesson as saved hashtag sets: replacing is fine once and destructive
        // every time after, with no undo.
        val typed = "Started this on the train home."

        val result = CaptionCoach.captionWith(
            existing = typed,
            title = "Departures in Amber",
            draft = CaptionDraft("A hook.", "A process line.", "A question?"),
        )

        assertTrue("their own words are gone", result.startsWith(typed))
        assertTrue(result.contains("Departures in Amber"))
    }

    @Test
    fun `accepting nothing leaves the field untouched`() {
        // Not even trimmed — an unfinished line with a trailing space is still theirs.
        val typed = "half a thought  "

        assertEquals(typed, CaptionCoach.captionWith(typed, title = null, draft = null))
        assertEquals(typed, CaptionCoach.captionWith(typed, title = "   "))
    }

    @Test
    fun `a title alone fills an empty caption`() {
        assertEquals("Departures in Amber", CaptionCoach.captionWith("", title = "Departures in Amber"))
    }

    @Test
    fun `the title leads, because that is where a name goes`() {
        val result = CaptionCoach.captionWith(
            existing = "",
            title = "First Light",
            draft = CaptionDraft("A hook.", "", ""),
        )

        assertEquals("First Light\n\nA hook.", result)
    }

    @Test
    fun `a draft alone does not leave a leading blank line`() {
        val result = CaptionCoach.captionWith(
            existing = "",
            draft = CaptionDraft("A hook.", "", "A question?"),
        )

        assertEquals("A hook.\n\nA question?", result)
    }
}
