package com.autoinsta.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFramePercent
import com.autoinsta.domain.model.MediaType

/**
 * A post's picture, for a list.
 *
 * ## Why videos get special treatment
 * A Reel used to draw a grey film icon, which meant a queue of three timelapses looked
 * like three identical rows — impossible to reorder deliberately, which is the one thing
 * the queue exists to let you do. Coil's `VideoFrameDecoder` (registered app-wide in
 * `AutoInstaApp`) pulls a real frame out instead.
 *
 * **Which frame matters.** The default is the first one, and the first frame of a
 * speedpaint is a blank canvas — every timelapse would still look identical. So this asks
 * for a frame [VIDEO_FRAME_PERCENT] of the way through, where the piece is finished enough
 * to recognise but before any outro card.
 */
@Composable
fun MediaThumbnail(
    localUri: String?,
    mediaType: MediaType?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.size(size),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (localUri == null) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                return@Box
            }

            val isVideo = mediaType == MediaType.VIDEO
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(mediaModel(localUri))
                    .apply { if (isVideo) videoFramePercent(VIDEO_FRAME_PERCENT) }
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )

            // A frame alone reads as a photo. The badge says "this one moves".
            if (isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(1.dp),
                    )
                }
            }
        }
    }
}

/**
 * How far into a video to grab the thumbnail.
 *
 * Far enough that a speedpaint shows recognisable artwork, short of the end where an
 * outro or a signature card would tell you nothing about which piece it is.
 */
private const val VIDEO_FRAME_PERCENT = 0.85
