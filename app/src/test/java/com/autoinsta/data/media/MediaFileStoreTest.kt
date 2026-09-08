package com.autoinsta.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the coach's downscale.
 *
 * The decode itself needs a real device and cannot be covered here — which is precisely how
 * a bug in it shipped once already (a header-only decode returns null *by contract*, and
 * treating that as failure made every image fail, silently, with nothing thrown to log).
 * What can be pinned down on the JVM is pinned down here.
 */
class MediaFileStoreTest {

    @Test
    fun `a small image is not scaled at all`() {
        assertEquals(1, MediaFileStore.sampleSizeFor(800))
        assertEquals(1, MediaFileStore.sampleSizeFor(1568))
        assertEquals(1, MediaFileStore.sampleSizeFor(3000))
    }

    @Test
    fun `a large export is halved until it is near the target`() {
        // 4000 -> 2000 is the first size at or under twice the 1568 target.
        assertEquals(2, MediaFileStore.sampleSizeFor(4000))
        assertEquals(4, MediaFileStore.sampleSizeFor(8000))
        assertEquals(8, MediaFileStore.sampleSizeFor(16000))
    }

    @Test
    fun `the sample size is always a power of two, which is all inSampleSize accepts`() {
        (1..20000 step 137).forEach { edge ->
            val sample = MediaFileStore.sampleSizeFor(edge)
            assertTrue(
                "$edge gave $sample, which is not a power of two",
                sample > 0 && (sample and (sample - 1)) == 0,
            )
        }
    }

    @Test
    fun `no image is ever left more than twice the target size`() {
        // The guarantee that matters: a 40-megapixel export must not reach the network at
        // full size, and must not be decoded at full size either.
        (1000..20000 step 251).forEach { edge ->
            val decoded = edge / MediaFileStore.sampleSizeFor(edge)
            assertTrue("$edge decoded to $decoded", decoded < 1568 * 2)
        }
    }

    @Test
    fun `a nonsense size does not loop forever or divide by zero`() {
        assertEquals(1, MediaFileStore.sampleSizeFor(0))
        assertEquals(1, MediaFileStore.sampleSizeFor(-5))
    }
}
