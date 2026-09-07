package com.autoinsta.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.autoinsta.data.db.relations.DonePostRow
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.ui.components.MediaThumbnail
import com.autoinsta.ui.queue.momentLabel

/**
 * The **Done** tab: everything that has been through the publisher.
 *
 * One row per post rather than per attempt — a piece that failed on Tuesday and went out
 * on Wednesday is one piece of art with a bumpy history, and two rows would make a list of
 * your work read like a log file. The bumps show up as "after 1 failed attempt".
 *
 * Two ways back: **put it in the queue** to let it take its turn, or **⚡** to publish it
 * again straight away. Repeating something that already went out asks first — the cost of
 * a mis-tap there is a duplicate on the real account.
 */
@Composable
fun DoneList(
    posts: List<DonePostRow>,
    contentPadding: PaddingValues,
    onReturnToQueue: (Long) -> Unit,
    onPostNow: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf<Confirmation?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (posts.isEmpty()) {
            item(key = "done-empty") { NothingDoneYet() }
        } else {
            items(posts, key = { "done-${it.id}" }) { row ->
                DoneCard(
                    row = row,
                    onReturnToQueue = {
                        if (row.status == PostStatus.POSTED) {
                            confirming = Confirmation(row, again = false)
                        } else {
                            onReturnToQueue(row.id)
                        }
                    },
                    onPostNow = {
                        if (row.status == PostStatus.POSTED) {
                            confirming = Confirmation(row, again = true)
                        } else {
                            onPostNow(row.id)
                        }
                    },
                )
            }
        }
    }

    confirming?.let { c ->
        val whenPosted = c.row.postedAt?.let { momentLabel(it) } ?: "earlier"
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Post this again?") },
            text = {
                Text(
                    "This already went out on $whenPosted. " +
                        if (c.again) {
                            "Posting it now will put the same piece on your account twice."
                        } else {
                            "Putting it back in the queue will post the same piece a second time."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (c.again) onPostNow(c.row.id) else onReturnToQueue(c.row.id)
                    confirming = null
                }) { Text(if (c.again) "Post again now" else "Queue it again") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel") }
            },
        )
    }
}

private data class Confirmation(val row: DonePostRow, val again: Boolean)

@Composable
private fun DoneCard(
    row: DonePostRow,
    onReturnToQueue: () -> Unit,
    onPostNow: () -> Unit,
) {
    val posted = row.status == PostStatus.POSTED
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DoneThumbnail(row)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (posted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (posted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = outcomeLabel(row),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (posted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                Text(
                    text = row.caption.ifBlank { "(no caption)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )

                // Why it failed is the whole reason to look at this list.
                row.errorMessage?.takeIf { !posted }?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (posted && row.failedAttempts > 0) {
                    Text(
                        text = "after ${row.failedAttempts} failed " +
                            if (row.failedAttempts == 1) "attempt" else "attempts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            IconButton(onClick = onReturnToQueue) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Put back in the queue",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onPostNow) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Post this now",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun outcomeLabel(row: DonePostRow): String = when {
    row.status == PostStatus.POSTED && row.postedAt != null -> "Posted ${momentLabel(row.postedAt)}"
    row.status == PostStatus.POSTED -> "Posted"
    row.postedAt != null -> "Didn't go out · ${momentLabel(row.postedAt)}"
    else -> "Didn't go out"
}

@Composable
private fun DoneThumbnail(row: DonePostRow) {
    MediaThumbnail(localUri = row.localUri, mediaType = row.mediaType)
}

@Composable
private fun NothingDoneYet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Nothing has gone out yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Once a post publishes — or fails — it'll appear here, with what " +
                "happened and a way to send it again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
