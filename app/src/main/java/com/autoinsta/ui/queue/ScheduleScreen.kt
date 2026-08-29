package com.autoinsta.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.PostingSlotEntity
import com.autoinsta.domain.QueuePlanner
import java.time.DayOfWeek

/**
 * Where the posting rhythm is set: the days and times the queue empties into.
 *
 * A flat list of slots rather than a days-by-times grid, because a grid cannot say
 * "Saturday, but at 11am" without giving every other day that time too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoInstaApp
    val viewModel: ScheduleViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ScheduleViewModel(app.queueRepository) as T
            }
        },
    )

    val slots by viewModel.slots.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val showAddSlot by viewModel.showAddSlot.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Posting schedule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAddSlot,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add slot") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                16.dp,
                padding.calculateTopPadding() + 8.dp,
                16.dp,
                96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "pause") {
                PauseCard(
                    paused = settings.paused,
                    onPausedChange = viewModel::setPaused,
                )
            }

            item(key = "slots-header") {
                SectionHeader(
                    title = "When posts go out",
                    subtitle = "The queue fills these in order. A slot with nothing waiting " +
                        "is simply skipped.",
                )
            }

            if (slots.isEmpty()) {
                item(key = "no-slots") { NoSlots() }
            } else {
                items(slots, key = { it.id }) { slot ->
                    SlotRow(
                        slot = slot,
                        onEnabledChange = { viewModel.setSlotEnabled(slot, it) },
                        onDeleteClick = { pendingDeleteId = slot.id },
                    )
                }
            }

            if (upcoming.isNotEmpty()) {
                item(key = "upcoming") { UpcomingPreview(upcoming) }
            }

            item(key = "catch-up") {
                CatchUpCard(
                    minutes = settings.catchUpWindowMinutes,
                    onMinutesChange = viewModel::setCatchUpWindow,
                )
            }
        }
    }

    if (showAddSlot) {
        AddSlotDialog(
            onDismiss = viewModel::dismissAddSlot,
            onConfirm = viewModel::addSlot,
        )
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Remove this slot?") },
            text = {
                Text(
                    "Posts waiting on it will move to the next one. " +
                        "To skip it just this once, switch it off instead."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSlot(deleteId)
                    pendingDeleteId = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PauseCard(paused: Boolean, onPausedChange: (Boolean) -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Pause the queue", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (paused) {
                        "Nothing will go out. Your posts are keeping their order."
                    } else {
                        "Posts go out on the schedule below."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = paused, onCheckedChange = onPausedChange)
        }
    }
}

@Composable
private fun SlotRow(
    slot: PostingSlotEntity,
    onEnabledChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = slotLabel(slot),
                style = MaterialTheme.typography.bodyLarge,
                color = if (slot.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = slot.enabled, onCheckedChange = onEnabledChange)
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove ${slotLabel(slot)}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun NoSlots() {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Text(
                    "No slots yet",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = "Add a day and time — say Monday at 7:00 PM. Anything you put in " +
                    "the queue will go out at the next one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * "Mon 7pm" is abstract; a real date is not. Showing the next few is the cheapest way to
 * notice a slot that says something other than what was meant.
 */
@Composable
private fun UpcomingPreview(times: List<Long>) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Next up", style = MaterialTheme.typography.titleSmall)
            times.forEach { at ->
                Text(
                    text = momentLabel(at),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CatchUpCard(minutes: Int, onMinutesChange: (Int) -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("If a slot is missed", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "A slot stays open this long after it passes — so a post can still " +
                    "fill it if the phone was off, or if you finish something just after. " +
                    "After that, it waits for the next slot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                QueuePlanner.CATCH_UP_WINDOW_CHOICES_MINUTES.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = minutes == choice,
                        onClick = { onMinutesChange(choice) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            QueuePlanner.CATCH_UP_WINDOW_CHOICES_MINUTES.size,
                        ),
                    ) {
                        Text(windowLabel(choice))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSlotDialog(
    onDismiss: () -> Unit,
    onConfirm: (DayOfWeek, Int, Int) -> Unit,
) {
    var day by remember { mutableStateOf(DayOfWeek.MONDAY) }
    val timeState = rememberTimePickerState(initialHour = 19, initialMinute = 0, is24Hour = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a slot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Which day?", style = MaterialTheme.typography.labelLarge)
                // Two rows of chips: seven across does not fit a phone at any font size.
                DayOfWeek.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { candidate ->
                            FilterChip(
                                selected = day == candidate,
                                onClick = { day = candidate },
                                label = { Text(shortDayName(candidate)) },
                            )
                        }
                    }
                }
                HorizontalDivider()
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day, timeState.hour, timeState.minute) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
