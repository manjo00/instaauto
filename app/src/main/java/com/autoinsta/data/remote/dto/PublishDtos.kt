package com.autoinsta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Responses from Instagram's publishing endpoints and from Cloudinary.
 *
 * Nullable throughout, and ids go through [FlexibleIdSerializer] — Meta returns ids as
 * JSON numbers in some responses and quoted strings in others, and a type mismatch fails
 * the entire parse. That already cost one completed login; see `docs/STATUS.md`.
 */

/**
 * `POST /<IG_ID>/media` — creating a container, for a single item, a carousel child, or
 * the carousel parent. All three return just an id.
 */
@Serializable
data class MediaContainerDto(
    @Serializable(with = FlexibleIdSerializer::class)
    val id: String? = null,
)

/** `POST /<IG_ID>/media_publish` — the published post. */
@Serializable
data class PublishedMediaDto(
    @Serializable(with = FlexibleIdSerializer::class)
    val id: String? = null,
)

/**
 * `GET /<CONTAINER_ID>?fields=status_code`
 *
 * Videos are not ready to publish the moment the container is made — Instagram
 * transcodes first, and publishing early fails.
 */
@Serializable
data class ContainerStatusDto(
    @SerialName("status_code") val statusCode: String? = null,
    /** Present when `status_code` is ERROR; explains what Instagram disliked. */
    val status: String? = null,
    @Serializable(with = FlexibleIdSerializer::class)
    val id: String? = null,
) {
    /** Instagram's five states, plus a catch-all for anything new. */
    enum class State { IN_PROGRESS, FINISHED, ERROR, EXPIRED, PUBLISHED, UNKNOWN }

    val state: State
        get() = when (statusCode?.uppercase()) {
            "IN_PROGRESS" -> State.IN_PROGRESS
            "FINISHED" -> State.FINISHED
            "ERROR" -> State.ERROR
            "EXPIRED" -> State.EXPIRED
            "PUBLISHED" -> State.PUBLISHED
            else -> State.UNKNOWN
        }
}

/**
 * `GET /<IG_ID>/content_publishing_limit`
 *
 * Checked before publishing. Instagram enforces a rolling 24-hour cap and simply refuses
 * beyond it; knowing first means a clear message instead of a confusing API error.
 */
@Serializable
data class PublishingLimitEnvelopeDto(
    val data: List<PublishingLimitDto>? = null,
) {
    val quotaUsage: Int? get() = data?.firstOrNull()?.quotaUsage
}

@Serializable
data class PublishingLimitDto(
    @SerialName("quota_usage") val quotaUsage: Int? = null,
    @SerialName("config") val config: PublishingLimitConfigDto? = null,
)

@Serializable
data class PublishingLimitConfigDto(
    @SerialName("quota_total") val quotaTotal: Int? = null,
    @SerialName("quota_duration") val quotaDuration: Long? = null,
)

/**
 * Cloudinary's response to an unsigned upload.
 *
 * `publicId` is what matters: the delivery URL is rebuilt from it with the fitting
 * transformation applied, rather than using `secureUrl` directly, so the stored original
 * stays untouched and the fit can change without re-uploading.
 */
@Serializable
data class CloudinaryUploadDto(
    @SerialName("public_id") val publicId: String? = null,
    @SerialName("secure_url") val secureUrl: String? = null,
    val format: String? = null,
    @SerialName("resource_type") val resourceType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bytes: Long? = null,
    val error: CloudinaryErrorDto? = null,
)

@Serializable
data class CloudinaryErrorDto(
    val message: String? = null,
)
