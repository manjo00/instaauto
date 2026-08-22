package com.autoinsta.ui.composepost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.entities.HashtagPresetEntity
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.repository.MediaToSave
import com.autoinsta.data.repository.PostRepository
import com.autoinsta.data.repository.PresetRepository
import com.autoinsta.domain.PostValidation
import com.autoinsta.domain.PostValidator
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * One piece of media the user has picked.
 *
 * [isImported] is false for something just chosen in the picker (the address is a
 * short-lived `content://` grant) and true for something loaded back from the
 * database (already copied into app storage). The repository uses it to avoid
 * re-copying every file each time an existing post is saved.
 */
data class PickedMedia(
    val uri: String,
    val mediaType: MediaType,
    val isImported: Boolean = false,
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
    val missedPolicy: MissedPostPolicy = MissedPostPolicy.POST_IF_RECENT,
    /** False when Android will not honour to-the-minute alarms; the UI warns. */
    val canScheduleExact: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveComplete: Boolean = false,
)

/**
 * Drives the create/edit-post screen. When [postId] is non-null the screen loads
 * the existing post (and its media) and Save performs an update instead of an insert.
 *
 * The rules about what makes a post valid live in [PostValidator], not here — this
 * class only wires state to those rules and turns a failure into a sentence.
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
                                isImported = true,
                                existingCloudinaryUrl = m.cloudinaryUrl,
                            )
                        },
                    caption = existing.post.caption,
                    hashtags = existing.post.hashtags,
                    selectedPresetId = existing.post.presetId,
                    scheduledAtMillis = existing.post.scheduledAt,
                    missedPolicy = existing.post.missedPolicy,
                )
            }
        }
    }

    fun setPostType(type: PostType) {
        _uiState.update { state ->
            // Switching to a single-file type keeps only the first item; switching to
            // CAROUSEL keeps everything already picked.
            val limit = PostValidator.maxMediaFor(type)
            state.copy(
                postType = type,
                media = state.media.take(limit),
                errorMessage = null,
            )
        }
    }

    /** Adds newly-picked media, respecting the limit for the current post type. */
    fun addMedia(picked: List<PickedMedia>) {
        if (picked.isEmpty()) return
        _uiState.update { state ->
            val limit = PostValidator.maxMediaFor(state.postType)
            val combined = if (limit == 1) picked.take(1) else (state.media + picked).take(limit)
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

    fun setMissedPolicy(policy: MissedPostPolicy) {
        _uiState.update { it.copy(missedPolicy = policy) }
    }

    /** Re-read on every screen entry — the user may have just changed it in Settings. */
    fun refreshExactAlarmAvailability() {
        _uiState.update { it.copy(canScheduleExact = postRepository.canScheduleExact()) }
    }

    fun consumeSaveComplete() {
        _uiState.update { it.copy(saveComplete = false) }
    }

    fun save() {
        val state = _uiState.value
        val validation = PostValidator.validate(
            postType = state.postType,
            mediaCount = state.media.size,
            scheduledAtMillis = state.scheduledAtMillis,
            nowMillis = System.currentTimeMillis(),
        )
        if (validation is PostValidation.Invalid) {
            _uiState.update { it.copy(errorMessage = messageFor(validation.reason)) }
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
                missedPolicy = state.missedPolicy,
                createdAt = if (postId != null) existingCreatedAt else now,
                workRequestId = existingWorkRequestId,
            )
            val media = state.media.map { picked ->
                MediaToSave(
                    sourceUri = picked.uri,
                    mediaType = picked.mediaType,
                    alreadyImported = picked.isImported,
                    existingCloudinaryUrl = picked.existingCloudinaryUrl,
                )
            }

            try {
                if (postId != null) {
                    postRepository.updatePost(entity, media)
                } else {
                    postRepository.insertPost(entity, media)
                }
                _uiState.update { it.copy(isSaving = false, saveComplete = true) }
            } catch (e: IOException) {
                // Most likely the picker's temporary read grant expired before save.
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Couldn't read one of your files. Pick it again and retry.",
                    )
                }
            }
        }
    }

    private fun messageFor(reason: PostValidation.Reason): String = when (reason) {
        PostValidation.Reason.NO_MEDIA ->
            "Add at least one photo or video."
        PostValidation.Reason.CAROUSEL_TOO_FEW ->
            "A carousel needs at least ${PostValidator.CAROUSEL_MIN_ITEMS} items."
        PostValidation.Reason.CAROUSEL_TOO_MANY ->
            "A carousel can hold at most ${PostValidator.CAROUSEL_MAX_ITEMS} items."
        PostValidation.Reason.TOO_MANY_FOR_TYPE ->
            "Only one file is allowed for this post type."
        PostValidation.Reason.TIME_IN_PAST ->
            "Pick a date and time in the future."
    }
}
