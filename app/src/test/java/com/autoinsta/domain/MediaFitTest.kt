package com.autoinsta.domain

import com.autoinsta.domain.MediaFit.Mode
import com.autoinsta.domain.MediaFit.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Instagram silently rejects anything outside 4:5 to 1.91:1, and the rejection lands at
 * publish time rather than at scheduling. These pin the boundaries down so a wrong number
 * shows up here instead of as a post that quietly never went out.
 */
class MediaFitTest {

    // ── The boundaries ─────────────────────────────────────────────────────

    @Test
    fun `a square image is fine`() {
        assertEquals(Verdict.Acceptable, MediaFit.verdictFor(1080, 1080))
    }

    @Test
    fun `exactly 4 to 5 is accepted - the tallest Instagram allows`() {
        assertEquals(Verdict.Acceptable, MediaFit.verdictFor(1080, 1350))
    }

    @Test
    fun `one pixel taller than 4 to 5 is rejected`() {
        assertTrue(MediaFit.verdictFor(1080, 1351) is Verdict.TooTall)
    }

    @Test
    fun `exactly 1_91 to 1 is accepted - the widest Instagram allows`() {
        // 1910x1000 is exactly 1.91
        assertEquals(Verdict.Acceptable, MediaFit.verdictFor(1910, 1000))
    }

    @Test
    fun `wider than 1_91 to 1 is rejected`() {
        assertTrue(MediaFit.verdictFor(2000, 1000) is Verdict.TooWide)
    }

    // ── The shapes an art account actually produces ────────────────────────

    @Test
    fun `a 9 by 16 phone-wallpaper piece is too tall`() {
        val verdict = MediaFit.verdictFor(1080, 1920)
        assertTrue(verdict is Verdict.TooTall)
        assertEquals(0.5625, (verdict as Verdict.TooTall).ratio, 0.0001)
    }

    @Test
    fun `a standard 4 by 5 portrait export is fine`() {
        assertTrue(MediaFit.isAcceptable(1080, 1350))
    }

    @Test
    fun `a wide panorama is too wide`() {
        assertTrue(MediaFit.verdictFor(3000, 1000) is Verdict.TooWide)
    }

    @Test
    fun `a 16 by 9 landscape piece is fine`() {
        // 1.777 sits inside 1.91
        assertTrue(MediaFit.isAcceptable(1920, 1080))
    }

    // ── Nonsense in, no crash out ──────────────────────────────────────────

    @Test
    fun `zero or negative dimensions are unknown rather than a crash`() {
        assertEquals(Verdict.Unknown, MediaFit.verdictFor(0, 0))
        assertEquals(Verdict.Unknown, MediaFit.verdictFor(1080, 0))
        assertEquals(Verdict.Unknown, MediaFit.verdictFor(-5, 100))
    }

    // ── Nearest allowed ratio ──────────────────────────────────────────────

    @Test
    fun `a too-tall image is brought to 4 to 5, not to square`() {
        // The least change that makes it publishable.
        assertEquals(0.8, MediaFit.nearestAllowedRatio(1080, 1920), 0.0001)
    }

    @Test
    fun `a too-wide image is brought to 1_91`() {
        assertEquals(1.91, MediaFit.nearestAllowedRatio(3000, 1000), 0.0001)
    }

    @Test
    fun `an acceptable image keeps its own ratio`() {
        assertEquals(1.0, MediaFit.nearestAllowedRatio(1080, 1080), 0.0001)
    }

    // ── Transformations ────────────────────────────────────────────────────

    @Test
    fun `every transformation forces JPEG - Instagram rejects PNG`() {
        val shapes = listOf(1080 to 1080, 1080 to 1920, 3000 to 1000)
        val modes = Mode.entries
        shapes.forEach { (w, h) ->
            modes.forEach { mode ->
                assertTrue(
                    "f_jpg missing for ${w}x$h $mode",
                    MediaFit.transformationFor(w, h, mode).contains("f_jpg"),
                )
            }
        }
    }

    @Test
    fun `an acceptable image is only width-capped`() {
        val t = MediaFit.transformationFor(1080, 1080, Mode.PAD)
        assertTrue(t.contains("c_limit"))
        assertTrue(t.contains("w_${MediaFit.MAX_WIDTH_PX}"))
        assertFalse("nothing to pad on an acceptable image", t.contains("c_pad"))
    }

    @Test
    fun `padding a tall image targets 4 to 5 and keeps everything`() {
        val t = MediaFit.transformationFor(1080, 1920, Mode.PAD)
        assertTrue("should pad", t.contains("c_pad"))
        assertTrue("should set a background", t.contains("b_"))
        assertTrue(t.contains("w_1440"))
        assertTrue("1440 / 0.8 = 1800", t.contains("h_1800"))
    }

    @Test
    fun `cropping a tall image fills the frame`() {
        val t = MediaFit.transformationFor(1080, 1920, Mode.CROP)
        assertTrue(t.contains("c_fill"))
        assertTrue("should pick the subject, not blindly centre", t.contains("g_auto"))
        assertFalse(t.contains("c_pad"))
    }

    @Test
    fun `as-is never pads or crops even when out of range`() {
        val t = MediaFit.transformationFor(1080, 1920, Mode.AS_IS)
        assertFalse(t.contains("c_pad"))
        assertFalse(t.contains("c_fill"))
        assertTrue(t.contains("c_limit"))
    }

    @Test
    fun `unknown dimensions fall back to a safe width cap`() {
        val t = MediaFit.transformationFor(0, 0, Mode.PAD)
        assertTrue(t.contains("c_limit"))
        assertFalse("cannot pad what we cannot measure", t.contains("c_pad"))
    }

    @Test
    fun `the pad colour is configurable`() {
        val t = MediaFit.transformationFor(1080, 1920, Mode.PAD, padColour = "rgb:101014")
        assertTrue(t.contains("b_rgb:101014"))
    }

    // ── Wording shown to the owner ─────────────────────────────────────────

    @Test
    fun `an acceptable image needs no explanation`() {
        assertEquals(null, MediaFit.explain(Verdict.Acceptable, Mode.PAD))
    }

    @Test
    fun `padding promises nothing is cut off`() {
        val text = MediaFit.explain(Verdict.TooTall(0.56), Mode.PAD).orEmpty()
        assertTrue(text.contains("bars", ignoreCase = true))
        assertTrue(text.contains("nothing is cut off", ignoreCase = true))
    }

    @Test
    fun `leaving a too-tall image as-is warns that the post will fail`() {
        val text = MediaFit.explain(Verdict.TooTall(0.56), Mode.AS_IS).orEmpty()
        assertTrue(text.contains("fail", ignoreCase = true))
    }

    @Test
    fun `cropping says plainly what will be lost`() {
        assertTrue(
            MediaFit.explain(Verdict.TooTall(0.56), Mode.CROP).orEmpty()
                .contains("cropped", ignoreCase = true)
        )
        assertTrue(
            MediaFit.explain(Verdict.TooWide(2.5), Mode.CROP).orEmpty()
                .contains("sides", ignoreCase = true)
        )
    }
}
