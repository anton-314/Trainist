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
import java.time.LocalDate
import javax.inject.Inject

/** A template paired with the date it was last trained from, if ever (spec: "last used" hint). */
data class TemplateListItem(
    val template: WorkoutTemplate,
    val lastUsedDate: LocalDate?,
)

/**
 * "Training" tab home — the list of saved workout templates plus a resume hint for any in-progress
 * session. Creating/editing templates happens on the separate [TemplateEditorViewModel] screen;
 * here we only list, delete (with an undo window, mirroring the custom-food / custom-exercise
 * deferred-delete pattern) and manually reorder (drag-and-drop, persisted immediately like every
 * other reorderable list in the app).
 */
@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: WorkoutTemplateRepository,
    private val sessions: WorkoutSessionRepository,
) : ViewModel() {

    // Deferred delete: hidden from the list while the undo snackbar is shown.
    private val _pendingDelete = MutableStateFlow<WorkoutTemplate?>(null)

    /** The single in-progress session, if any — surfaced as a resume banner on the tab. */
    val activeSession: StateFlow<WorkoutSession?> = sessions.activeSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val templates: StateFlow<List<TemplateListItem>> = combine(
        repository.templates(),
        sessions.sessions(),
        _pendingDelete,
    ) { list, allSessions, pending ->
        val lastUsedByTemplate = allSessions
            .mapNotNull { s -> s.templateStableId?.let { it to s.date } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.max() }
        list.filter { pending == null || it.id != pending.id }
            .map { TemplateListItem(it, lastUsedByTemplate[it.stableId]) }
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

    /** Swap two templates — called repeatedly (once per adjacent step) while one is dragged into place. */
    fun moveTemplate(from: Int, to: Int) {
        val current = templates.value
        if (from !in current.indices || to !in current.indices) return
        val ids = current.map { it.template.id }.toMutableList()
        val tmp = ids[from]; ids[from] = ids[to]; ids[to] = tmp
        viewModelScope.launch { repository.reorder(ids) }
    }
}
