package com.autoinsta.ui.home

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.autoinsta.AutoInstaApp
import com.autoinsta.BuildConfig
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.domain.DragReorder
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostType
import com.autoinsta.ui.components.ExactAlarmBanner
import com.autoinsta.ui.components.mediaModel
import com.autoinsta.ui.components.openExactAlarmSettings
import com.autoinsta.ui.queue.momentLabel
import kotlinx.coroutines.delay

/**
 * The queue.
 *
 * Two sections, because they are two different promises. The **pool** is an order — what
 * goes out next, and next after that — and can be dragged. **Set times** are appointments
 * the owner made deliberately, and are left exactly where they were put.
 *
 * Press and hold a queue card (or grab its handle) to move it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSchedule: () -> Unit,
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
                    HomeViewModel(app.postRepository, app.queueRepository) as T
            }
        },
    )

    val queue by viewModel.queue.collectAsState()
    val fixedPosts by viewModel.fixedPosts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val hasSlots by viewModel.hasSlots.collectAsState()
    val canScheduleExact by viewModel.canScheduleExact.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshExactAlarmAvailability()
        viewModel.refreshPlan()
    }

    // ── Drag state ─────────────────────────────────────────────────────────
    // The list deliberately does not reflow while a card is being dragged: the card
    // follows the finger, a line shows where it will land, and the order is committed on
    // release. Reflowing mid-drag means correcting the drag offset by the height of every
    // displaced card, which is exactly the kind of arithmetic that produces jitter no
    // unit test can catch.
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragTranslation by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(autoScroll) {
        if (autoScroll == 0f) return@LaunchedEffect
        while (true) {
            listState.scrollBy(autoScroll)
            delay(16)
        }
    }

    fun queueBounds(): List<DragReorder.ItemBounds> =
        listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            val id = (info.key as? String)?.removePrefix(QUEUE_KEY_PREFIX)?.toLongOrNull()
                ?: return@mapNotNull null
            val index = queue.indexOfFirst { it.post.id == id }
            if (index < 0) null
            else DragReorder.ItemBounds(index, info.offset, info.offset + info.size)
        }

    fun endDrag() {
        draggingId = null
        dragTranslation = 0f
        targetIndex = -1
        autoScroll = 0f
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("autoinsta") },
                actions = {
                    IconButton(onClick = onOpenSchedule) {
                        Icon(Icons.Default.Schedule, contentDescription = "Posting schedule")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreatePost,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New post") },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                16.dp,
                padding.calculateTopPadding() + 16.dp,
                16.dp,
                96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!canScheduleExact) {
                item(key = "exact-alarm-banner") {
                    ExactAlarmBanner(onFixClick = { openExactAlarmSettings(context) })
                }
            }

            if (settings.paused) {
                item(key = "paused-banner") { PausedBanner(onOpenSchedule = onOpenSchedule) }
            }

            if (queue.isNotEmpty() && !hasSlots) {
                item(key = "no-slots-banner") { NoSlotsBanner(onOpenSchedule = onOpenSchedule) }
            }

            if (queue.isEmpty() && fixedPosts.isEmpty()) {
                item(key = "empty") { EmptyQueue() }
            }

            if (queue.isNotEmpty()) {
                item(key = "queue-header") {
                    SectionHeader(
                        title = "Queue",
                        subtitle = "Goes out in this order. Press and hold to move one.",
                    )
                }
                itemsIndexed(
                    items = queue,
                    key = { _, item -> "$QUEUE_KEY_PREFIX${item.post.id}" },
                ) { index, item ->
                    val isDragging = draggingId == item.post.id

                    val startDrag: (Offset) -> Unit = {
                        val info = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == "$QUEUE_KEY_PREFIX${item.post.id}" }
                        // The card's centre, not the touch point, so grabbing the handle
                        // and pressing anywhere on the card behave identically.
                        pointerY = ((info?.offset ?: 0) + (info?.size ?: 0) / 2).toFloat()
                        dragTranslation = 0f
                        targetIndex = index
                        draggingId = item.post.id
                        viewModel.beginDrag()
                    }
                    val moveDrag: (Float) -> Unit = { dy ->
                        dragTranslation += dy
                        pointerY += dy
                        targetIndex = DragReorder.targetIndexFor(index, pointerY.toInt(), queueBounds())
                        autoScroll = edgeScrollFor(pointerY, listState)
                    }
                    val finishDrag = {
                        viewModel.dropAt(index, targetIndex.takeIf { it >= 0 } ?: index)
                        endDrag()
                    }
                    val abortDrag = {
                        viewModel.cancelDrag()
                        endDrag()
                    }

                    QueueCard(
                        item = item,
                        whenLabel = queueWhenLabel(
                            atMillis = item.post.scheduledAt,
                            paused = settings.paused,
                            hasSlots = hasSlots,
                        ),
                        isDragging = isDragging,
                        showDropLine = draggingId != null && !isDragging && targetIndex == index,
                        onClick = { onEditPost(item.post.id) },
                        onDeleteClick = { pendingDeleteId = item.post.id },
                        onFireSoonClick = { viewModel.fireSoon(item.post.id) },
                        handleModifier = Modifier.pointerInput(item.post.id) {
                            detectDragGestures(
                                onDragStart = startDrag,
                                onDrag = { change, amount -> change.consume(); moveDrag(amount.y) },
                                onDragEnd = finishDrag,
                                onDragCancel = abortDrag,
                            )
                        },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragTranslation else 0f
                            }
                            .pointerInput(item.post.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = startDrag,
                                    onDrag = { change, amount ->
                                        change.consume(); moveDrag(amount.y)
                                    },
                                    onDragEnd = finishDrag,
                                    onDragCancel = abortDrag,
                                )
                            },
                    )
                }
            }

            if (fixedPosts.isNotEmpty()) {
                item(key = "fixed-header") {
                    SectionHeader(
                        title = "Set times",
                        subtitle = "Pinned to a date. The queue leaves these alone.",
                    )
                }
                items(fixedPosts, key = { "$FIXED_KEY_PREFIX${it.post.id}" }) { item ->
                    QueueCard(
                        item = item,
                        whenLabel = momentLabel(item.post.scheduledAt),
                        isDragging = false,
                        showDropLine = false,
                        onClick = { onEditPost(item.post.id) },
                        onDeleteClick = { pendingDeleteId = item.post.id },
                        onFireSoonClick = { viewModel.fireSoon(item.post.id) },
                        handleModifier = null,
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

private const val QUEUE_KEY_PREFIX = "q-"
private const val FIXED_KEY_PREFIX = "f-"

/** How close to an edge the finger has to get before the list starts scrolling itself. */
private const val EDGE_SCROLL_ZONE_PX = 220f
private const val EDGE_SCROLL_SPEED_PX = 22f

private fun edgeScrollFor(pointerY: Float, state: LazyListState): Float {
    val viewportHeight = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
    return when {
        pointerY < EDGE_SCROLL_ZONE_PX -> -EDGE_SCROLL_SPEED_PX
        pointerY > viewportHeight - EDGE_SCROLL_ZONE_PX -> EDGE_SCROLL_SPEED_PX
        else -> 0f
    }
}

/**
 * A queued post's date is only real if the queue can actually place it. Saying
 * "Wed 7:00 PM" while paused, or with no slots set, would be a straight lie.
 */
private fun queueWhenLabel(atMillis: Long, paused: Boolean, hasSlots: Boolean): String = when {
    paused -> "Paused"
    !hasSlots -> "Waiting — no posting times set"
    else -> momentLabel(atMillis)
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PausedBanner(onOpenSchedule: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PauseCircle, contentDescription = null)
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text("Queue paused", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Nothing will go out. Your posts are keeping their order.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpenSchedule) { Text("Resume") }
        }
    }
}

@Composable
private fun NoSlotsBanner(onOpenSchedule: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text("No posting times yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Your posts are waiting, but there's nothing telling the app when to " +
                        "send them.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpenSchedule) { Text("Set them") }
        }
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
            text = "Nothing waiting yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Tap \"New post\" to add a piece to the queue — pick your media, write " +
                "a caption, and it'll go out at your next posting time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun QueueCard(
    item: ScheduledPostWithMedia,
    whenLabel: String,
    isDragging: Boolean,
    showDropLine: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFireSoonClick: () -> Unit,
    /** Null for a fixed-time post, which is not reorderable. */
    handleModifier: Modifier?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showDropLine) {
            HorizontalDivider(
                thickness = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isDragging) 8.dp else 1.dp,
            ),
        ) {
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
                        text = whenLabel,
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

                if (handleModifier != null) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = handleModifier.padding(start = 4.dp),
                    )
                }
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
