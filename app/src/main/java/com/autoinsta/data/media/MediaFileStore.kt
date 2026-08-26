package com.autoinsta.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
        private const val DEFAULT_EXTENSION = ".bin"
        private const val MAX_EXTENSION_LENGTH = 5
    }
}
