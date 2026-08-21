package com.autoinsta.data.media

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MediaFileStoreTest {

    private lateinit var context: Context
    private lateinit var store: MediaFileStore
    private lateinit var sourceDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = MediaFileStore(context)
        sourceDir = File(context.cacheDir, "store-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        sourceDir.deleteRecursively()
        File(context.filesDir, MediaFileStore.MEDIA_DIR_NAME).deleteRecursively()
    }

    private fun sourceUri(name: String, bytes: ByteArray): Uri {
        val file = File(sourceDir, name).apply { writeBytes(bytes) }
        return Uri.parse("file://${file.absolutePath}")
    }

    @Test
    fun importCopiesIntoAppPrivateStorage() = runTest {
        val bytes = "hello".toByteArray()
        val path = store.import(sourceUri("a.jpg", bytes))

        val mediaDir = File(context.filesDir, MediaFileStore.MEDIA_DIR_NAME)
        assertEquals(
            "Imported files must land in the app's own media directory",
            mediaDir.absolutePath,
            File(path).parentFile?.absolutePath,
        )
        assertTrue(store.exists(path))
    }

    @Test
    fun eachImportGetsItsOwnFile() = runTest {
        val first = store.import(sourceUri("a.jpg", "one".toByteArray()))
        val second = store.import(sourceUri("b.jpg", "two".toByteArray()))

        assertFalse("Two imports must not collide", first == second)
        assertEquals("one", File(first).readText())
        assertEquals("two", File(second).readText())
    }

    @Test
    fun importFailsLoudlyWhenTheSourceCannotBeRead() = runTest {
        val missing = Uri.parse("file://${sourceDir.absolutePath}/does-not-exist.jpg")
        try {
            store.import(missing)
            throw AssertionError("Expected an IOException for an unreadable source")
        } catch (expected: IOException) {
            // Correct: the caller gets a real failure instead of a silently empty file.
            // (FileNotFoundException, thrown by openInputStream, is an IOException.)
        }
    }

    @Test
    fun deleteRemovesAnImportedFile() = runTest {
        val path = store.import(sourceUri("a.jpg", "x".toByteArray()))
        store.delete(path)
        assertFalse(store.exists(path))
    }

    @Test
    fun deleteIgnoresPathsOutsideOurMediaDirectory() = runTest {
        val outsider = File(sourceDir, "not-ours.jpg").apply { writeBytes("keep me".toByteArray()) }

        store.delete(outsider.absolutePath)

        assertTrue(
            "A stray database value must never delete files outside app media storage",
            outsider.exists(),
        )
    }

    @Test
    fun deletingAMissingFileIsHarmless() = runTest {
        val path = store.import(sourceUri("a.jpg", "x".toByteArray()))
        store.delete(path)
        store.delete(path) // second call must not throw
        assertFalse(store.exists(path))
    }

    @Test
    fun fileExtensionFollowsTheSourceMimeType() = runTest {
        // file:// URIs resolve their MIME type from the extension, so a .png source
        // should come out as .png rather than the .bin fallback.
        val path = store.import(sourceUri("art.png", "png-bytes".toByteArray()))
        assertTrue("Expected a .png copy, got $path", path.endsWith(".png"))
    }
}
