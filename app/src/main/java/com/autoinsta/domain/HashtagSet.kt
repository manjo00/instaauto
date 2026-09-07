package com.autoinsta.domain

/**
 * Reading and combining hashtag text.
 *
 * ## Why merging, not replacing
 * Applying a preset used to overwrite whatever was in the hashtag field. That is fine the
 * first time and destructive every time after: type two tags specific to the piece, then
 * pick a preset, and the two are gone with no undo. Merging keeps both, and skipping
 * duplicates makes applying the same preset twice a no-op rather than a mess.
 *
 * Pure, so the awkward parts — a tag typed twice in different case, a preset that overlaps
 * another, punctuation between tags — are unit tests rather than things noticed later in
 * a published caption.
 */
object HashtagSet {

    /** Same shape [PublishPolicy] counts, so the two can never disagree about a tag. */
    private val TAG = Regex("""#[\p{L}\p{N}_]+""")

    /** The hashtags in [text], in the order written, keeping the form they were typed in. */
    fun parse(text: String): List<String> = TAG.findAll(text).map { it.value }.toList()

    fun count(text: String): Int = parse(text).size

    /**
     * [existing] plus any tag from [incoming] that is not already there.
     *
     * Comparison ignores case — `#Art` and `#art` are the same tag to Instagram, and
     * keeping both would waste one of the thirty allowed.
     */
    fun merge(existing: String, incoming: String): String {
        val have = parse(existing).map { it.lowercase() }.toMutableSet()
        val added = parse(incoming).filter { have.add(it.lowercase()) }
        if (added.isEmpty()) return existing

        val trimmed = existing.trimEnd()
        return if (trimmed.isEmpty()) added.joinToString(" ")
        else trimmed + " " + added.joinToString(" ")
    }

    /**
     * Tidy a preset's tags on save: one space between them, nothing else kept.
     *
     * Presets are typed once and reused for months, so the stored form is worth
     * normalising — otherwise a stray newline or comma follows every post that uses it.
     */
    fun normalise(text: String): String = parse(text).joinToString(" ")

    /** True when [text] has no tags at all — a preset that would do nothing. */
    fun isEmpty(text: String): Boolean = parse(text).isEmpty()
}
