package com.autoinsta.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Home/queue screen — a live list of posts still waiting to fire,
 * newest-scheduled-first per [PostRepository.observeScheduled].
 */
class HomeViewModel(
    private val postRepository: PostRepository,
) : ViewModel() {

    val scheduledPosts: StateFlow<List<ScheduledPostWithMedia>> =
        postRepository.observeScheduled().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _canScheduleExact = MutableStateFlow(true)
    /** False when Android will not honour to-the-minute alarms; the queue warns. */
    val canScheduleExact: StateFlow<Boolean> = _canScheduleExact

    /** Re-read whenever the queue is shown — Settings may have changed underneath us. */
    fun refreshExactAlarmAvailability() {
        _canScheduleExact.value = postRepository.canScheduleExact()
    }

    fun deletePost(postId: Long) {
        viewModelScope.launch {
            postRepository.deletePost(postId)
        }
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
