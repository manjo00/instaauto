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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.autoinsta.ui.components.mediaModel
import com.autoinsta.ui.components.ExactAlarmBanner
import com.autoinsta.ui.components.openExactAlarmSettings
import com.autoinsta.BuildConfig
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The live queue of scheduled posts. Tap a card to edit it; the trash icon
 * deletes (after confirmation); the FAB starts a new post.
 */
@Composable
fun HomeScreen(
    onCreatePost: () -> Unit,
    onEditPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoInstaApp
    val viewModel: HomeViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(app.postRepository) as T
            }
        },
    )

    val posts by viewModel.scheduledPosts.collectAsState()
    val canScheduleExact by viewModel.canScheduleExact.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshExactAlarmAvailability() }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreatePost,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New post") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!canScheduleExact) {
                item(key = "exact-alarm-banner") {
                    ExactAlarmBanner(onFixClick = { openExactAlarmSettings(context) })
                }
            }
            if (posts.isEmpty()) {
                item(key = "empty") { EmptyQueue() }
            } else {
                items(posts, key = { it.post.id }) { item ->
                    QueueItemCard(
                        item = item,
                        onClick = { onEditPost(item.post.id) },
                        onDeleteClick = { pendingDeleteId = item.post.id },
                        onFireSoonClick = { viewModel.fireSoon(item.post.id) },
                    )
                }
            }
        }
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this post?") },
            text = { Text("This removes it from the queue. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePost(deleteId)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyQueue() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Nothing scheduled yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Tap \"New post\" to plan your next upload — pick media, write a caption, and choose when it goes live.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// Built per-call rather than held in a static: the device locale can change while
// the app is running, and SimpleDateFormat is not thread-safe to share.
private fun dateTimeFormat() = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())

@Composable
private fun QueueItemCard(
    item: ScheduledPostWithMedia,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFireSoonClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(item.mediaItems.firstOrNull())

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = postTypeIcon(item.post.postType),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = postTypeLabel(item.post.postType),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Text(
                    text = item.post.caption.ifBlank { "(no caption)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = dateTimeFormat().format(item.post.scheduledAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Debug builds only: re-time this post to fire in ~20s so the whole
            // alarm -> worker -> notification path can be checked in one sitting.
            if (BuildConfig.DEBUG) {
                IconButton(onClick = onFireSoonClick) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Fire in 20 seconds (debug)",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(media: MediaItemEntity?) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(64.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                media == null -> Icon(Icons.Default.PhotoCamera, contentDescription = null)
                media.mediaType == MediaType.VIDEO -> Icon(Icons.Default.Movie, contentDescription = null)
                else -> AsyncImage(
                    model = mediaModel(media.localUri),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

private fun postTypeIcon(type: PostType) = when (type) {
    PostType.SINGLE_IMAGE -> Icons.Default.PhotoCamera
    PostType.REEL -> Icons.Default.Movie
    PostType.CAROUSEL -> Icons.Default.Collections
}

private fun postTypeLabel(type: PostType) = when (type) {
    PostType.SINGLE_IMAGE -> "Post"
    PostType.REEL -> "Reel"
    PostType.CAROUSEL -> "Carousel"
}
