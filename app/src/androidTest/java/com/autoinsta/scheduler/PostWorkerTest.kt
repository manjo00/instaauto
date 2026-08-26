package com.autoinsta.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.remote.NetworkModule
import com.autoinsta.data.repository.MediaToSave
import com.autoinsta.data.repository.PublishRepository
import com.autoinsta.data.repository.PublishResult
import com.autoinsta.domain.ScheduleCalculator
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives [PostWorker] directly, so its behaviour is verified without waiting for a real
 * alarm. The timing decisions themselves live in `ScheduleCalculatorTest` on the JVM;
 * this checks the worker acts on them and leaves the database in the right state.
 *
 * ## Publishing is faked here, deliberately
 * A real [PublishRepository] posts to the connected Instagram account. **A test suite
 * must never be able to do that** — an earlier version of this file could, and running it
 * once credentials existed was a genuine hazard.
 *
 * These substitute a fake publisher through `AutoInstaApp.publishRepositoryOverride` and
 * assert what the *worker* does with each outcome, which is what this class is actually
 * responsible for. The publish pipeline itself is covered by `PublishPolicyTest` and
 * `MediaFitTest`, and was proven against the live services by hand.
 */
@RunWith(AndroidJUnit4::class)
class PostWorkerTest {

    private lateinit var context: Context
    private lateinit var app: AutoInstaApp
    private lateinit var sourceDir: File
    private val createdPostIds = mutableListOf<Long>()

    private val HOUR = 60L * 60L * 1000L

    /** Returns whatever it is told to and never touches the network. */
    private class FakePublisher(
        private val result: PublishResult,
    ) : PublishRepository(
        uploader = NetworkModule.cloudinaryUploader,
        api = NetworkModule.instagramApi,
        accountRepository = ApplicationProvider
            .getApplicationContext<AutoInstaApp>()
            .accountRepository,
    ) {
        var callCount = 0
            private set

        override suspend fun publish(post: ScheduledPostWithMedia): PublishResult {
            callCount++
            return result
        }
    }

    private fun publisherReturning(result: PublishResult): FakePublisher =
        FakePublisher(result).also { app.publishRepositoryOverride = it }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as AutoInstaApp
        sourceDir = File(context.cacheDir, "worker-test").apply { mkdirs() }
        // Default: publishing "succeeds" without going anywhere near Instagram.
        publisherReturning(PublishResult.Success(FAKE_MEDIA_ID))
    }

    @After
    fun tearDown() = runTest {
        app.publishRepositoryOverride = null
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

    // ── What the worker does with each publish outcome ─────────────────────

    @Test
    fun aSuccessfulPublishIsMarkedPostedAndRecordsInstagramsId() = runTest {
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)

        val result = runWorkerFor(id)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(PostStatus.POSTED, statusOf(id))

        val history = app.historyRepository.getForPost(id)
        assertEquals(1, history.size)
        assertEquals(FAKE_MEDIA_ID, history.first().instagramMediaId)
    }

    @Test
    fun aPermanentFailureIsRecordedAndNotRetried() = runTest {
        publisherReturning(PublishResult.PermanentFailure("Image is too tall for Instagram."))
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)

        val result = runWorkerFor(id)

        // Retrying a shape Instagram will never accept would burn the daily quota forever.
        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(PostStatus.FAILED, statusOf(id))
        assertTrue(
            "the recorded reason should be the one the publisher gave",
            app.historyRepository.getForPost(id).first().errorMessage.orEmpty()
                .contains("too tall", ignoreCase = true),
        )
    }

    @Test
    fun aTransientFailureIsRetriedAndLeftInTheQueue() = runTest {
        publisherReturning(PublishResult.TransientFailure("No internet connection."))
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)

        val result = runWorkerFor(id)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            "the post must go back in the queue or the retry is blocked by the status guard",
            PostStatus.SCHEDULED,
            statusOf(id),
        )
        assertTrue(
            "a pending retry is not an outcome worth recording yet",
            app.historyRepository.getForPost(id).isEmpty(),
        )
    }

    // ── Media gone missing ─────────────────────────────────────────────────

    @Test
    fun aPostWhoseMediaVanishedFailsWithoutEvenTryingToPublish() = runTest {
        val fake = publisherReturning(PublishResult.Success(FAKE_MEDIA_ID))
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)
        app.postRepository.getById(id)!!.mediaItems.forEach { File(it.localUri).delete() }

        runWorkerFor(id)

        assertEquals(PostStatus.FAILED, statusOf(id))
        assertEquals("missing media should short-circuit before any upload", 0, fake.callCount)
        assertTrue(
            app.historyRepository.getForPost(id).first().errorMessage.orEmpty()
                .contains("missing", ignoreCase = true),
        )
    }

    // ── The per-post missed rules, end to end ──────────────────────────────

    @Test
    fun defaultPolicyRefusesAPostStalerThanTheGracePeriod() = runTest {
        val fake = publisherReturning(PublishResult.Success(FAKE_MEDIA_ID))
        val id = givenPost(
            scheduledAt = System.currentTimeMillis() - (ScheduleCalculator.MISSED_GRACE_MILLIS + HOUR),
            policy = MissedPostPolicy.POST_IF_RECENT,
        )

        runWorkerFor(id)

        assertEquals(PostStatus.FAILED, statusOf(id))
        assertEquals("a stale post must not reach Instagram at all", 0, fake.callCount)
    }

    @Test
    fun defaultPolicyPublishesInsideTheGracePeriod() = runTest {
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
        val fake = publisherReturning(PublishResult.Success(FAKE_MEDIA_ID))
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
        assertEquals("and must certainly not reach Instagram", 0, fake.callCount)
        assertTrue(app.historyRepository.getForPost(id).isEmpty())
    }

    // ── Guards ─────────────────────────────────────────────────────────────

    @Test
    fun aPostThatIsNotDueYetIsLeftAlone() = runTest {
        val fake = publisherReturning(PublishResult.Success(FAKE_MEDIA_ID))
        val id = givenPost(scheduledAt = System.currentTimeMillis() + HOUR)

        runWorkerFor(id)

        assertEquals(PostStatus.SCHEDULED, statusOf(id))
        assertEquals(0, fake.callCount)
    }

    @Test
    fun runningTwiceDoesNotPublishTwice() = runTest {
        val fake = publisherReturning(PublishResult.Success(FAKE_MEDIA_ID))
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)

        runWorkerFor(id)
        runWorkerFor(id)

        // A duplicate here would be a duplicate on the account.
        assertEquals("the second run must not publish again", 1, fake.callCount)
        assertEquals(1, app.historyRepository.getForPost(id).size)
    }

    @Test
    fun aDeletedPostIsHandledQuietly() = runTest {
        val fake = publisherReturning(PublishResult.Success(FAKE_MEDIA_ID))
        val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)
        app.postRepository.deletePost(id)
        createdPostIds.remove(id)

        val result = runWorkerFor(id)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun theWorkerNeverLeavesAPostStuckInPosting() = runTest {
        // POSTING is a transient state. A post left there would look permanently
        // in-flight in the queue and would never be picked up again.
        listOf(
            PublishResult.Success(FAKE_MEDIA_ID),
            PublishResult.PermanentFailure("nope"),
            PublishResult.TransientFailure("later"),
        ).forEach { outcome ->
            publisherReturning(outcome)
            val id = givenPost(scheduledAt = System.currentTimeMillis() - 1000L)

            runWorkerFor(id)

            assertNotEquals("stuck in POSTING after $outcome", PostStatus.POSTING, statusOf(id))
        }
    }

    private companion object {
        const val FAKE_MEDIA_ID = "fake-media-id-123"
    }
}
