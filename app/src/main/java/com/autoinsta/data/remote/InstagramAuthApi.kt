package com.autoinsta.data.remote

import com.autoinsta.BuildConfig
import com.autoinsta.data.remote.dto.InstagramProfileDto
import com.autoinsta.data.remote.dto.LongLivedTokenDto
import com.autoinsta.data.remote.dto.ShortLivedTokenDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Meta's authentication endpoints for Business Login for Instagram.
 *
 * These live on **two different hosts**, which is why each call declares a full `@Url`
 * rather than relying on one base URL:
 * - `api.instagram.com` — turning the login code into a token
 * - `graph.instagram.com` — everything after that
 *
 * Getting the host wrong returns a confusing "invalid platform app" error rather than
 * anything that points at the real problem.
 */
interface InstagramAuthApi {

    /**
     * Step 1: the code from the login redirect becomes a **1-hour** token.
     * Form-encoded, not JSON — Meta rejects a JSON body here.
     */
    @FormUrlEncoded
    @POST
    suspend fun exchangeCodeForShortLivedToken(
        @Url url: String = SHORT_LIVED_TOKEN_URL,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
    ): ShortLivedTokenDto

    /** Step 2: the 1-hour token becomes a **60-day** token. */
    @GET
    suspend fun exchangeForLongLivedToken(
        @Url url: String = LONG_LIVED_TOKEN_URL,
        @Query("grant_type") grantType: String = "ig_exchange_token",
        @Query("client_secret") clientSecret: String,
        @Query("access_token") accessToken: String,
    ): LongLivedTokenDto

    /** Extends a 60-day token by another 60. Only works if it is ≥24 hours old. */
    @GET
    suspend fun refreshLongLivedToken(
        @Url url: String = REFRESH_TOKEN_URL,
        @Query("grant_type") grantType: String = "ig_refresh_token",
        @Query("access_token") accessToken: String,
    ): LongLivedTokenDto

    /** Who we just connected as. */
    @GET
    suspend fun getProfile(
        @Url url: String = PROFILE_URL,
        @Query("fields") fields: String = "user_id,username,account_type,profile_picture_url",
        @Query("access_token") accessToken: String,
    ): InstagramProfileDto

    companion object {
        const val SHORT_LIVED_TOKEN_URL = "https://api.instagram.com/oauth/access_token"
        const val LONG_LIVED_TOKEN_URL = "https://graph.instagram.com/access_token"
        const val REFRESH_TOKEN_URL = "https://graph.instagram.com/refresh_access_token"
        const val PROFILE_URL = "https://graph.instagram.com/me"

        /** The permissions autoinsta needs: read the profile, and publish. */
        val SCOPES = listOf(
            "instagram_business_basic",
            "instagram_business_content_publish",
        )

        /**
         * The page the WebView opens. The user logs in here and approves; Instagram then
         * redirects to [BuildConfig.OAUTH_REDIRECT_URI] with `?code=...` appended, which
         * the WebView intercepts.
         */
        fun authorizationUrl(): String =
            "https://www.instagram.com/oauth/authorize" +
                "?client_id=${BuildConfig.META_APP_ID}" +
                "&redirect_uri=${BuildConfig.OAUTH_REDIRECT_URI}" +
                "&response_type=code" +
                "&scope=${SCOPES.joinToString(",")}"
    }
}
