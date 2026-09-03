package com.autoinsta.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.AccountEntity
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.remote.CloudinaryUploader
import com.autoinsta.data.remote.InstagramApi
import com.autoinsta.data.remote.NetworkModule
import com.autoinsta.data.remote.dto.ContainerStatusDto
import com.autoinsta.data.remote.dto.MediaContainerDto
import com.autoinsta.data.remote.dto.PublishedMediaDto
import com.autoinsta.data.remote.dto.PublishingLimitDto
import com.autoinsta.data.remote.dto.PublishingLimitEnvelopeDto
import com.autoinsta.domain.MediaFit
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response

/**
 * The publish pipeline, driven against a fake Instagram.
 *
 * ## The post this exists for
 * On 2026-09-03 a real post failed with **"Media ID is not available"**. That is Meta's
 * wording for *the container is not ready yet*: a single image was published the instant
 * its container was created, before Instagram had finished fetching the file from
 * Cloudinary. Only Reels waited; images and carousels did not.
 *
 * Two things had to be true for that post to be lost, so both are pinned here:
 * the readiness check, and the fact that Meta's "not yet" must be retried rather than
 * treated as a permanent rejection.
 *
 * Nothing here touches the network — the API, the uploader and the account are all fakes.
 * A test suite must never be able to publish to the owner's account.
 */
@RunWith(AndroidJUnit4::class)
class PublishRepositoryTest {

    private lateinit var app: AutoInstaApp
    private lateinit var mediaFile: File

    /** Every API call, in the order it was made. The ordering is the whole point. */
    private class FakeApi(
        private val statuses: MutableList<String> = mutableListOf("FINISHED"),
        private val publishThrows: Exception? = null,
    ) : InstagramApi {
        val calls = mutableListOf<String>()

        override suspend fun createImageContainer(
            igUserId: String,
            imageUrl: String,
            caption: String?,
            isCarouselItem: Boolean?,
            accessToken: String,
        ): MediaContainerDto {
            calls += "createImageContainer"
            return MediaContainerDto(id = "container-1")
        }

        override suspend fun createReelContainer(
            igUserId: String,
            videoUrl: String,
            mediaType: String,
            caption: String?,
            accessToken: String,
        ): MediaContainerDto {
            calls += "createReelContainer"
            return MediaContainerDto(id = "container-1")
        }

        override suspend fun createVideoCarouselItem(
            igUserId: String,
            videoUrl: String,
            mediaType: String,
            isCarouselItem: Boolean,
            accessToken: String,
        ): MediaContainerDto {
            calls += "createVideoCarouselItem"
            return MediaContainerDto(id = "child")
        }

        override suspend fun createCarouselContainer(
            igUserId: String,
            children: String,
            mediaType: String,
            caption: String?,
            accessToken: String,
        ): MediaContainerDto {
            calls += "createCarouselContainer"
            return MediaContainerDto(id = "container-1")
        }

        override suspend fun getContainerStatus(
            containerId: String,
            fields: String,
            accessToken: String,
        ): ContainerStatusDto {
            calls += "getContainerStatus"
            val next = if (statuses.size > 1) statuses.removeAt(0) else statuses.first()
            return ContainerStatusDto(statusCode = next)
        }

        override suspend fun publishContainer(
            igUserId: String,
            creationId: String,
            accessToken: String,
        ): PublishedMediaDto {
            calls += "publishContainer"
            publishThrows?.let { throw it }
            return PublishedMediaDto(id = "ig-media-99")
        }

        override suspend fun getPublishingLimit(
            igUserId: String,
            fields: String,
            accessToken: String,
        ): PublishingLimitEnvelopeDto {
            calls += "getPublishingLimit"
            return PublishingLimitEnvelopeDto(data = listOf(PublishingLimitDto(quotaUsage = 0)))
        }
    }

    /** Returns a plausible upload without going anywhere. */
    private class FakeUploader : CloudinaryUploader(OkHttpClient(), "cloud", "preset") {
        override fun isConfigured(): Boolean = true

        override suspend fun upload(file: File, mediaType: MediaType): Uploaded =
            Uploaded(
                publicId = "pub-1",
                secureUrl = "https://example.invalid/pub-1.jpg",
                widthPx = 2000,
                heightPx = 2500,
                resourceType = if (mediaType == MediaType.VIDEO) "video" else "image",
            )

        override fun deliveryUrl(
            uploaded: Uploaded,
            mode: MediaFit.Mode,
            cropOffset: Float,
            padColour: String,
        ): String = "https://example.invalid/delivery.jpg"
    }

    private inner class FakeAccounts : AccountRepository(
        accountDao = app.database.accountDao(),
        tokenStore = app.tokenStore,
        authApi = NetworkModule.instagramAuthApi,
    ) {
        override suspend fun get(): AccountEntity? = AccountEntity(
            igUserId = "ig-1",
            username = "tester",
            profilePictureUrl = null,
            connectedAt = 0L,
            tokenExpiresAt = Long.MAX_VALUE,
        )

        override fun accessToken(): String = "fake-token"
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        mediaFile = File(app.cacheDir, "publish-test.jpg").apply { writeBytes(ByteArray(32) { 1 }) }
    }

    @After
    fun tearDown() {
        mediaFile.delete()
    }

    private fun repositoryWith(api: InstagramApi) =
        PublishRepository(uploader = FakeUploader(), api = api, accountRepository = FakeAccounts())

    private fun post(type: PostType, items: Int = 1): ScheduledPostWithMedia =
        ScheduledPostWithMedia(
            post = ScheduledPostEntity(
                id = 1,
                postType = type,
                status = PostStatus.SCHEDULED,
                caption = "test",
                hashtags = "",
                presetId = null,
                scheduledAt = 0L,
                createdAt = 0L,
            ),
            mediaItems = (0 until items).map { i ->
                MediaItemEntity(
                    id = i.toLong(),
                    postId = 1,
                    mediaType = if (type == PostType.REEL) MediaType.VIDEO else MediaType.IMAGE,
                    localUri = mediaFile.absolutePath,
                    cloudinaryUrl = null,
                    orderIndex = i,
                    widthPx = 2000,
                    heightPx = 2500,
                    fitMode = MediaFit.Mode.PAD,
                    cropOffset = 0.5f,
                )
            },
        )

    private fun httpError(body: String) = HttpException(
        Response.error<Any>(400, body.toResponseBody("application/json".toMediaType()))
    )

    // ── The readiness check ────────────────────────────────────────────────

    @Test
    fun aSingleImageIsCheckedForReadinessBeforeItIsPublished() = runBlocking {
        val api = FakeApi()

        val result = repositoryWith(api).publish(post(PostType.SINGLE_IMAGE))

        assertTrue("expected success, got $result", result is PublishResult.Success)
        val statusAt = api.calls.indexOf("getContainerStatus")
        val publishAt = api.calls.indexOf("publishContainer")
        assertTrue("the container's status was never checked: ${api.calls}", statusAt >= 0)
        assertTrue(
            "published before checking the container was ready: ${api.calls}",
            statusAt < publishAt,
        )
    }

    @Test
    fun aSingleImageWaitsWhileInstagramIsStillFetchingIt() = runBlocking {
        val api = FakeApi(statuses = mutableListOf("IN_PROGRESS", "FINISHED"))

        val result = repositoryWith(api).publish(post(PostType.SINGLE_IMAGE))

        assertTrue(result is PublishResult.Success)
        assertEquals(
            "should have looked twice before publishing",
            2,
            api.calls.count { it == "getContainerStatus" },
        )
        assertEquals(1, api.calls.count { it == "publishContainer" })
    }

    @Test
    fun aCarouselIsAlsoCheckedBeforePublishing() = runBlocking {
        val api = FakeApi()

        val result = repositoryWith(api).publish(post(PostType.CAROUSEL, items = 2))

        assertTrue("expected success, got $result", result is PublishResult.Success)
        val statusAt = api.calls.indexOf("getContainerStatus")
        val publishAt = api.calls.indexOf("publishContainer")
        // Both halves matter. Comparing the indices alone would pass on the broken code,
        // where indexOf returns -1 for a check that never happened.
        assertTrue("the carousel's status was never checked: ${api.calls}", statusAt >= 0)
        assertTrue(
            "published before checking the carousel was ready: ${api.calls}",
            statusAt < publishAt,
        )
    }

    @Test
    fun anImageWhoseStatusNeverResolvesIsStillPublished() = runBlocking {
        // The status endpoint gives nothing useful. Refusing here would lose a post that
        // was almost certainly fine.
        val api = FakeApi(statuses = mutableListOf("SOMETHING_ELSE"))

        val result = repositoryWith(api).publish(post(PostType.SINGLE_IMAGE))

        assertTrue("expected success, got $result", result is PublishResult.Success)
        assertEquals(1, api.calls.count { it == "publishContainer" })
    }

    // ── "Not yet" must be retried, not given up on ─────────────────────────

    @Test
    fun mediaIdNotAvailableIsRetriedRatherThanFailingThePost() = runBlocking {
        val api = FakeApi(
            publishThrows = httpError("""{"error":{"message":"Media ID is not available"}}"""),
        )

        val result = repositoryWith(api).publish(post(PostType.SINGLE_IMAGE))

        // Before this fix it was a PermanentFailure, the worker gave up, and the post
        // dropped out of the queue for good.
        assertTrue(
            "Meta saying 'not ready yet' must be retried, got $result",
            result is PublishResult.TransientFailure,
        )
    }

    @Test
    fun a4xxThatReallyMeansNoIsStillPermanent() = runBlocking {
        val api = FakeApi(
            publishThrows = httpError("""{"error":{"message":"Aspect ratio not supported"}}"""),
        )

        val result = repositoryWith(api).publish(post(PostType.SINGLE_IMAGE))

        assertTrue(
            "a genuine rejection must not be retried forever, got $result",
            result is PublishResult.PermanentFailure,
        )
    }
}
