package dev.antonlammers.macrotrac.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.model.WorkoutSession
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import dev.antonlammers.macrotrac.domain.repository.WorkoutSessionRepository
import dev.antonlammers.macrotrac.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Training" tab home — the list of saved workout templates plus a resume hint for any in-progress
 * session. Creating/editing templates happens on the separate [TemplateEditorViewModel] screen;
 * here we only list and delete (with an undo window, mirroring the custom-food / custom-exercise
 * deferred-delete pattern).
 */
@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: WorkoutTemplateRepository,
    sessions: WorkoutSessionRepository,
) : ViewModel() {

    // Deferred delete: hidden from the list while the undo snackbar is shown.
    private val _pendingDelete = MutableStateFlow<WorkoutTemplate?>(null)

    /** The single in-progress session, if any — surfaced as a resume banner on the tab. */
    val activeSession: StateFlow<WorkoutSession?> = sessions.activeSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val templates: StateFlow<List<WorkoutTemplate>> = combine(
        repository.templates(),
        _pendingDelete,
    ) { list, pending ->
        list.filter { pending == null || it.id != pending.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deletePending(template: WorkoutTemplate) {
        _pendingDelete.value = template
    }

    fun confirmDelete(template: WorkoutTemplate) {
        if (_pendingDelete.value?.id == template.id) _pendingDelete.value = null
        viewModelScope.launch { repository.delete(template.id) }
    }

    fun undoDelete(template: WorkoutTemplate) {
        if (_pendingDelete.value?.id == template.id) _pendingDelete.value = null
    }
}
