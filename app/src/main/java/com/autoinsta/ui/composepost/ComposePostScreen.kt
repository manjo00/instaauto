package com.autoinsta.ui.composepost

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.autoinsta.ui.components.mediaModel
import com.autoinsta.ui.components.ExactAlarmBanner
import com.autoinsta.ui.components.openExactAlarmSettings
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.HashtagPresetEntity
import com.autoinsta.domain.PostValidator
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Create/edit screen for a scheduled post. When [postId] is non-null the form is
 * pre-filled from the existing row and Save performs an update.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposePostScreen(
    postId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoInstaApp

    val viewModel: ComposePostViewModel = viewModel(
        key = "compose_post_${postId ?: "new"}",
        factory = remember(postId) {
            viewModelFactoryOf {
                ComposePostViewModel(
                    postId = postId,
                    postRepository = app.postRepository,
                    presetRepository = app.presetRepository,
                )
            }
        },
    )

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        // The user may have just come back from the Settings page that grants this.
        viewModel.refreshExactAlarmAvailability()
    }

    LaunchedEffect(state.saveComplete) {
        if (state.saveComplete) {
            viewModel.consumeSaveComplete()
            onNavigateBack()
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.addMedia(listOf(it.toPickedMedia(context))) }
    }
    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(PostValidator.CAROUSEL_MAX_ITEMS),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMedia(uris.map { it.toPickedMedia(context) })
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit post" else "New post") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PostTypeSelector(
                selected = state.postType,
                onSelect = viewModel::setPostType,
            )

            MediaPicker(
                media = state.media,
                postType = state.postType,
                onAddClick = {
                    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    if (state.postType == PostType.CAROUSEL) {
                        multiPickerLauncher.launch(request)
                    } else {
                        singlePickerLauncher.launch(request)
                    }
                },
                onRemove = viewModel::removeMedia,
            )

            OutlinedTextField(
                value = state.caption,
                onValueChange = viewModel::setCaption,
                label = { Text("Caption") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            HashtagSection(
                presets = state.presets,
                selectedPresetId = state.selectedPresetId,
                hashtags = state.hashtags,
                onPresetSelected = viewModel::selectPreset,
                onHashtagsChange = viewModel::setHashtags,
            )

            ScheduleSection(
                scheduledAtMillis = state.scheduledAtMillis,
                onScheduledAtChange = viewModel::setScheduledAt,
            )

            if (!state.canScheduleExact) {
                ExactAlarmBanner(onFixClick = { openExactAlarmSettings(context) })
            }

            MissedPostSection(
                selected = state.missedPolicy,
                onSelect = viewModel::setMissedPolicy,
            )

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (state.isEditing) "Save changes" else "Schedule post")
                }
            }
        }
    }
}

@Composable
private fun PostTypeSelector(
    selected: PostType,
    onSelect: (PostType) -> Unit,
) {
    val options = listOf(
        PostType.SINGLE_IMAGE to "Post",
        PostType.REEL to "Reel",
        PostType.CAROUSEL to "Carousel",
    )
    Column {
        Text("Post type", style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.padding(top = 8.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = selected == type,
                        onClick = { onSelect(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaPicker(
    media: List<PickedMedia>,
    postType: PostType,
    onAddClick: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    val helper = when (postType) {
        PostType.CAROUSEL -> "Pick 2–10 photos or videos, in order."
        PostType.REEL -> "Pick one vertical video."
        PostType.SINGLE_IMAGE -> "Pick one photo or video."
    }
    Column {
        Text("Media", style = MaterialTheme.typography.labelLarge)
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(media.size) { index ->
                MediaThumbnail(
                    media = media[index],
                    onRemove = { onRemove(index) },
                )
            }
            val canAddMore = when (postType) {
                PostType.CAROUSEL -> media.size < PostValidator.CAROUSEL_MAX_ITEMS
                else -> media.isEmpty()
            }
            if (canAddMore) {
                item {
                    AddMediaCard(onClick = onAddClick)
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(media: PickedMedia, onRemove: () -> Unit) {
    Box(
        modifier = Modifier.size(110.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (media.mediaType == MediaType.VIDEO) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Video",
                        modifier = Modifier.size(36.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = mediaModel(media.uri),
                    contentDescription = "Selected media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp),
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun AddMediaCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(110.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add media")
                Text("Add", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HashtagSection(
    presets: List<HashtagPresetEntity>,
    selectedPresetId: Long?,
    hashtags: String,
    onPresetSelected: (HashtagPresetEntity?) -> Unit,
    onHashtagsChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPreset = presets.find { it.id == selectedPresetId }

    Column {
        Text("Hashtags", style = MaterialTheme.typography.labelLarge)
        Spacer(8)

        if (presets.isNotEmpty()) {
            Box {
                OutlinedTextField(
                    value = selectedPreset?.name ?: "No preset",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Preset") },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose preset")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    DropdownMenuItem(
                        text = { Text("No preset (free text)") },
                        onClick = {
                            onPresetSelected(null)
                            expanded = false
                        },
                    )
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.name) },
                            onClick = {
                                onPresetSelected(preset)
                                expanded = false
                            },
                        )
                    }
                }
            }
            Spacer(8)
        }

        OutlinedTextField(
            value = hashtags,
            onValueChange = onHashtagsChange,
            label = { Text("Hashtags") },
            placeholder = { Text("#digitalart #illustration") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Spacer(heightDp: Int) {
    Box(modifier = Modifier.height(heightDp.dp))
}

// Built per-call rather than held in a static: the device locale can change while
// the app is running, and SimpleDateFormat is not thread-safe to share.
private fun dateFormat() = SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault())
private fun timeFormat() = SimpleDateFormat("h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(
    scheduledAtMillis: Long,
    onScheduledAtChange: (Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val calendar = remember(scheduledAtMillis) {
        Calendar.getInstance().apply { timeInMillis = scheduledAtMillis }
    }

    Column {
        Text("Publish at", style = MaterialTheme.typography.labelLarge)
        Spacer(8)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text(dateFormat().format(calendar.time))
            }
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                Text(timeFormat().format(calendar.time))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = scheduledAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val pickedMillis = datePickerState.selectedDateMillis
                    if (pickedMillis != null) {
                        val newCal = Calendar.getInstance().apply { timeInMillis = pickedMillis }
                        val merged = Calendar.getInstance().apply {
                            timeInMillis = scheduledAtMillis
                            set(Calendar.YEAR, newCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, newCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, newCal.get(Calendar.DAY_OF_MONTH))
                        }
                        onScheduledAtChange(merged.timeInMillis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = false,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val merged = Calendar.getInstance().apply {
                                timeInMillis = scheduledAtMillis
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE, timePickerState.minute)
                                set(Calendar.SECOND, 0)
                            }
                            onScheduledAtChange(merged.timeInMillis)
                            showTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

/**
 * Tiny `ViewModelProvider.Factory` shim — avoids pulling in the `viewModelFactory`
 * DSL (whose extension surface varies across lifecycle releases) and keeps
 * construction explicit: we hand the ViewModel its repositories ourselves
 * (manual DI, matching the rest of the app — see AutoInstaApp).
 */
private fun <VM : ViewModel> viewModelFactoryOf(factory: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
    }

private fun Uri.toPickedMedia(context: android.content.Context): PickedMedia {
    val mime = context.contentResolver.getType(this)
    val type = if (mime?.startsWith("video/") == true) MediaType.VIDEO else MediaType.IMAGE
    return PickedMedia(uri = toString(), mediaType = type)
}

/**
 * What happens if the phone is off or dead when this post was due.
 *
 * Per post rather than one app-wide setting, because the right answer genuinely
 * differs: a time-of-day post landing hours late is worse than not landing, while an
 * evergreen piece is fine whenever.
 */
@Composable
private fun MissedPostSection(
    selected: MissedPostPolicy,
    onSelect: (MissedPostPolicy) -> Unit,
) {
    val options = listOf(
        Triple(
            MissedPostPolicy.POST_IF_RECENT,
            "Post if under an hour late",
            "Skips it if your phone was off longer than that.",
        ),
        Triple(
            MissedPostPolicy.POST_ANYWAY,
            "Post it anyway",
            "Goes out whenever the phone is back, however late.",
        ),
        Triple(
            MissedPostPolicy.ASK_ME,
            "Ask me first",
            "Waits in the queue until you decide.",
        ),
    )

    Column {
        Text("If the phone is off at that time", style = MaterialTheme.typography.labelLarge)
        Spacer(8)
        options.forEach { (policy, title, explanation) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(policy) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(
                    selected = selected == policy,
                    onClick = { onSelect(policy) },
                )
                Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
