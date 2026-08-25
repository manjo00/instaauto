package com.autoinsta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Responses from Meta's auth endpoints.
 *
 * Every field is nullable on purpose, per the project convention: these come off the
 * network and Meta can and does change shapes, return partial objects, or return an
 * error body with HTTP 200. Validating at the point of use beats crashing on a missing
 * field somewhere deep in the call stack.
 */

/** `POST https://api.instagram.com/oauth/access_token` */
@Serializable
data class ShortLivedTokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val permissions: List<String>? = null,
)

/**
 * `GET https://graph.instagram.com/access_token` (exchange) and
 * `GET https://graph.instagram.com/refresh_access_token` (refresh).
 * Both return the same shape.
 */
@Serializable
data class LongLivedTokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    /** Seconds until expiry — normally 60 days' worth. */
    @SerialName("expires_in") val expiresIn: Long? = null,
)

/** `GET https://graph.instagram.com/me` */
@Serializable
data class InstagramProfileDto(
    /** Business Login returns `user_id`; some responses use `id`. Accept either. */
    @SerialName("user_id") val userId: String? = null,
    val id: String? = null,
    val username: String? = null,
    @SerialName("account_type") val accountType: String? = null,
    @SerialName("profile_picture_url") val profilePictureUrl: String? = null,

) {
    /** Whichever id field Meta actually populated. */
    val resolvedId: String? get() = userId ?: id
}

/**
 * Meta's error envelope. It arrives with a non-2xx status, but the body is what says
 * *why* — and the message is usually the only useful diagnostic.
 */
@Serializable
data class MetaErrorEnvelopeDto(
    val error: MetaErrorDto? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
) {
    /** A human-readable reason, whichever of Meta's two error shapes came back. */
    val readableMessage: String?
        get() = error?.message ?: errorMessage
}

@Serializable
data class MetaErrorDto(
    val message: String? = null,
    val type: String? = null,
    val code: Int? = null,
    @SerialName("error_subcode") val errorSubcode: Int? = null,
)
