package com.autoinsta.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.entities.QueueSettingsEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.repository.PostRepository
import com.autoinsta.data.repository.QueueRepository
import com.autoinsta.domain.DragReorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Home screen: the pool in the owner's order, then anything pinned to a time.
 */
class HomeViewModel(
    private val postRepository: PostRepository,
    private val queueRepository: QueueRepository,
) : ViewModel() {

    /**
     * The order being dragged, before it is committed.
     *
     * While a drag is happening the database still holds the old order, and letting the
     * list re-sort itself underneath the finger would be unusable. Non-null only for the
     * length of a gesture.
     */
    private val draftOrder = MutableStateFlow<List<Long>?>(null)

    /** The pool, in the owner's order — overridden by the draft during a drag. */
    val queue: StateFlow<List<ScheduledPostWithMedia>> =
        combine(queueRepository.observeQueue(), draftOrder) { posts, draft ->
            if (draft == null) {
                posts
            } else {
                posts.sortedBy { item ->
                    draft.indexOf(item.post.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Posts the owner pinned to a date. Shown separately; never reordered. */
    val fixedPosts: StateFlow<List<ScheduledPostWithMedia>> =
        queueRepository.observeFixedScheduled().stateIn(
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

    /** False means the queue has no rhythm yet, so nothing in the pool has a date. */
    val hasSlots: StateFlow<Boolean> =
        queueRepository.observeSlots().map { slots -> slots.any { it.enabled } }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val _canScheduleExact = MutableStateFlow(true)

    /** False when Android will not honour to-the-minute alarms; the queue warns. */
    val canScheduleExact: StateFlow<Boolean> = _canScheduleExact

    /** Re-read whenever the queue is shown — Settings may have changed underneath us. */
    fun refreshExactAlarmAvailability() {
        _canScheduleExact.value = postRepository.canScheduleExact()
    }

    /** Times go stale on their own; opening the queue is a good moment to catch up. */
    fun refreshPlan() {
        viewModelScope.launch { queueRepository.replan() }
    }

    fun deletePost(postId: Long) {
        viewModelScope.launch { postRepository.deletePost(postId) }
    }

    // ── Dragging ───────────────────────────────────────────────────────────

    fun beginDrag() {
        draftOrder.value = queue.value.map { it.post.id }
    }

    /** Called once on release, not on every frame — the list does not reflow mid-drag. */
    fun dropAt(fromIndex: Int, toIndex: Int) {
        val current = draftOrder.value ?: queue.value.map { it.post.id }
        val moved = DragReorder.move(current, fromIndex, toIndex)
        viewModelScope.launch {
            if (moved != current) queueRepository.reorder(moved)
            // Cleared only after the write, so the list never flashes the old order.
            draftOrder.value = null
        }
    }

    fun cancelDrag() {
        draftOrder.value = null
    }

    /**
     * Debug-only: re-time a post to fire shortly from now.
     *
     * Verifying a scheduler honestly means waiting for real time to pass. This makes
     * that a 20-second job instead of an hour. Only reachable from debug builds.
     */
    fun fireSoon(postId: Long, inSeconds: Int = 20) {
        viewModelScope.launch {
            val existing = postRepository.getById(postId) ?: return@launch
            postRepository.updatePost(
                existing.post.copy(scheduledAt = System.currentTimeMillis() + inSeconds * 1000L)
            )
            postRepository.rescheduleAlarm(postId)
        }
    }
}
