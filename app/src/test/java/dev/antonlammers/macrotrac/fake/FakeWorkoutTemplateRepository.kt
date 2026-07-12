package dev.antonlammers.macrotrac.fake

import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import dev.antonlammers.macrotrac.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** [templates] order is the canonical manual order — like Room's `position` column, but the list's
 *  own order stands in for it, so a new template is appended and [reorder] simply rearranges it. */
class FakeWorkoutTemplateRepository : WorkoutTemplateRepository {

    private val _templates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
    private var nextId = 1L

    override fun templates(): Flow<List<WorkoutTemplate>> = _templates

    override fun template(id: Long): Flow<WorkoutTemplate?> =
        _templates.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun save(template: WorkoutTemplate): Long {
        // Mirrors Room's unique(stableId) + REPLACE: a fresh insert (id == 0) whose stableId already
        // exists (e.g. re-importing the same backup) replaces that row instead of adding a new one.
        val conflict = if (template.id == 0L) _templates.value.firstOrNull { it.stableId == template.stableId } else null
        val id = conflict?.id ?: if (template.id == 0L) nextId++ else template.id
        val normalized = template.copy(
            id = id,
            exercises = template.exercises.mapIndexed { index, e -> e.copy(position = index) },
        )
        _templates.update { list ->
            val existingIndex = list.indexOfFirst { it.id == id }
            if (existingIndex >= 0) list.toMutableList().apply { this[existingIndex] = normalized }
            else list + normalized
        }
        return id
    }

    override suspend fun delete(id: Long) {
        _templates.update { it.filterNot { t -> t.id == id } }
    }

    override suspend fun reorder(templateIds: List<Long>) {
        _templates.update { list ->
            val byId = list.associateBy { it.id }
            templateIds.mapNotNull { byId[it] } + list.filterNot { it.id in templateIds }
        }
    }
}
