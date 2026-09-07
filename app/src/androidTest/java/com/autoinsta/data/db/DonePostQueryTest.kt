package com.autoinsta.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.entities.PostHistoryEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.domain.MediaFit
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import com.autoinsta.domain.model.TimingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Done tab's query, against a throwaway database.
 *
 * It is the only piece of hand-written SQL in the app with real joins in it — two
 * correlated sub-selects and a count — so it earns a test. Everything it can get wrong is
 * silent: a duplicated row per attempt, the *oldest* outcome instead of the newest, or a
 * post that is still waiting showing up as finished.
 */
@RunWith(AndroidJUnit4::class)
class DonePostQueryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        // In-memory: this must never touch the owner's real posts.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun givenPost(
        id: Long,
        status: PostStatus,
        caption: String,
        scheduledAt: Long,
    ) {
        db.scheduledPostDao().insert(
            ScheduledPostEntity(
                id = id,
                postType = PostType.SINGLE_IMAGE,
                status = status,
                caption = caption,
                hashtags = "",
                presetId = null,
                scheduledAt = scheduledAt,
                timingMode = TimingMode.QUEUED,
                createdAt = 1L,
            )
        )
        db.mediaItemDao().insertAll(
            listOf(
                MediaItemEntity(
                    postId = id,
                    mediaType = MediaType.IMAGE,
                    localUri = "/media/$id-first.jpg",
                    cloudinaryUrl = null,
                    orderIndex = 0,
                    widthPx = 1000,
                    heightPx = 1000,
                    fitMode = MediaFit.Mode.PAD,
                    cropOffset = 0.5f,
                ),
                MediaItemEntity(
                    postId = id,
                    mediaType = MediaType.IMAGE,
                    localUri = "/media/$id-second.jpg",
                    cloudinaryUrl = null,
                    orderIndex = 1,
                    widthPx = 1000,
                    heightPx = 1000,
                    fitMode = MediaFit.Mode.PAD,
                    cropOffset = 0.5f,
                ),
            )
        )
    }

    private suspend fun givenAttempt(
        postId: Long,
        status: PostStatus,
        postedAt: Long,
        mediaId: String? = null,
        error: String? = null,
    ) {
        db.postHistoryDao().insert(
            PostHistoryEntity(
                postId = postId,
                postType = PostType.SINGLE_IMAGE,
                caption = "c",
                hashtags = "",
                scheduledAt = 1L,
                postedAt = postedAt,
                status = status,
                instagramMediaId = mediaId,
                errorMessage = error,
            )
        )
    }

    @Test
    fun aPostThatFailedThenSucceededIsOneRowShowingTheSuccess() = runBlocking {
        givenPost(1L, PostStatus.POSTED, "the piece", scheduledAt = 100L)
        givenAttempt(1L, PostStatus.FAILED, postedAt = 200L, error = "Media ID is not available")
        givenAttempt(1L, PostStatus.POSTED, postedAt = 300L, mediaId = "ig-1")

        val rows = db.scheduledPostDao().observeDone().first()

        assertEquals("one row per post, not per attempt", 1, rows.size)
        val row = rows.single()
        assertEquals(PostStatus.POSTED, row.status)
        assertEquals("the newest attempt wins", 300L, row.postedAt)
        assertEquals("ig-1", row.instagramMediaId)
        assertEquals("the earlier failure is counted, not listed", 1, row.failedAttempts)
    }

    @Test
    fun aFailedPostCarriesItsReason() = runBlocking {
        givenPost(2L, PostStatus.FAILED, "the flop", scheduledAt = 100L)
        givenAttempt(2L, PostStatus.FAILED, postedAt = 250L, error = "Aspect ratio not supported")

        val row = db.scheduledPostDao().observeDone().first().single()

        assertEquals(PostStatus.FAILED, row.status)
        assertEquals("Aspect ratio not supported", row.errorMessage)
        assertNull(row.instagramMediaId)
        assertEquals(1, row.failedAttempts)
    }

    @Test
    fun postsStillWaitingAreNotShown() = runBlocking {
        givenPost(3L, PostStatus.SCHEDULED, "still waiting", scheduledAt = 100L)
        givenPost(4L, PostStatus.POSTED, "went out", scheduledAt = 100L)
        givenAttempt(4L, PostStatus.POSTED, postedAt = 400L, mediaId = "ig-4")

        val rows = db.scheduledPostDao().observeDone().first()

        assertEquals(listOf("went out"), rows.map { it.caption })
    }

    @Test
    fun theThumbnailIsTheFirstMediaItem() = runBlocking {
        givenPost(5L, PostStatus.POSTED, "carousel", scheduledAt = 100L)
        givenAttempt(5L, PostStatus.POSTED, postedAt = 500L, mediaId = "ig-5")

        val row = db.scheduledPostDao().observeDone().first().single()

        assertEquals("/media/5-first.jpg", row.localUri)
    }

    @Test
    fun newestFinishedPostComesFirst() = runBlocking {
        givenPost(6L, PostStatus.POSTED, "older", scheduledAt = 100L)
        givenAttempt(6L, PostStatus.POSTED, postedAt = 600L, mediaId = "ig-6")
        givenPost(7L, PostStatus.POSTED, "newer", scheduledAt = 100L)
        givenAttempt(7L, PostStatus.POSTED, postedAt = 700L, mediaId = "ig-7")

        val rows = db.scheduledPostDao().observeDone().first()

        assertEquals(listOf("newer", "older"), rows.map { it.caption })
    }

    @Test
    fun aPostWithNoHistoryStillAppears() = runBlocking {
        // Shouldn't happen, but a row that fell through the cracks must not vanish.
        givenPost(8L, PostStatus.FAILED, "no record", scheduledAt = 100L)

        val row = db.scheduledPostDao().observeDone().first().single()

        assertEquals("no record", row.caption)
        assertNull(row.postedAt)
        assertTrue(row.failedAttempts == 0)
    }
}
