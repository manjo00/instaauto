package com.autoinsta.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.entities.PostingSlotEntity
import com.autoinsta.data.db.entities.QueueSettingsEntity
import com.autoinsta.data.repository.QueueRepository
import com.autoinsta.data.repository.toSlot
import com.autoinsta.domain.QueuePlanner
import java.time.DayOfWeek
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the posting-schedule screen: the slots, the pause switch, and the catch-up window.
 *
 * Every write goes through [QueueRepository], which replans afterwards — so changing the
 * schedule immediately re-dates everything in the pool rather than leaving the queue
 * describing a rhythm that no longer exists.
 */
class ScheduleViewModel(
    private val queueRepository: QueueRepository,
) : ViewModel() {

    val slots: StateFlow<List<PostingSlotEntity>> =
        queueRepository.observeSlots().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val settings: StateFlow<QueueSettingsEntity> =
        queueRepository.observeSettings().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QueueSettingsEntity(),
        )

    /**
     * The next few real dates the schedule produces.
     *
     * "Mon 7pm, Wed 7pm" is abstract; "tomorrow, then Wednesday the 9th" is not. Showing
     * the actual dates is the cheapest way to catch a slot that says something other than
     * what the owner meant.
     */
    val upcoming: StateFlow<List<Long>> =
        combine(slots, settings) { allSlots, current ->
            if (current.paused) return@combine emptyList()
            val enabled = allSlots.filter { it.enabled }.map { it.toSlot() }
            QueuePlanner.slotTimesFrom(enabled, System.currentTimeMillis(), ZoneId.systemDefault())
                .take(PREVIEW_COUNT)
                .toList()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _showAddSlot = MutableStateFlow(false)
    val showAddSlot: StateFlow<Boolean> = _showAddSlot

    fun openAddSlot() { _showAddSlot.value = true }
    fun dismissAddSlot() { _showAddSlot.value = false }

    fun addSlot(day: DayOfWeek, hour: Int, minute: Int) {
        viewModelScope.launch {
            queueRepository.addSlot(day, hour, minute)
            _showAddSlot.value = false
        }
    }

    fun deleteSlot(slotId: Long) {
        viewModelScope.launch { queueRepository.deleteSlot(slotId) }
    }

    /** Switching a slot off keeps it — rebuilding a weekly time from memory is a bad trade. */
    fun setSlotEnabled(slot: PostingSlotEntity, enabled: Boolean) {
        viewModelScope.launch { queueRepository.updateSlot(slot.copy(enabled = enabled)) }
    }

    fun setPaused(paused: Boolean) {
        viewModelScope.launch { queueRepository.setPaused(paused) }
    }

    fun setCatchUpWindow(minutes: Int) {
        viewModelScope.launch { queueRepository.setCatchUpWindow(minutes) }
    }

    private companion object {
        const val PREVIEW_COUNT = 5
    }
}
