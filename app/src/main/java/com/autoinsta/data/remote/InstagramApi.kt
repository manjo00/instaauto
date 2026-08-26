package com.autoinsta.data.remote

import com.autoinsta.data.remote.dto.ContainerStatusDto
import com.autoinsta.data.remote.dto.MediaContainerDto
import com.autoinsta.data.remote.dto.PublishedMediaDto
import com.autoinsta.data.remote.dto.PublishingLimitEnvelopeDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Instagram's content-publishing endpoints.
 *
 * Publishing is always **two steps**: create a *container* describing the post, then
 * publish that container. They are separate because Instagram fetches and transcodes the
 * media in between — for video that takes real time, and publishing before it finishes
 * fails.
 *
 * Base URL is `graph.instagram.com` (Business Login for Instagram). Using
 * `graph.facebook.com` here returns a misleading "invalid platform app" error.
 *
 * Instagram never receives a file: every call passes a **public URL** that Instagram
 * fetches. Meta: *"we cURL media used in publishing attempts."* That is what Cloudinary
 * is for.
 */
interface InstagramApi {

    /**
     * Create a container for a single image or the item of a carousel.
     *
     * @param isCarouselItem true for a carousel child. Children take no caption — the
     *        caption belongs to the carousel parent, and Instagram ignores it here.
     */
    @FormUrlEncoded
    @POST("{igUserId}/media")
    suspend fun createImageContainer(
        @Path("igUserId") igUserId: String,
        @Field("image_url") imageUrl: String,
        @Field("caption") caption: String? = null,
        @Field("is_carousel_item") isCarouselItem: Boolean? = null,
        @Field("access_token") accessToken: String,
    ): MediaContainerDto

    /**
     * Create a container for a Reel.
     *
     * The response arrives immediately but the reel is **not** publishable yet —
     * [getContainerStatus] must report FINISHED first.
     */
    @FormUrlEncoded
    @POST("{igUserId}/media")
    suspend fun createReelContainer(
        @Path("igUserId") igUserId: String,
        @Field("video_url") videoUrl: String,
        @Field("media_type") mediaType: String = "REELS",
        @Field("caption") caption: String? = null,
        @Field("access_token") accessToken: String,
    ): MediaContainerDto

    /** Create a container for a video that is part of a carousel (not a Reel). */
    @FormUrlEncoded
    @POST("{igUserId}/media")
    suspend fun createVideoCarouselItem(
        @Path("igUserId") igUserId: String,
        @Field("video_url") videoUrl: String,
        @Field("media_type") mediaType: String = "VIDEO",
        @Field("is_carousel_item") isCarouselItem: Boolean = true,
        @Field("access_token") accessToken: String,
    ): MediaContainerDto

    /**
     * Create the carousel itself from its children.
     *
     * @param children comma-separated container ids, 2–10 of them, in display order.
     */
    @FormUrlEncoded
    @POST("{igUserId}/media")
    suspend fun createCarouselContainer(
        @Path("igUserId") igUserId: String,
        @Field("children") children: String,
        @Field("media_type") mediaType: String = "CAROUSEL",
        @Field("caption") caption: String? = null,
        @Field("access_token") accessToken: String,
    ): MediaContainerDto

    /**
     * Is this container ready? Returns IN_PROGRESS, FINISHED, ERROR, EXPIRED or PUBLISHED.
     * Meta's guidance: poll once a minute, for no more than five.
     */
    @GET("{containerId}")
    suspend fun getContainerStatus(
        @Path("containerId") containerId: String,
        @Query("fields") fields: String = "status_code,status",
        @Query("access_token") accessToken: String,
    ): ContainerStatusDto

    /** Publish a finished container. This is the step that puts the post on the account. */
    @FormUrlEncoded
    @POST("{igUserId}/media_publish")
    suspend fun publishContainer(
        @Path("igUserId") igUserId: String,
        @Field("creation_id") creationId: String,
        @Field("access_token") accessToken: String,
    ): PublishedMediaDto

    /**
     * How much of the rolling 24-hour publishing quota is already used.
     * Checked first so hitting the cap reads as a clear message rather than a bare
     * API rejection at 3am.
     */
    @GET("{igUserId}/content_publishing_limit")
    suspend fun getPublishingLimit(
        @Path("igUserId") igUserId: String,
        @Query("fields") fields: String = "config,quota_usage",
        @Query("access_token") accessToken: String,
    ): PublishingLimitEnvelopeDto

    companion object {
        const val BASE_URL = "https://graph.instagram.com/"
    }
}
