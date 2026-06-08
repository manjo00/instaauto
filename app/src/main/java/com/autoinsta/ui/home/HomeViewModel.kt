package com.autoinsta.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.repository.PostRepository
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

    fun deletePost(postId: Long) {
        viewModelScope.launch {
            postRepository.deletePost(postId)
        }
    }
}
