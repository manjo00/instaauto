package com.autoinsta.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.HashtagPresetEntity
import com.autoinsta.domain.HashtagSet
import com.autoinsta.domain.PublishPolicy

/**
 * Saved sets of hashtags, so a set typed once can be reused for months.
 *
 * The table and the repository have been here since Phase 1 with nothing able to fill
 * them, which is why the picker on the compose screen was always empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = androidx.compose.ui.platform.LocalContext.current
        .applicationContext as AutoInstaApp
    val viewModel: PresetsViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PresetsViewModel(app.presetRepository) as T
            }
        },
    )

    val presets by viewModel.presets.collectAsState()
    val draft by viewModel.draft.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Hashtag sets") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::startNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New set") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                16.dp,
                padding.calculateTopPadding() + 16.dp,
                16.dp,
                96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (presets.isEmpty()) {
                item(key = "empty") { NoPresetsYet() }
            } else {
                items(presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onClick = { viewModel.startEditing(preset) },
                        onDeleteClick = { pendingDeleteId = preset.id },
                    )
                }
            }
        }
    }

    draft?.let { current ->
        PresetEditor(
            draft = current,
            onNameChange = viewModel::setName,
            onHashtagsChange = viewModel::setHashtags,
            onSave = viewModel::saveDraft,
            onCancel = viewModel::cancelDraft,
        )
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this set?") },
            text = {
                Text(
                    "Posts you've already made keep their hashtags — only the saved set " +
                        "goes away."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(deleteId)
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
private fun PresetCard(
    preset: HashtagPresetEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = preset.hashtags,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "${HashtagSet.count(preset.hashtags)} tags",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${preset.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PresetEditor(
    draft: PresetDraft,
    onNameChange: (String) -> Unit,
    onHashtagsChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val overLimit = draft.tagCount > PublishPolicy.MAX_HASHTAGS

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (draft.isEditing) "Edit set" else "New set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    placeholder = { Text("Digital art") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.hashtags,
                    onValueChange = onHashtagsChange,
                    label = { Text("Hashtags") },
                    placeholder = { Text("#digitalart #illustration #artwork") },
                    minLines = 3,
                    isError = overLimit,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (overLimit) {
                        "${draft.tagCount} tags — Instagram allows ${PublishPolicy.MAX_HASHTAGS}."
                    } else {
                        "${draft.tagCount} of ${PublishPolicy.MAX_HASHTAGS} tags"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = "Anything that isn't a #tag is dropped when you save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            // Over the limit is still saveable: a set can be trimmed at post time, and
            // refusing to store it would lose the typing.
            TextButton(onClick = onSave, enabled = draft.canSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

@Composable
private fun NoPresetsYet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Tag,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No saved sets yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Instagram allows five hashtags per post, and reusing the same five " +
                "everywhere reads as spam. Save a few small sets instead — one per kind " +
                "of piece — and pick the one that fits.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
