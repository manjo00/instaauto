package com.autoinsta.data.remote

import com.autoinsta.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds the HTTP stack. Hand-wired rather than injected, matching how the rest of the
 * app is assembled in [com.autoinsta.AutoInstaApp].
 */
object NetworkModule {

    private val json = Json {
        // Meta adds fields without warning; unknown ones must not blow up parsing.
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Uploads to Cloudinary in Phase 5 can be large and slow; alarms fire in the
        // background where a hung request would hold a wakelock.
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC, not BODY: token responses would otherwise print the access
                // token into logcat, where anything on the device could read it.
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                )
            }
        }
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            // Every call supplies its own @Url — Meta's auth spans two hosts — but
            // Retrofit still insists on a base URL being set.
            .baseUrl("https://graph.instagram.com/")
            .client(okHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val instagramAuthApi: InstagramAuthApi by lazy {
        retrofit.create(InstagramAuthApi::class.java)
    }
}
