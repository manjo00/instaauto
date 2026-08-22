package com.autoinsta.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.repository.MediaToSave
import com.autoinsta.domain.ScheduleCalculator
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives [PostWorker] directly, so the publish path is verified without waiting for a
 * real alarm to fire. The timing decisions themselves are covered by
 * `ScheduleCalculatorTest` on the JVM; this checks that the worker acts on them and
 * leaves the database in the right state.
 */
@RunWith(AndroidJUnit4::class)
class PostWorkerTest {

    private lateinit var context: Context
    private lateinit var app: AutoInstaApp
    private lateinit var sourceDir: File
    private val createdPostIds = mutableListOf<Long>()

    private val HOUR = 60L * 60L * 1000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as AutoInstaApp
        sourceDir = File(context.cacheDir, "worker-test").apply { mkdirs() }
    }

    @After
    fun tearDown() = runTest {
        createdPostIds.forEach { app.postRepository.deletePost(it) }
        createdPostIds.clear()
        sourceDir.deleteRecursively()
    }

    private fun sourceUri(name: String = "art.jpg"): String {
        val f = File(sourceDir, name).apply { writeBytes(ByteArray(64) { 7 }) }
        return "file://${f.absolutePath}"
    }

    private suspend fun givenPost(
        scheduledAt: Long,
        policy: MissedPostPolicy = MissedPostPolicy.POST_IF_RECENT,
        withMedia: Boolean = true,
    ): Long {
        val id = app.postRepository.insertPost(
            ScheduledPostEntity(
                postType = PostType.SINGLE_IMAGE,
                status = PostStatus.SCHEDULED,
                caption = "worker test",
                hashtags = "#test",
                presetId = null,
                scheduledAt = scheduledAt,
                missedPolicy = policy,
                createdAt = System.currentTimeMillis(),
            ),
            if (withMedia) {
                listOf(MediaToSave(sourceUri(), MediaType.IMAGE, alreadyImported = false))
            } else {
                emptyList()
            },
        )
        createdPostIds += id
        return id
    }

    private suspend fun runWorkerFor(postId: Long): ListenableWorker.Result =
        TestListenableWorkerBuilder<PostWorker>(context)
            .setInputData(PostWorker.inputFor(postId))
            .build()
            .doWork()

    private suspend fun statusOf(postId: Long) = app.postRepository.getById(postId)?.post?.status

    // ── The happy path ─────────────────────────────────────────────────────

    @Test
    fun aDuePostIsMarkedPostedAndRecordedInHistory() = runTest {
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)

        val result = runWorkerFor(id)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(PostStatus.POSTED, statusOf(id))

        val history = app.historyRepository.getForPost(id)
        assertEquals(1, history.size)
        assertEquals(PostStatus.POSTED, history.first().status)
        assertNotNull("the stub still records an id so Phase 5 can swap it", history.first().instagramMediaId)
    }

    // ── Media gone missing ─────────────────────────────────────────────────

    @Test
    fun aPostWhoseMediaVanishedFailsInsteadOfPublishingNothing() = runTest {
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)
        // Simulate the file being lost after scheduling.
        app.postRepository.getById(id)!!.mediaItems.forEach { File(it.localUri).delete() }

        runWorkerFor(id)

        assertEquals(PostStatus.FAILED, statusOf(id))
        val history = app.historyRepository.getForPost(id)
        assertEquals(PostStatus.FAILED, history.first().status)
        assertTrue(
            "the failure should say what went wrong",
            history.first().errorMessage.orEmpty().contains("missing", ignoreCase = true),
        )
    }

    // ── The per-post missed rules, end to end ──────────────────────────────

    @Test
    fun defaultPolicyRefusesAPostStalerThanTheGracePeriod() = runTest {
        val staleBy = ScheduleCalculator.MISSED_GRACE_MILLIS + HOUR
        val id = givenPost(
            scheduledAt = System.currentTimeMillis() - staleBy,
            policy = MissedPostPolicy.POST_IF_RECENT,
        )

        runWorkerFor(id)

        assertEquals(PostStatus.FAILED, statusOf(id))
    }

    @Test
    fun defaultPolicyStillPostsInsideTheGracePeriod() = runTest {
        val id = givenPost(
            scheduledAt = System.currentTimeMillis() - (ScheduleCalculator.MISSED_GRACE_MILLIS / 2),
            policy = MissedPostPolicy.POST_IF_RECENT,
        )

        runWorkerFor(id)

        assertEquals(PostStatus.POSTED, statusOf(id))
    }

    @Test
    fun postAnywayPublishesSomethingDaysLate() = runTest {
        val id = givenPost(
            scheduledAt = System.currentTimeMillis() - 5L * 24 * HOUR,
            policy = MissedPostPolicy.POST_ANYWAY,
        )

        runWorkerFor(id)

        assertEquals(PostStatus.POSTED, statusOf(id))
    }

    @Test
    fun askMeLeavesAStalePostUntouchedForTheUser() = runTest {
        val id = givenPost(
            scheduledAt = System.currentTimeMillis() - 5L * HOUR,
            policy = MissedPostPolicy.ASK_ME,
        )

        runWorkerFor(id)

        assertEquals(
            "an ask-me post must stay in the queue, not silently publish or fail",
            PostStatus.SCHEDULED,
            statusOf(id),
        )
        assertTrue(app.historyRepository.getForPost(id).isEmpty())
    }

    // ── Guards ─────────────────────────────────────────────────────────────

    @Test
    fun aPostThatIsNotDueYetIsLeftAlone() = runTest {
        val id = givenPost(scheduledAt = System.currentTimeMillis() + HOUR)

        runWorkerFor(id)

        assertEquals(PostStatus.SCHEDULED, statusOf(id))
    }

    @Test
    fun runningTwiceDoesNotPublishTwice() = runTest {
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)

        runWorkerFor(id)
        runWorkerFor(id)

        assertEquals(
            "the second run must be a no-op, not a duplicate post",
            1,
            app.historyRepository.getForPost(id).size,
        )
    }

    @Test
    fun aDeletedPostIsHandledQuietly() = runTest {
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)
        app.postRepository.deletePost(id)
        createdPostIds.remove(id)

        val result = runWorkerFor(id)

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
