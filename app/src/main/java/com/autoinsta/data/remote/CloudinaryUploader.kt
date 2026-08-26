package com.autoinsta.data.remote

import com.autoinsta.BuildConfig
import com.autoinsta.data.remote.dto.CloudinaryUploadDto
import com.autoinsta.domain.MediaFit
import com.autoinsta.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Puts a local file somewhere Instagram can reach it.
 *
 * ## Why this exists at all
 * Instagram never accepts an upload. It fetches the media itself from a public address —
 * Meta: *"we cURL media used in publishing attempts, so the media must be hosted on a
 * publicly accessible server."* The app has no server, so Cloudinary's free tier holds
 * the file for the moment it takes Instagram to collect it.
 *
 * ## Unsigned uploads, and what that shapes
 * The app uploads with an **unsigned preset**, so no Cloudinary secret ships inside the
 * APK. The trade-off is that unsigned uploads accept almost no parameters — no resizing,
 * no format conversion at upload time.
 *
 * That turns out to be the better arrangement anyway: the original is stored exactly as
 * exported, and the fitting (JPEG conversion, width cap, padding or cropping) is applied
 * to the **delivery URL** instead. The stored artwork is never degraded, and the fit can
 * be changed later without re-uploading a thing.
 */
class CloudinaryUploader(
    private val client: OkHttpClient,
    private val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME,
    private val uploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    data class Uploaded(
        val publicId: String,
        val secureUrl: String,
        val widthPx: Int,
        val heightPx: Int,
        val resourceType: String,
    )

    /** True when the app has been given Cloudinary credentials to work with. */
    fun isConfigured(): Boolean = cloudName.isNotBlank() && uploadPreset.isNotBlank()

    /**
     * Upload [file] and return where it landed.
     *
     * @throws IOException with a readable reason — this runs inside a background worker
     *         whose only output is a notification, so the message has to stand alone.
     */
    suspend fun upload(file: File, mediaType: MediaType): Uploaded = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw IOException("Cloudinary isn't set up yet — add the upload preset in settings.")
        }
        if (!file.isFile || !file.canRead()) {
            throw IOException("The media file is missing from storage.")
        }

        val resource = if (mediaType == MediaType.VIDEO) "video" else "image"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(contentTypeFor(file, mediaType)),
            )
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/$resource/upload")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val dto = runCatching { json.decodeFromString<CloudinaryUploadDto>(raw) }.getOrNull()

            if (!response.isSuccessful) {
                // Cloudinary puts the real reason in the body; the HTTP code alone
                // ("400") tells the owner nothing actionable.
                val reason = dto?.error?.message ?: "Upload failed (HTTP ${response.code})."
                throw IOException(reason)
            }

            val publicId = dto?.publicId
                ?: throw IOException("Cloudinary accepted the file but returned no id.")

            Uploaded(
                publicId = publicId,
                secureUrl = dto.secureUrl.orEmpty(),
                widthPx = dto.width ?: 0,
                heightPx = dto.height ?: 0,
                resourceType = dto.resourceType ?: resource,
            )
        }
    }

    /**
     * The address handed to Instagram, with the fitting baked in.
     *
     * Rebuilt from the public id rather than using Cloudinary's returned `secure_url`,
     * because that one points at the untouched original — which may be a PNG, or too
     * tall, and would be rejected.
     */
    fun deliveryUrl(
        uploaded: Uploaded,
        mode: MediaFit.Mode,
        cropOffset: Float = 0.5f,
        padColour: String = MediaFit.DEFAULT_PAD_COLOUR,
    ): String {
        if (uploaded.resourceType == "video") {
            // Videos are left alone: Instagram's video rules are about codec, duration
            // and bitrate, none of which a delivery transformation should be guessing at.
            return uploaded.secureUrl
        }

        val transformation = MediaFit.transformationFor(
            widthPx = uploaded.widthPx,
            heightPx = uploaded.heightPx,
            mode = mode,
            padColour = padColour,
            cropOffset = cropOffset,
        )
        return "https://res.cloudinary.com/$cloudName/image/upload/$transformation/${uploaded.publicId}.jpg"
    }

    private fun contentTypeFor(file: File, mediaType: MediaType) =
        when {
            mediaType == MediaType.VIDEO -> "video/*"
            file.extension.equals("png", ignoreCase = true) -> "image/png"
            file.extension.equals("webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }.toMediaTypeOrNull()
}
