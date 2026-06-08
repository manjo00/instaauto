package com.autoinsta.ui.composepost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.entities.HashtagPresetEntity
import com.autoinsta.data.db.entities.MediaItemEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.repository.PostRepository
import com.autoinsta.data.repository.PresetRepository
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Maximum items allowed in a carousel (Instagram limit). */
const val CAROUSEL_MAX_ITEMS = 10
private const val CAROUSEL_MIN_ITEMS = 2

/** One piece of media the user has picked, before/after it's persisted. */
data class PickedMedia(
    val uri: String,
    val mediaType: MediaType,
    /** Non-null when this item already existed in the DB (edit mode). */
    val existingId: Long? = null,
    val existingCloudinaryUrl: String? = null,
)

data class ComposePostUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val postType: PostType = PostType.SINGLE_IMAGE,
    val media: List<PickedMedia> = emptyList(),
    val caption: String = "",
    val hashtags: String = "",
    val selectedPresetId: Long? = null,
    val presets: List<HashtagPresetEntity> = emptyList(),
    /** Epoch millis for the chosen publish time. Defaults to "now + 1 hour". */
    val scheduledAtMillis: Long = System.currentTimeMillis() + 60L * 60L * 1000L,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveComplete: Boolean = false,
)

/**
 * Drives the create/edit-post screen. When [postId] is non-null the screen loads
 * the existing post (and its media) and Save performs an update instead of an insert.
 */
class ComposePostViewModel(
    private val postId: Long?,
    private val postRepository: PostRepository,
    private val presetRepository: PresetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ComposePostUiState(isEditing = postId != null, isLoading = postId != null)
    )
    val uiState: StateFlow<ComposePostUiState> = _uiState

    private var existingCreatedAt: Long = System.currentTimeMillis()
    private var existingWorkRequestId: String? = null

    init {
        viewModelScope.launch {
            presetRepository.observeAll().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
        if (postId != null) loadExisting(postId)
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val existing = postRepository.getById(id)
            if (existing == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Post not found")
                }
                return@launch
            }
            existingCreatedAt = existing.post.createdAt
            existingWorkRequestId = existing.post.workRequestId
            _uiState.update {
                it.copy(
                    isLoading = false,
                    postType = existing.post.postType,
                    media = existing.mediaItems
                        .sortedBy { m -> m.orderIndex }
                        .map { m ->
                            PickedMedia(
                                uri = m.localUri,
                                mediaType = m.mediaType,
                                existingId = m.id,
                                existingCloudinaryUrl = m.cloudinaryUrl,
                            )
                        },
                    caption = existing.post.caption,
                    hashtags = existing.post.hashtags,
                    selectedPresetId = existing.post.presetId,
                    scheduledAtMillis = existing.post.scheduledAt,
                )
            }
        }
    }

    fun setPostType(type: PostType) {
        _uiState.update { state ->
            // Switching away from CAROUSEL keeps only the first item; switching
            // to CAROUSEL keeps everything already picked.
            val trimmedMedia = if (type != PostType.CAROUSEL) {
                state.media.take(1)
            } else {
                state.media
            }
            state.copy(postType = type, media = trimmedMedia, errorMessage = null)
        }
    }

    /** Adds newly-picked media, respecting per-type limits. Replaces for single types. */
    fun addMedia(picked: List<PickedMedia>) {
        if (picked.isEmpty()) return
        _uiState.update { state ->
            val combined = if (state.postType == PostType.CAROUSEL) {
                (state.media + picked).take(CAROUSEL_MAX_ITEMS)
            } else {
                picked.take(1)
            }
            state.copy(media = combined, errorMessage = null)
        }
    }

    fun removeMedia(index: Int) {
        _uiState.update { state ->
            val updated = state.media.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
            }
            state.copy(media = updated)
        }
    }

    fun setCaption(value: String) {
        _uiState.update { it.copy(caption = value, errorMessage = null) }
    }

    fun setHashtags(value: String) {
        _uiState.update { it.copy(hashtags = value, selectedPresetId = null, errorMessage = null) }
    }

    fun selectPreset(preset: HashtagPresetEntity?) {
        _uiState.update {
            it.copy(
                selectedPresetId = preset?.id,
                hashtags = preset?.hashtags ?: it.hashtags,
            )
        }
    }

    fun setScheduledAt(millis: Long) {
        _uiState.update { it.copy(scheduledAtMillis = millis, errorMessage = null) }
    }

    fun consumeSaveComplete() {
        _uiState.update { it.copy(saveComplete = false) }
    }

    fun save() {
        val state = _uiState.value
        val validationError = validate(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entity = ScheduledPostEntity(
                id = postId ?: 0,
                postType = state.postType,
                status = PostStatus.SCHEDULED,
                caption = state.caption.trim(),
                hashtags = state.hashtags.trim(),
                presetId = state.selectedPresetId,
                scheduledAt = state.scheduledAtMillis,
                createdAt = if (postId != null) existingCreatedAt else now,
                workRequestId = existingWorkRequestId,
            )
            val mediaEntities = state.media.mapIndexed { index, picked ->
                MediaItemEntity(
                    id = picked.existingId ?: 0,
                    postId = postId ?: 0,
                    mediaType = picked.mediaType,
                    localUri = picked.uri,
                    cloudinaryUrl = picked.existingCloudinaryUrl,
                    orderIndex = index,
                )
            }

            if (postId != null) {
                postRepository.updatePost(entity, mediaEntities)
            } else {
                postRepository.insertPost(entity, mediaEntities)
            }

            _uiState.update { it.copy(isSaving = false, saveComplete = true) }
        }
    }

    private fun validate(state: ComposePostUiState): String? {
        if (state.media.isEmpty()) {
            return "Add at least one photo or video."
        }
        if (state.postType == PostType.CAROUSEL && state.media.size < CAROUSEL_MIN_ITEMS) {
            return "A carousel needs at least $CAROUSEL_MIN_ITEMS items."
        }
        if (state.postType != PostType.CAROUSEL && state.media.size > 1) {
            return "Only one file is allowed for this post type."
        }
        if (state.scheduledAtMillis <= System.currentTimeMillis()) {
            return "Pick a date and time in the future."
        }
        return null
    }
}
