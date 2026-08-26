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
    fun `cropping a tall image uses an explicit window, not automatic gravity`() {
        // This replaced an earlier assertion expecting c_fill,g_auto. Letting Cloudinary
        // guess the subject is wrong here: the owner positions the frame themselves, and
        // g_auto would silently override that choice.
        val t = MediaFit.transformationFor(1080, 1920, Mode.CROP)
        assertTrue("crops to an exact rectangle", t.contains("c_crop"))
        assertFalse("must not let Cloudinary pick for them", t.contains("g_auto"))
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

    // ── Which part of the image survives a crop ────────────────────────────

    @Test
    fun `cropping a tall image keeps full width and slides vertically`() {
        // 1080x1920 at 4:5 keeps 1080x1350.
        val centre = MediaFit.cropWindow(1080, 1920, offset = 0.5f)
        assertEquals(1080, centre.width)
        assertEquals(1350, centre.height)
        assertEquals(0, centre.x)
        assertEquals("centred: (1920-1350)/2 = 285", 285, centre.y)
    }

    @Test
    fun `offset zero keeps the top of a tall image`() {
        assertEquals(0, MediaFit.cropWindow(1080, 1920, offset = 0f).y)
    }

    @Test
    fun `offset one keeps the bottom of a tall image`() {
        val w = MediaFit.cropWindow(1080, 1920, offset = 1f)
        assertEquals(1920 - 1350, w.y)
        assertEquals("the window must not run past the image", 1920, w.y + w.height)
    }

    @Test
    fun `cropping a wide image keeps full height and slides horizontally`() {
        // 3000x1000 at 1.91 keeps 1910x1000.
        val centre = MediaFit.cropWindow(3000, 1000, offset = 0.5f)
        assertEquals(1910, centre.width)
        assertEquals(1000, centre.height)
        assertEquals(0, centre.y)
        assertEquals((3000 - 1910) / 2, centre.x)
    }

    @Test
    fun `an offset outside 0 to 1 is clamped rather than running off the image`() {
        val low = MediaFit.cropWindow(1080, 1920, offset = -5f)
        val high = MediaFit.cropWindow(1080, 1920, offset = 99f)
        assertEquals(0, low.y)
        assertEquals(1920 - 1350, high.y)
    }

    @Test
    fun `an acceptable image has the whole image as its window`() {
        val w = MediaFit.cropWindow(1080, 1080, offset = 0.3f)
        assertEquals(0, w.x)
        assertEquals(0, w.y)
        assertEquals(1080, w.width)
        assertEquals(1080, w.height)
    }

    @Test
    fun `a crop never scales up - the window always fits inside the image`() {
        listOf(1080 to 1920, 3000 to 1000, 500 to 2000, 4000 to 900).forEach { (w, h) ->
            listOf(0f, 0.5f, 1f).forEach { offset ->
                val win = MediaFit.cropWindow(w, h, offset)
                assertTrue("${w}x$h @$offset: width overruns", win.x + win.width <= w)
                assertTrue("${w}x$h @$offset: height overruns", win.y + win.height <= h)
                assertTrue("${w}x$h @$offset: negative origin", win.x >= 0 && win.y >= 0)
            }
        }
    }

    // ── The crop transformation ────────────────────────────────────────────

    @Test
    fun `crop transformation carries the owner's offset, not a re-centred one`() {
        val top = MediaFit.transformationFor(1080, 1920, Mode.CROP, cropOffset = 0f)
        val bottom = MediaFit.transformationFor(1080, 1920, Mode.CROP, cropOffset = 1f)

        assertTrue("top crop should start at y_0", top.contains("y_0"))
        assertTrue("bottom crop should start lower down", bottom.contains("y_570"))
        assertFalse("a re-centred crop would discard the choice", top == bottom)
    }

    @Test
    fun `crop transformation caps the width in a second step`() {
        val t = MediaFit.transformationFor(1080, 1920, Mode.CROP, cropOffset = 0.5f)
        assertTrue("two chained transformations", t.contains("/"))
        assertTrue(t.endsWith("c_limit,w_1440"))
    }

    // ── How much gets lost ─────────────────────────────────────────────────

    @Test
    fun `a 9 by 16 crop loses about 30 percent of the piece`() {
        // 1350/1920 kept = ~70%.
        val lost = MediaFit.croppedAwayFraction(1080, 1920)
        assertTrue("expected ~0.30, was $lost", lost > 0.28f && lost < 0.32f)
    }

    @Test
    fun `an acceptable image loses nothing`() {
        assertEquals(0f, MediaFit.croppedAwayFraction(1080, 1080), 0.001f)
    }

    @Test
    fun `unmeasured dimensions report no loss rather than dividing by zero`() {
        assertEquals(0f, MediaFit.croppedAwayFraction(0, 0), 0.001f)
    }
}
