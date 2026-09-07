package com.autoinsta.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Combining hashtag text, including the cases that would otherwise be discovered in a
 * caption that has already been published.
 */
class HashtagSetTest {

    // ── Reading ────────────────────────────────────────────────────────────

    @Test
    fun `tags are read in the order they were written`() {
        assertEquals(
            listOf("#digitalart", "#illustration", "#art"),
            HashtagSet.parse("#digitalart #illustration #art"),
        )
    }

    @Test
    fun `words that are not tags are ignored`() {
        assertEquals(listOf("#art"), HashtagSet.parse("a quiet morning #art"))
        assertEquals(0, HashtagSet.count("no tags here at all"))
    }

    @Test
    fun `non-latin tags count`() {
        // The account posts in Arabic as well as English.
        assertEquals(listOf("#فن", "#art"), HashtagSet.parse("#فن #art"))
    }

    @Test
    fun `a bare hash is not a tag`() {
        assertEquals(emptyList<String>(), HashtagSet.parse("# # #"))
        assertTrue(HashtagSet.isEmpty("# not a tag"))
    }

    @Test
    fun `tags survive being separated by anything`() {
        assertEquals(3, HashtagSet.count("#one,#two\n#three"))
    }

    // ── Merging — the reason this exists ───────────────────────────────────

    @Test
    fun `applying a preset keeps what was already typed`() {
        // The whole point: two tags specific to this piece must not be lost.
        val result = HashtagSet.merge("#sunset #wip", "#digitalart #illustration")

        assertEquals("#sunset #wip #digitalart #illustration", result)
    }

    @Test
    fun `applying the same preset twice changes nothing`() {
        val once = HashtagSet.merge("#sunset", "#art #digitalart")
        val twice = HashtagSet.merge(once, "#art #digitalart")

        assertEquals(once, twice)
    }

    @Test
    fun `a tag already present in a different case is not added again`() {
        // Instagram treats these as one tag; keeping both wastes one of the thirty.
        val result = HashtagSet.merge("#Art", "#art #illustration")

        assertEquals("#Art #illustration", result)
    }

    @Test
    fun `merging into an empty field just gives the preset`() {
        assertEquals("#art #digitalart", HashtagSet.merge("", "#art #digitalart"))
        assertEquals("#art", HashtagSet.merge("   ", "#art"))
    }

    @Test
    fun `merging nothing leaves the field untouched`() {
        assertEquals("#sunset", HashtagSet.merge("#sunset", ""))
        assertEquals("#sunset", HashtagSet.merge("#sunset", "no tags here"))
    }

    @Test
    fun `merging does not disturb the prose already in the field`() {
        val result = HashtagSet.merge("a quiet morning #sunset", "#art")

        assertEquals("a quiet morning #sunset #art", result)
    }

    @Test
    fun `a preset that repeats itself is added once`() {
        assertEquals("#art", HashtagSet.merge("", "#art #art #ART"))
    }

    // ── Normalising, for what gets stored ──────────────────────────────────

    @Test
    fun `a saved preset is tidied to single spaces`() {
        assertEquals(
            "#digitalart #illustration",
            HashtagSet.normalise("  #digitalart,\n\n  #illustration  "),
        )
    }

    @Test
    fun `normalising drops anything that is not a tag`() {
        assertEquals("#art", HashtagSet.normalise("my tags: #art please"))
    }

    @Test
    fun `an empty preset is recognised`() {
        assertTrue(HashtagSet.isEmpty(""))
        assertTrue(HashtagSet.isEmpty("just words"))
        assertFalse(HashtagSet.isEmpty("#art"))
    }
}
