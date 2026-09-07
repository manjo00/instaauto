package com.autoinsta.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.entities.HashtagPresetEntity
import com.autoinsta.data.repository.PresetRepository
import com.autoinsta.domain.HashtagSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The preset currently being written, whether new or an edit of an existing one. */
data class PresetDraft(
    /** Null for a new preset. */
    val id: Long? = null,
    val name: String = "",
    val hashtags: String = "",
) {
    val tagCount: Int get() = HashtagSet.count(hashtags)

    /** A preset with no name or no tags would do nothing useful. */
    val canSave: Boolean get() = name.isNotBlank() && !HashtagSet.isEmpty(hashtags)

    val isEditing: Boolean get() = id != null
}

/**
 * Backs the hashtag-presets screen.
 *
 * The table and repository have existed since Phase 1 with nothing to fill them, so the
 * picker on the compose screen was permanently empty. This is the missing half.
 */
class PresetsViewModel(
    private val presetRepository: PresetRepository,
) : ViewModel() {

    val presets: StateFlow<List<HashtagPresetEntity>> =
        presetRepository.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _draft = MutableStateFlow<PresetDraft?>(null)

    /** Non-null while the add/edit sheet is open. */
    val draft: StateFlow<PresetDraft?> = _draft

    fun startNew() {
        _draft.value = PresetDraft()
    }

    fun startEditing(preset: HashtagPresetEntity) {
        _draft.value = PresetDraft(
            id = preset.id,
            name = preset.name,
            hashtags = preset.hashtags,
        )
    }

    fun setName(value: String) {
        _draft.value = _draft.value?.copy(name = value)
    }

    fun setHashtags(value: String) {
        _draft.value = _draft.value?.copy(hashtags = value)
    }

    fun cancelDraft() {
        _draft.value = null
    }

    /**
     * Store the draft, with the tags tidied to single spaces.
     *
     * A preset is typed once and reused for months, so a stray newline or comma left in it
     * would follow every post that ever uses it.
     */
    fun saveDraft() {
        val current = _draft.value ?: return
        if (!current.canSave) return

        val tidied = HashtagSet.normalise(current.hashtags)
        viewModelScope.launch {
            val existingId = current.id
            if (existingId == null) {
                presetRepository.insert(
                    HashtagPresetEntity(
                        name = current.name.trim(),
                        hashtags = tidied,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            } else {
                val existing = presetRepository.getById(existingId) ?: return@launch
                presetRepository.update(
                    existing.copy(name = current.name.trim(), hashtags = tidied)
                )
            }
            _draft.value = null
        }
    }

    fun delete(presetId: Long) {
        viewModelScope.launch { presetRepository.delete(presetId) }
    }
}
