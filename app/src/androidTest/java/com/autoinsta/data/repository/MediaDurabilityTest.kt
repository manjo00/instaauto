package com.autoinsta.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoinsta.data.db.AppDatabase
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.media.MediaFileStore
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * # Regression test for the stale-media bug
 *
 * Android's Photo Picker returns an address whose read permission dies with the app
 * process. autoinsta schedules posts *days* ahead, so by publish time that permission
 * is long gone and the address is worthless.
 *
 * The property that must hold: **once a post is scheduled, its media is readable with
 * plain file I/O — no ContentResolver, no permission, no original file needed.**
 *
 * This test fails on the pre-fix code, where `localUri` held the raw
 * `content://media/picker/...` string and `File(localUri).canRead()` was false.
 */
@RunWith(AndroidJUnit4::class)
class MediaDurabilityTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var mediaFileStore: MediaFileStore
    private lateinit var repository: PostRepository
    private lateinit var sourceDir: File

    /** Stands in for a photo sitting in the user's gallery. */
    private val originalBytes = ByteArray(4096) { (it % 251).toByte() }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mediaFileStore = MediaFileStore(context)
        repository = PostRepository(
            postDao = database.scheduledPostDao(),
            mediaDao = database.mediaItemDao(),
            mediaFileStore = mediaFileStore,
        )
        sourceDir = File(context.cacheDir, "durability-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        sourceDir.deleteRecursively()
        File(context.filesDir, MediaFileStore.MEDIA_DIR_NAME).deleteRecursively()
    }

    /** Writes a fake "gallery photo" and returns a `file://` URI for it. */
    private fun givenSourceFile(name: String = "original.jpg"): String {
        val file = File(sourceDir, name)
        file.writeBytes(originalBytes)
        return "file://${file.absolutePath}"
    }

    private fun aPost() = ScheduledPostEntity(
        postType = PostType.SINGLE_IMAGE,
        status = PostStatus.SCHEDULED,
        caption = "test",
        hashtags = "#test",
        presetId = null,
        scheduledAt = System.currentTimeMillis() + 86_400_000L,
        createdAt = System.currentTimeMillis(),
    )

    // ── The regression ─────────────────────────────────────────────────────

    @Test
    fun scheduledMediaIsReadableAsAPlainFile() = runTest {
        val postId = repository.insertPost(
            aPost(),
            listOf(
                MediaToSave(
                    sourceUri = givenSourceFile(),
                    mediaType = MediaType.IMAGE,
                    alreadyImported = false,
                )
            ),
        )

        val stored = repository.getById(postId)!!.mediaItems.single()

        // The whole point: openable with plain file I/O, no permissions involved.
        val file = File(stored.localUri)
        assertTrue(
            "Stored media must be a readable file, was: ${stored.localUri}",
            file.isFile && file.canRead(),
        )
    }

    @Test
    fun theCopyIsByteForByteIdenticalToTheOriginal() = runTest {
        val postId = repository.insertPost(
            aPost(),
            listOf(MediaToSave(givenSourceFile(), MediaType.IMAGE, alreadyImported = false)),
        )

        val stored = File(repository.getById(postId)!!.mediaItems.single().localUri)

        // No decode, no re-encode, no resize — image quality is untouched.
        assertArrayEqualsBytes(originalBytes, stored.readBytes())
    }

    @Test
    fun mediaSurvivesTheOriginalBeingDeletedFromTheGallery() = runTest {
        val sourceUri = givenSourceFile()
        val postId = repository.insertPost(
            aPost(),
            listOf(MediaToSave(sourceUri, MediaType.IMAGE, alreadyImported = false)),
        )

        // The user tidies up their gallery after scheduling.
        File(sourceUri.removePrefix("file://")).delete()

        val stored = File(repository.getById(postId)!!.mediaItems.single().localUri)
        assertTrue("Scheduled media must not depend on the original", stored.canRead())
        assertArrayEqualsBytes(originalBytes, stored.readBytes())
    }

    // ── Housekeeping: files must not leak ──────────────────────────────────

    @Test
    fun deletingAPostAlsoDeletesItsFiles() = runTest {
        val postId = repository.insertPost(
            aPost(),
            listOf(MediaToSave(givenSourceFile(), MediaType.IMAGE, alreadyImported = false)),
        )
        val stored = File(repository.getById(postId)!!.mediaItems.single().localUri)
        assertTrue(stored.exists())

        repository.deletePost(postId)

        assertFalse("Deleting a post must not leave its bytes on disk", stored.exists())
    }

    @Test
    fun editingAPostDoesNotReimportMediaItAlreadyHas() = runTest {
        val postId = repository.insertPost(
            aPost(),
            listOf(MediaToSave(givenSourceFile(), MediaType.IMAGE, alreadyImported = false)),
        )
        val originalPath = repository.getById(postId)!!.mediaItems.single().localUri

        // Re-save exactly what was loaded — the flow an "edit the caption" does.
        repository.updatePost(
            repository.getById(postId)!!.post.copy(caption = "edited"),
            listOf(MediaToSave(originalPath, MediaType.IMAGE, alreadyImported = true)),
        )

        val afterEdit = repository.getById(postId)!!
        assertEquals("edited", afterEdit.post.caption)
        assertEquals(
            "Re-saving must reuse the existing file, not make a second copy",
            originalPath,
            afterEdit.mediaItems.single().localUri,
        )
        assertEquals(1, mediaDirFileCount())
    }

    @Test
    fun replacingMediaOnEditDeletesTheFileThatWasDroppedTest() = runTest {
        val postId = repository.insertPost(
            aPost(),
            listOf(MediaToSave(givenSourceFile("first.jpg"), MediaType.IMAGE, alreadyImported = false)),
        )
        val droppedPath = repository.getById(postId)!!.mediaItems.single().localUri

        repository.updatePost(
            repository.getById(postId)!!.post,
            listOf(MediaToSave(givenSourceFile("second.jpg"), MediaType.IMAGE, alreadyImported = false)),
        )

        assertFalse("The replaced file must be cleaned up", File(droppedPath).exists())
        assertEquals("Exactly one file should remain", 1, mediaDirFileCount())
    }

    @Test
    fun aCarouselKeepsItsMediaInOrder() = runTest {
        val postId = repository.insertPost(
            aPost().copy(postType = PostType.CAROUSEL),
            (0 until 3).map {
                MediaToSave(givenSourceFile("item$it.jpg"), MediaType.IMAGE, alreadyImported = false)
            },
        )

        val items = repository.getById(postId)!!.mediaItems.sortedBy { it.orderIndex }
        assertEquals(3, items.size)
        assertEquals(listOf(0, 1, 2), items.map { it.orderIndex })
        items.forEach { assertTrue(File(it.localUri).canRead()) }
    }

    private fun mediaDirFileCount(): Int =
        File(context.filesDir, MediaFileStore.MEDIA_DIR_NAME).listFiles()?.size ?: 0

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertEquals("byte count", expected.size, actual.size)
        assertTrue("byte contents differ", expected.contentEquals(actual))
    }
}
