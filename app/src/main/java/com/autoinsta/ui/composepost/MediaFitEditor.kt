package com.autoinsta.ui.composepost

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.autoinsta.domain.MediaFit
import com.autoinsta.ui.components.mediaModel
import kotlin.math.roundToInt

/**
 * Full-screen editor for deciding how one image is fitted into the shape Instagram accepts.
 *
 * ## Why this screen exists
 * Instagram only accepts images between 4:5 and 1.91:1. A lot of art falls outside that,
 * and something has to give. Doing it silently would mean either bars appearing without
 * warning or — worse — a composition quietly cropped in half. So the owner sees exactly
 * what will happen and chooses.
 *
 * **Fit** keeps the whole piece and adds bars. **Crop** fills the frame, and the owner
 * drags to choose which part survives. The dimmed area is what gets discarded, so the
 * cost of cropping is visible rather than described.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaFitEditor(
    media: PickedMedia,
    /** Carousels share one shape, set by the first item — shown so it isn't a surprise. */
    sharedRatioNote: String? = null,
    onSave: (mode: MediaFit.Mode, cropOffset: Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember(media.uri) {
        mutableStateOf(
            // An image Instagram already accepts needs no decision; anything else
            // defaults to the safe option.
            if (MediaFit.isAcceptable(media.widthPx, media.heightPx)) MediaFit.Mode.AS_IS
            else media.fitMode
        )
    }
    var offset by remember(media.uri) { mutableFloatStateOf(media.cropOffset) }

    val verdict = MediaFit.verdictFor(media.widthPx, media.heightPx)
    val alreadyFine = verdict is MediaFit.Verdict.Acceptable
    val unmeasured = verdict is MediaFit.Verdict.Unknown

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Fit for Instagram") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(mode, offset) }) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text("Done", modifier = Modifier.padding(start = 4.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    unmeasured -> Text(
                        "Can't measure this file, so it'll be sent as it is.",
                        color = Color.White,
                        modifier = Modifier.padding(24.dp),
                    )
                    else -> FramedPreview(
                        media = media,
                        mode = mode,
                        offset = offset,
                        onOffsetChange = { offset = it.coerceIn(0f, 1f) },
                        verdict = verdict,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (alreadyFine) {
                    Text(
                        "This one already fits — Instagram will show it exactly as it is.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!unmeasured) {
                    ModeSelector(selected = mode, onSelect = { mode = it })

                    MediaFit.explain(verdict, mode)?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (mode == MediaFit.Mode.CROP) {
                        val lost = (MediaFit.croppedAwayFraction(media.widthPx, media.heightPx) * 100)
                            .roundToInt()
                        Text(
                            text = "Drag the picture to choose what stays. " +
                                "About $lost% of it won't be shown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                sharedRatioNote?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selected: MediaFit.Mode,
    onSelect: (MediaFit.Mode) -> Unit,
) {
    val options = listOf(
        MediaFit.Mode.PAD to ("Fit" to Icons.Default.Fullscreen),
        MediaFit.Mode.CROP to ("Crop" to Icons.Default.CropFree),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Icon(label.second, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label.first, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * The image with Instagram's frame drawn over it.
 *
 * In **Fit** the whole picture is shown inside the frame, so the bars are literally what
 * will be published. In **Crop** the picture fills the frame and everything outside is
 * dimmed — dragging moves which part is inside.
 */
@Composable
private fun FramedPreview(
    media: PickedMedia,
    mode: MediaFit.Mode,
    offset: Float,
    onOffsetChange: (Float) -> Unit,
    verdict: MediaFit.Verdict,
) {
    val targetRatio = MediaFit.nearestAllowedRatio(media.widthPx, media.heightPx).toFloat()
    val isTall = verdict is MediaFit.Verdict.TooTall

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(targetRatio)
                .clipToBounds()
        ) {
            AsyncImage(
                model = mediaModel(media.uri),
                contentDescription = "Preview",
                contentScale = if (mode == MediaFit.Mode.CROP) ContentScale.Crop else ContentScale.Fit,
                alignment = when {
                    mode != MediaFit.Mode.CROP -> Alignment.Center
                    // Map 0..1 onto Compose's -1..1 bias so dragging tracks the finger.
                    isTall -> androidx.compose.ui.BiasAlignment(0f, (offset * 2f) - 1f)
                    else -> androidx.compose.ui.BiasAlignment((offset * 2f) - 1f, 0f)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (mode == MediaFit.Mode.PAD) Color.White else Color.Transparent)
                    .then(
                        if (mode != MediaFit.Mode.CROP) Modifier
                        else if (isTall) {
                            Modifier.pointerInput(media.uri, isTall) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    // A full drag across the frame sweeps the whole range.
                                    onOffsetChange(offset - dragAmount / size.height)
                                }
                            }
                        } else {
                            Modifier.pointerInput(media.uri, isTall) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    onOffsetChange(offset - dragAmount / size.width)
                                }
                            }
                        }
                    ),
            )

            // The frame outline, drawn on top so it reads as "this is what Instagram shows".
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            color = Color.White.copy(alpha = 0.9f),
                            size = Size(size.width, size.height),
                            style = Stroke(width = 3f),
                        )
                        // Rule-of-thirds guides, faint, to help position a subject.
                        val thirdW = size.width / 3f
                        val thirdH = size.height / 3f
                        val guide = Color.White.copy(alpha = 0.25f)
                        listOf(thirdW, thirdW * 2).forEach { x ->
                            drawLine(guide, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                        }
                        listOf(thirdH, thirdH * 2).forEach { y ->
                            drawLine(guide, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                        }
                    },
            )
        }
    }
}
