package com.autoinsta.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.autoinsta.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Owns the media files this app keeps on disk.
 *
 * ## Why this exists
 * Android's Photo Picker hands back an address like
 * `content://media/picker/0/.../media/1000000033` plus a read permission that lives
 * only as long as the app's process. autoinsta stores that address and opens it
 * hours or days later from a background worker — by which time the process has died
 * and the permission with it. The address still looks fine; reading it throws
 * `SecurityException`.
 *
 * `takePersistableUriPermission` does not rescue this: persistable grants only come
 * from `ACTION_OPEN_DOCUMENT`, not the Photo Picker.
 *
 * So we copy the bytes into our own storage the moment a post is scheduled. The copy
 * survives process death, reboot, and the user deleting the original from their gallery.
 *
 * The copy is a raw stream copy — **no decoding, no re-encoding, no resizing** — so
 * image and video quality is bit-for-bit identical to what the user picked.
 */
class MediaFileStore(
    private val context: Context,
) {

    /** `<app filesDir>/media` — private to this app, no permissions needed to read it back. */
    private val mediaDir: File
        get() = File(context.filesDir, MEDIA_DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * Copies [sourceUri] into app-private storage.
     *
     * @return the absolute path of the stored file — this is what goes in the database.
     * @throws IOException if the source cannot be opened (e.g. the grant already expired).
     */
    suspend fun import(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val extension = extensionFor(sourceUri)
        val destination = File(mediaDir, "${UUID.randomUUID()}$extension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Could not open media source: $sourceUri")

        destination.absolutePath
    }

    /** Pixel dimensions of a stored file, or 0x0 if they cannot be read. */
    data class Dimensions(val widthPx: Int, val heightPx: Int)

    /**
     * Measure an imported file without loading it into memory.
     *
     * `inJustDecodeBounds` reads only the header, so a 40-megapixel export costs nothing
     * to measure. This has to happen locally and before upload: the compose screen needs
     * to know whether Instagram will accept the shape *while the owner is still looking
     * at it*, and Cloudinary only reports dimensions after the file is already sent.
     */
    suspend fun measure(path: String, mediaType: MediaType): Dimensions =
        withContext(Dispatchers.IO) {
            if (mediaType == MediaType.VIDEO) return@withContext measureVideo(path)

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeFile(path, options) }
            Dimensions(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
        }

    private fun measureVideo(path: String): Dimensions {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            Dimensions(w?.toIntOrNull() ?: 0, h?.toIntOrNull() ?: 0)
        } catch (e: Exception) {
            // A video we cannot measure is not a failure — Instagram's video rules are
            // about codec and duration, and MediaFit treats 0x0 as Unknown.
            Dimensions(0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * A small JPEG of one piece of media, for showing to the caption coach.
     *
     * Three problems solved in one place, all of which would otherwise reach the network
     * layer:
     *
     * 1. **The address may be either kind.** Media just chosen in the picker is still a
     *    `content://` grant; media loaded from a saved post is a path in our own storage.
     *    The coach is used at both moments — most often the first.
     * 2. **A Reel is a video**, which no vision model reads. A frame stands in for it, taken
     *    at [COACH_VIDEO_FRAME_PERCENT] through, because a speedpaint's first frame is a
     *    blank canvas. Same reasoning as the queue's thumbnails.
     * 3. **Art exports are enormous.** A 40-megapixel PNG base64s into tens of megabytes,
     *    which is slow on mobile data and gets rejected outright. It is downscaled and
     *    re-encoded here — the stored original is never touched, exactly as [MediaFit] does
     *    it for publishing.
     *
     * @return JPEG bytes, or null if nothing readable could be produced.
     */
    suspend fun coachSnapshot(uri: String, mediaType: MediaType): ByteArray? =
        withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                if (mediaType == MediaType.VIDEO) videoFrame(uri) else decodeScaled(uri)
            }.onFailure {
                // Silence here is what made the first version of this method impossible to
                // diagnose from the app alone.
                Log.w(TAG, "coachSnapshot failed for $uri", it)
            }.getOrNull() ?: return@withContext null

            runCatching {
                java.io.ByteArrayOutputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, COACH_JPEG_QUALITY, out)
                    out.toByteArray()
                }
            }.also { bitmap.recycle() }.getOrNull()
        }

    /** Reads either kind of address — a picker grant or one of our own files. */
    private fun openStream(uri: String): java.io.InputStream? =
        if (uri.startsWith(CONTENT_SCHEME)) {
            context.contentResolver.openInputStream(Uri.parse(uri))
        } else {
            File(uri).takeIf { it.isFile && it.canRead() }?.inputStream()
        }

    /**
     * Decodes at roughly [COACH_MAX_EDGE_PX], never full size.
     *
     * `inSampleSize` only halves, so the result can be up to twice the target — which is
     * fine, and far better than decoding a 40-megapixel bitmap to shrink it afterwards.
     * That allocation alone can take the app out on a large export.
     */
    private fun decodeScaled(uri: String): android.graphics.Bitmap? {
        val header = openStream(uri) ?: run {
            Log.w(TAG, "coachSnapshot: could not open $uri")
            return null
        }

        // A header-only decode returns null BY CONTRACT — the answer arrives on `bounds`,
        // not as a return value. Testing this result (`?: return null`) makes the whole
        // method fail on every image, silently, because nothing throws.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        header.use { BitmapFactory.decodeStream(it, null, bounds) }

        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) {
            Log.w(TAG, "coachSnapshot: no readable image header in $uri")
            return null
        }

        val pixels = openStream(uri) ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(longEdge)
        }
        return pixels.use { BitmapFactory.decodeStream(it, null, options) }
            ?: run {
                Log.w(TAG, "coachSnapshot: decode failed for $uri (${longEdge}px)")
                null
            }
    }

    /** A frame from part-way through, scaled down. Null if the video will not open. */
    private fun videoFrame(uri: String): android.graphics.Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.startsWith(CONTENT_SCHEME)) {
                retriever.setDataSource(context, Uri.parse(uri))
            } else {
                retriever.setDataSource(uri)
            }
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val atMicros = (durationMs * 1000L * COACH_VIDEO_FRAME_PERCENT).toLong()
            retriever
                .getFrameAtTime(atMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let(::shrink)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Brings a decoded frame down to [COACH_MAX_EDGE_PX] on its long edge. */
    private fun shrink(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= COACH_MAX_EDGE_PX) return bitmap

        val scale = COACH_MAX_EDGE_PX.toFloat() / longEdge
        val scaled = android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** True if [path] points at a file we imported and it is still readable. */
    fun exists(path: String): Boolean = File(path).let { it.isFile && it.canRead() }

    /**
     * Deletes one imported file. Safe to call with a path that is already gone.
     * Ignores anything outside our media directory — a stray database value should
     * never be able to delete arbitrary files.
     */
    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile?.absolutePath == mediaDir.absolutePath) {
            file.delete()
        }
        Unit
    }

    suspend fun deleteAll(paths: List<String>) {
        paths.forEach { delete(it) }
    }

    /**
     * Picks a file extension for the copy, so the file on disk still looks like what
     * it is. The extension is cosmetic — Cloudinary and the Graph API go by the actual
     * bytes and the declared MIME type — but a directory full of `.bin` is miserable
     * to debug.
     *
     * Two sources, in order:
     * 1. The MIME type from the resolver. This is what real Photo Picker `content://`
     *    URIs give us, and it is the reliable one.
     * 2. The extension visible in the URI itself. `getType()` returns null for
     *    `file://` URIs, so without this fallback anything not from the picker would
     *    land as `.bin`.
     */
    private fun extensionFor(uri: Uri): String {
        val fromMimeType = context.contentResolver.getType(uri)?.let { mimeType ->
            when {
                mimeType.endsWith("/jpeg") -> ".jpg"
                mimeType.endsWith("/png") -> ".png"
                mimeType.endsWith("/webp") -> ".webp"
                mimeType.endsWith("/heic") || mimeType.endsWith("/heif") -> ".heic"
                mimeType.endsWith("/mp4") -> ".mp4"
                mimeType.endsWith("/quicktime") -> ".mov"
                mimeType.startsWith("video/") -> ".mp4"
                mimeType.startsWith("image/") -> ".jpg"
                else -> null
            }
        }
        if (fromMimeType != null) return fromMimeType

        val fromPath = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_EXTENSION_LENGTH && it.all(Char::isLetterOrDigit) }

        return if (fromPath != null) ".$fromPath" else DEFAULT_EXTENSION
    }

    companion object {
        const val MEDIA_DIR_NAME = "media"
        private const val TAG = "MediaFileStore"
        private const val DEFAULT_EXTENSION = ".bin"
        private const val MAX_EXTENSION_LENGTH = 5
        private const val CONTENT_SCHEME = "content://"

        /**
         * How much to divide the image by while decoding, as a power of two — the only
         * thing `inSampleSize` accepts.
         *
         * In the companion object so it can be unit-tested on the JVM without a Context.
         * The decode around it needs a device and cannot be, which is exactly how a bug
         * here stayed invisible once already.
         */
        @androidx.annotation.VisibleForTesting
        internal fun sampleSizeFor(longEdge: Int): Int {
            var sample = 1
            while (longEdge / (sample * 2) >= COACH_MAX_EDGE_PX) sample *= 2
            return sample
        }

        /**
         * Long edge sent to the coach. Larger buys nothing — the API resizes anything
         * bigger before the model sees it, so the extra pixels are paid for in upload
         * time on mobile data and thrown away.
         */
        private const val COACH_MAX_EDGE_PX = 1568

        private const val COACH_JPEG_QUALITY = 85

        /** Late enough that a timelapse shows the finished piece, not a blank canvas. */
        private const val COACH_VIDEO_FRAME_PERCENT = 0.85
    }
}
