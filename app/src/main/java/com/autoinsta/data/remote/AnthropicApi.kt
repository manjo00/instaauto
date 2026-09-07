package com.autoinsta.data.remote

import com.autoinsta.data.remote.dto.CoachRequestDto
import com.autoinsta.data.remote.dto.CoachResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * The one Anthropic endpoint the caption coach needs.
 *
 * The API key travels as a per-call header rather than an interceptor so it is only ever
 * attached to this host — the same OkHttp client also talks to Instagram and Cloudinary,
 * and a key added globally would be sent to both.
 */
interface AnthropicApi {

    @Headers("anthropic-version: $ANTHROPIC_VERSION")
    @POST
    suspend fun createMessage(
        @Url url: String = MESSAGES_URL,
        @Header("x-api-key") apiKey: String,
        @Body body: CoachRequestDto,
    ): CoachResponseDto

    companion object {
        const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"

        /** Pinned. Anthropic versions the wire format by date and never silently changes it. */
        const val ANTHROPIC_VERSION = "2023-06-01"

        /**
         * Thinking is on by default on this model and is billed whether or not it is
         * shown, so the ceiling has to leave room for it — a tight `max_tokens` returns an
         * empty answer rather than an error, which is exactly the kind of failure that
         * looks like a bug in our code.
         */
        const val MAX_TOKENS = 4096
    }
}
