package com.autoinsta.data.repository

import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.remote.CloudinaryUploader
import com.autoinsta.data.remote.InstagramApi
import com.autoinsta.data.remote.dto.ContainerStatusDto
import com.autoinsta.domain.MediaFit
import com.autoinsta.domain.PublishPolicy
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** The outcome of trying to put a post on Instagram. */
sealed interface PublishResult {
    /** It's live. [mediaId] is Instagram's id for the post. */
    data class Success(val mediaId: String) : PublishResult

    /** It failed for a reason that won't change on its own — bad media, over quota. */
    data class PermanentFailure(val reason: String) : PublishResult

    /** It failed for a reason that might not recur — network, Instagram having a moment. */
    data class TransientFailure(val reason: String) : PublishResult
}

/**
 * Publishes a scheduled post to Instagram.
 *
 * ## The shape of the work
 * Instagram never accepts a file. Every publish is: put the media somewhere public
 * (Cloudinary), describe the post to Instagram as a *container*, wait for Instagram to
 * fetch and transcode it, then publish that container.
 *
 * Three variants:
 * - **Single image** — one container, publish.
 * - **Reel** — one container, then poll until Instagram finishes transcoding, then publish.
 * - **Carousel** — a container per item, then a parent container listing them, then publish.
 *
 * ## Why failures are split in two
 * This runs inside a background worker. A network blip should be retried; a 9:16 image
 * Instagram will never accept should not be retried every fifteen minutes forever. The
 * caller uses [PublishResult] to tell those apart.
 *
 * `open` so instrumented tests can substitute a fake. Without that seam, running the
 * device test suite would publish test content to the owner's live Instagram account —
 * a test suite must never be able to do that.
 */
open class PublishRepository(
    private val uploader: CloudinaryUploader,
    private val api: InstagramApi,
    private val accountRepository: AccountRepository,
) {

    open suspend fun publish(post: ScheduledPostWithMedia): PublishResult = withContext(Dispatchers.IO) {
        val account = accountRepository.get()
            ?: return@withContext PublishResult.PermanentFailure(
                "No Instagram account connected."
            )
        val token = accountRepository.accessToken()
            ?: return@withContext PublishResult.PermanentFailure(
                "Instagram login has expired — reconnect in Settings."
            )
        if (!uploader.isConfigured()) {
            return@withContext PublishResult.PermanentFailure(
                "Cloudinary isn't set up — add the upload preset."
            )
        }

        // Checked before uploading anything: over quota means the media transfer would be
        // wasted, and the owner deserves a clear reason rather than an API rejection.
        val quota = runCatching {
            api.getPublishingLimit(igUserId = account.igUserId, accessToken = token)
        }.getOrNull()
        if (!PublishPolicy.hasQuotaRemaining(quota?.quotaUsage)) {
            return@withContext PublishResult.TransientFailure(
                "Instagram's daily posting limit is used up. It'll be retried later."
            )
        }

        val caption = PublishPolicy.combineCaption(post.post.caption, post.post.hashtags)
        val captionVerdict = PublishPolicy.checkCaption(post.post.caption, post.post.hashtags)
        if (captionVerdict !is PublishPolicy.CaptionVerdict.Ok) {
            return@withContext PublishResult.PermanentFailure(
                PublishPolicy.explain(captionVerdict) ?: "Instagram rejected the caption."
            )
        }

        val media = post.mediaItems.sortedBy { it.orderIndex }
        if (media.isEmpty()) {
            return@withContext PublishResult.PermanentFailure("This post has no media.")
        }

        try {
            when (post.post.postType) {
                PostType.SINGLE_IMAGE -> publishSingle(account.igUserId, token, media.first().toFileRef(), caption)
                PostType.REEL -> publishReel(account.igUserId, token, media.first().toFileRef(), caption)
                PostType.CAROUSEL -> publishCarousel(account.igUserId, token, media.map { it.toFileRef() }, caption)
            }
        } catch (e: Exception) {
            classify(e)
        }
    }

    // ── The three pipelines ────────────────────────────────────────────────

    private suspend fun publishSingle(
        igUserId: String,
        token: String,
        item: FileRef,
        caption: String,
    ): PublishResult {
        val url = uploadAndBuildUrl(item)
        val container = api.createImageContainer(
            igUserId = igUserId,
            imageUrl = url,
            caption = caption,
            accessToken = token,
        ).id ?: return PublishResult.TransientFailure("Instagram didn't return a container id.")

        return publishContainer(igUserId, token, container)
    }

    private suspend fun publishReel(
        igUserId: String,
        token: String,
        item: FileRef,
        caption: String,
    ): PublishResult {
        val url = uploadAndBuildUrl(item)
        val container = api.createReelContainer(
            igUserId = igUserId,
            videoUrl = url,
            caption = caption,
            accessToken = token,
        ).id ?: return PublishResult.TransientFailure("Instagram didn't return a container id.")

        // A reel isn't publishable the moment its container exists — Instagram has to
        // fetch and transcode the video first.
        return when (val wait = awaitReady(igUserId, token, container)) {
            is PublishResult.Success -> publishContainer(igUserId, token, container)
            else -> wait
        }
    }

    private suspend fun publishCarousel(
        igUserId: String,
        token: String,
        items: List<FileRef>,
        caption: String,
    ): PublishResult {
        if (!PublishPolicy.carouselCountValid(items.size)) {
            return PublishResult.PermanentFailure(
                "A carousel needs between 2 and 10 items; this one has ${items.size}."
            )
        }

        // Children carry no caption — Instagram ignores it there, and the caption belongs
        // to the parent container.
        val childIds = items.map { item ->
            val url = uploadAndBuildUrl(item)
            val id = if (item.mediaType == MediaType.VIDEO) {
                api.createVideoCarouselItem(igUserId = igUserId, videoUrl = url, accessToken = token).id
            } else {
                api.createImageContainer(
                    igUserId = igUserId,
                    imageUrl = url,
                    caption = null,
                    isCarouselItem = true,
                    accessToken = token,
                ).id
            }
            id ?: return PublishResult.TransientFailure("Instagram rejected one of the carousel items.")
        }

        val parent = api.createCarouselContainer(
            igUserId = igUserId,
            children = childIds.joinToString(","),
            caption = caption,
            accessToken = token,
        ).id ?: return PublishResult.TransientFailure("Instagram didn't return a carousel id.")

        return publishContainer(igUserId, token, parent)
    }

    // ── Shared steps ───────────────────────────────────────────────────────

    /**
     * Waits for a video container to become publishable, following Meta's guidance of one
     * check per minute for at most five.
     */
    private suspend fun awaitReady(
        igUserId: String,
        token: String,
        containerId: String,
    ): PublishResult {
        var attempt = 1
        while (true) {
            val state = runCatching {
                api.getContainerStatus(containerId = containerId, accessToken = token).state
            }.getOrElse { ContainerStatusDto.State.UNKNOWN }

            when (val decision = PublishPolicy.decidePoll(state, attempt)) {
                is PublishPolicy.PollDecision.ReadyToPublish ->
                    return PublishResult.Success(containerId)
                is PublishPolicy.PollDecision.GiveUp ->
                    return PublishResult.PermanentFailure(decision.reason)
                is PublishPolicy.PollDecision.WaitAndRetry -> {
                    delay(decision.delayMillis)
                    attempt++
                }
            }
        }
    }

    private suspend fun publishContainer(
        igUserId: String,
        token: String,
        containerId: String,
    ): PublishResult {
        val mediaId = api.publishContainer(
            igUserId = igUserId,
            creationId = containerId,
            accessToken = token,
        ).id ?: return PublishResult.TransientFailure("Instagram didn't confirm the post.")

        return PublishResult.Success(mediaId)
    }

    /** Upload, then build the address with the fitting applied. */
    private suspend fun uploadAndBuildUrl(item: FileRef): String {
        val uploaded = uploader.upload(File(item.path), item.mediaType)
        return uploader.deliveryUrl(uploaded, item.fitMode)
    }

    /**
     * Decide whether a failure is worth retrying.
     *
     * Getting this wrong in either direction is costly: retrying a permanently-bad image
     * burns quota forever, while giving up on a dropped connection loses a post that
     * would have worked.
     */
    private fun classify(e: Exception): PublishResult = when (e) {
        is retrofit2.HttpException -> {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val message = body?.let { metaMessage(it) }
            when (e.code()) {
                // 4xx is Instagram saying no — the same request will keep being refused.
                in 400..499 -> PublishResult.PermanentFailure(
                    message ?: "Instagram rejected the post (HTTP ${e.code()})."
                )
                else -> PublishResult.TransientFailure(
                    message ?: "Instagram had a problem (HTTP ${e.code()}). Will retry."
                )
            }
        }
        is java.net.UnknownHostException ->
            PublishResult.TransientFailure("No internet connection. Will retry.")
        is java.net.SocketTimeoutException ->
            PublishResult.TransientFailure("The upload timed out. Will retry.")
        is java.io.IOException ->
            PublishResult.TransientFailure(e.message ?: "Upload failed. Will retry.")
        else ->
            PublishResult.PermanentFailure(e.message ?: "Something went wrong publishing.")
    }

    private fun metaMessage(body: String): String? =
        Regex(""""(?:error_user_msg|error_message|message)"\s*:\s*"([^"]+)"""")
            .find(body)?.groupValues?.getOrNull(1)

    /** A media item reduced to what publishing needs. */
    private data class FileRef(
        val path: String,
        val mediaType: MediaType,
        val fitMode: MediaFit.Mode,
    )

    private fun com.autoinsta.data.db.entities.MediaItemEntity.toFileRef() = FileRef(
        path = localUri,
        mediaType = mediaType,
        // Phase 5a always pads: nothing is cropped without the owner saying so, and
        // nothing fails for shape. Phase 5b makes this a per-item choice.
        fitMode = MediaFit.Mode.PAD,
    )
}
