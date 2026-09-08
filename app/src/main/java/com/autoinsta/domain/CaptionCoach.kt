package com.autoinsta.domain

/**
 * What the owner said before any suggestion is made.
 *
 * The order matters: these two answers are collected **first**, deliberately, so the
 * owner's own words about the piece exist before a model has said anything. A coach that
 * speaks first is a ghostwriter.
 */
data class CoachAnswers(
    /** "What was hard about this one?" */
    val whatWasHard: String = "",
    /** "What's it about, in three words?" */
    val whatItsAbout: String = "",
) {
    val hasSomething: Boolean
        get() = whatWasHard.isNotBlank() || whatItsAbout.isNotBlank()
}

/** One title, and — the point — where it came from. */
data class TitleSuggestion(
    val text: String,
    /** Which of [CaptionCoach.TITLE_SOURCES] this used. */
    val source: String,
    /** One short line on what the move is, so the method is learnable. */
    val why: String,
)

/** A caption in the three parts it should have, so the shape is visible. */
data class CaptionDraft(
    val hook: String,
    val process: String,
    val invitation: String,
) {
    /** How it reads once written out. */
    fun asText(): String = listOf(hook, process, invitation)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
}

/** One hashtag with its job and its rough size. */
data class TagSuggestion(
    val tag: String,
    /** One of [CaptionCoach.TAG_ROLES]. */
    val role: String,
    /** Rough post volume and why it earns a slot. */
    val note: String,
)

data class CoachSuggestions(
    val titles: List<TitleSuggestion>,
    val caption: CaptionDraft,
    val hashtags: List<TagSuggestion>,
)

/**
 * Turns a finished piece plus the owner's own words into title options, a caption draft
 * and five hashtags — **with the reasoning attached to each**, because the point is to be
 * needed less over time.
 *
 * ## Why this is a coach and not a generator
 * The owner's words: *"I want to learn how to write them myself so naming my pieces at
 * least comes from me."* So every suggestion carries where it came from — which of the
 * five title sources, which hashtag role — and the caption is returned in its three parts
 * rather than as a finished block. The method is the deliverable; the words are a sample.
 *
 * The teaching content mirrors `docs/manual/captions-and-hashtags.md`. If that file and
 * this prompt disagree, the manual is the source of truth and this should be corrected.
 *
 * Pure: prompt building and response parsing only. Nothing here calls the network, so the
 * part that decides *what good looks like* is unit-testable without an API key.
 */
object CaptionCoach {

    /** The five places a title can come from. Order is the order to try them in. */
    val TITLE_SOURCES = listOf(
        "the feeling",
        "a specific detail",
        "time or place",
        "what it almost was",
        "borrowed language",
    )

    /** The five hashtag slots: 2 niche community, 2 topic, 1 flexible. */
    val TAG_ROLES = listOf("niche community", "topic", "flexible")

    /** How many titles to offer. Three is the "never use your first title" rule. */
    const val TITLE_COUNT = 3

    /** How many past captions to send as voice reference. */
    const val VOICE_SAMPLE_SIZE = 8

    /**
     * The rules the coach works to.
     *
     * Written as instruction rather than description because it is a system prompt, and
     * kept in one place so the teaching can be corrected without touching any wiring.
     */
    fun systemPrompt(tagLimit: Int = PublishPolicy.MAX_HASHTAGS): String = """
        You are helping a digital artist title and caption their own work. They have said
        plainly that they want to LEARN to do this themselves, not have it done for them.
        Every suggestion you make must therefore teach the move it used.

        NAMING A PIECE
        A title's job is not to describe the image — the image already did that. It adds
        what the image cannot say, and makes someone look twice.

        LENGTH: one to three words. Four at the very outside. A title that reads as a
        sentence or an offhand remark is not a title. Simplicity is not a smaller version
        of a good title; it is most of what makes it one.

        CONCRETE BEATS ABSTRACT: a real noun outlasts a mood. Reach for an object, a
        number, a place, an hour. A feeling is what a title should produce in someone, not
        what it should announce.

        Offer exactly $TITLE_COUNT titles, each from a DIFFERENT source below, and name
        which source you used:
        - the feeling — never as an adjective; find the object or gesture that carries it
        - a specific detail — the small thing most viewers miss
        - time or place — real or invented, specific enough to actually be somewhere
        - what it almost was — the version painted over, the attempt abandoned
        - borrowed language — an idiom, a sign, a technical term, a line people actually
          say. From OUTSIDE the piece.

        Never offer a title that merely describes what is visible. Avoid colour+noun
        ("Blue Mountain"), bare gerunds ("Flowing"), and two feelings joined by "and" or
        "yet" ("Empty Yet Longing") — all three read as placeholders, the last because it
        names the mood instead of finding an image for it.

        NEVER hand the artist's own words back as a title. What they told you is material
        for the CAPTION; a title must be something they have not already said.

        THE CAPTION
        Three parts, in order:
        1. hook — one line that is NOT about the art: a thought, a confession, a question.
        2. process — one concrete detail about making it: what was hard, what changed,
           what was nearly abandoned. This is the only part nobody else could write.
        3. invitation — something a reader can answer in three words.
        Build it from the artist's own answers wherever they gave you any. Use their
        phrasing rather than replacing it. Do not describe the artwork back to them.

        HASHTAGS
        Instagram allows exactly $tagLimit per post and they no longer expand reach — they
        classify. Offer $tagLimit, as a portfolio: 2 niche community (aim 50K-500K posts),
        2 topic (aim 100K-1M), 1 flexible (medium, format, or a signature tag). Give each
        one its role and a rough post volume. Never suggest mega-tags like #art or #love —
        they are billions of posts deep and classify nothing.

        VOICE
        If past captions are provided, match their register, length and punctuation habits.
        A suggestion that does not sound like them is worse than no suggestion.

        Be concrete and brief. No preamble, no praise of the artwork.
    """.trimIndent()

    /**
     * The per-post request.
     *
     * The artist's answers come first in the text for a reason: they are the material,
     * and anything the model adds is meant to be built on top of them.
     */
    fun userPrompt(
        answers: CoachAnswers,
        pastCaptions: List<String> = emptyList(),
    ): String = buildString {
        appendLine("Here is the finished piece.")
        appendLine()

        if (answers.hasSomething) {
            appendLine("The artist's own words about it:")
            if (answers.whatWasHard.isNotBlank()) {
                appendLine("- What was hard about it: ${answers.whatWasHard.trim()}")
            }
            if (answers.whatItsAbout.isNotBlank()) {
                appendLine("- What it's about: ${answers.whatItsAbout.trim()}")
            }
        } else {
            // Not an error. Some days there is nothing to say yet, and the coach should
            // still work — it just leans on the image alone and says less confidently.
            appendLine("The artist did not add any notes this time, so work from the")
            appendLine("image alone and keep the caption's process line tentative.")
        }

        val voice = pastCaptions.filter { it.isNotBlank() }.take(VOICE_SAMPLE_SIZE)
        if (voice.isNotEmpty()) {
            appendLine()
            appendLine("Past captions of theirs, for voice only — do not reuse their content:")
            voice.forEach { appendLine("---") ; appendLine(it.trim()) }
        }
    }.trim()

    /**
     * The response shape, as JSON Schema.
     *
     * Structured output rather than parsing prose: the labels (`source`, `role`) are the
     * teaching, so they cannot be optional or free-form. A model that answers in a
     * paragraph has not helped.
     *
     * **Array lengths cannot be constrained here at all.** Structured output rejects both
     * `minItems` above 1 and `maxItems` outright, each with its own HTTP 400 and no
     * suggestion whatsoever. Verified against the live API on 2026-09-08 — twice, because
     * removing the first one revealed the second.
     *
     * So the counts are asked for in [systemPrompt] and enforced in code on the way out
     * ([titlesToOffer], [tagsAsText]), never assumed. What the schema still guarantees is
     * the shape of each *item* — and that is the part that matters, because `source` and
     * `role` are the teaching and must never be optional or free-form.
     */
    fun responseSchema(): String = """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["titles", "caption", "hashtags"],
          "properties": {
            "titles": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["text", "source", "why"],
                "properties": {
                  "text": { "type": "string" },
                  "source": { "type": "string", "enum": [${TITLE_SOURCES.joinToString(", ") { "\"$it\"" }}] },
                  "why": { "type": "string" }
                }
              }
            },
            "caption": {
              "type": "object",
              "additionalProperties": false,
              "required": ["hook", "process", "invitation"],
              "properties": {
                "hook": { "type": "string" },
                "process": { "type": "string" },
                "invitation": { "type": "string" }
              }
            },
            "hashtags": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["tag", "role", "note"],
                "properties": {
                  "tag": { "type": "string" },
                  "role": { "type": "string", "enum": [${TAG_ROLES.joinToString(", ") { "\"$it\"" }}] },
                  "note": { "type": "string" }
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * The titles worth showing.
     *
     * The schema cannot cap the array (see [responseSchema]), so the cap lives here. Blank
     * entries and repeats are dropped first — three options that include a duplicate is
     * really two, and the whole point is having a real choice to make.
     */
    fun titlesToOffer(suggestions: List<TitleSuggestion>): List<TitleSuggestion> = suggestions
        .filter { it.text.isNotBlank() }
        .distinctBy { it.text.trim().lowercase() }
        .take(TITLE_COUNT)

    /**
     * The caption field after the owner accepts a title and/or the draft.
     *
     * **Additive, never destructive.** Whatever is already in the field is kept and the
     * accepted text goes underneath it. This is the same rule [HashtagSet.merge] follows
     * for saved sets, and for the same reason: replacing is fine the first time and
     * destructive every time after, with no undo — type two lines, open the coach out of
     * curiosity, and they are gone.
     *
     * The title leads because that is where a piece's name belongs; everything after it is
     * meant to be edited.
     */
    fun captionWith(
        existing: String,
        title: String? = null,
        draft: CaptionDraft? = null,
    ): String {
        val addition = listOfNotNull(
            title?.trim()?.takeIf { it.isNotEmpty() },
            draft?.asText()?.takeIf { it.isNotEmpty() },
        ).joinToString("\n\n")

        val kept = existing.trim()
        return when {
            // Accepting nothing must leave the field byte-for-byte alone — not even trimmed.
            addition.isEmpty() -> existing
            kept.isEmpty() -> addition
            else -> "$kept\n\n$addition"
        }
    }

    /**
     * The hashtags as they would go in the field.
     *
     * Trimmed to the platform limit here rather than trusting the model to have obeyed
     * `maxItems` — a suggestion that quietly puts the post over the cap would undo the
     * whole point of showing a count.
     */
    fun tagsAsText(
        suggestions: List<TagSuggestion>,
        tagLimit: Int = PublishPolicy.MAX_HASHTAGS,
    ): String = suggestions
        .map { it.tag.trim() }
        .filter { it.startsWith("#") && it.length > 1 }
        .distinctBy { it.lowercase() }
        .take(tagLimit)
        .joinToString(" ")
}
