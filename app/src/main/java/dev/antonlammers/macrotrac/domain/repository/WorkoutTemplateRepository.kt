package dev.antonlammers.macrotrac.domain.repository

import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

interface WorkoutTemplateRepository {
    /** Ordered by the user's manual drag-to-reorder position. */
    fun templates(): Flow<List<WorkoutTemplate>>
    fun template(id: Long): Flow<WorkoutTemplate?>

    /** Inserts a new template (id == 0) or replaces an existing one, rewriting its exercise list. */
    suspend fun save(template: WorkoutTemplate): Long
    suspend fun delete(id: Long)

    /** Applies a new manual order: [templateIds] in the desired order (must cover known ids). */
    suspend fun reorder(templateIds: List<Long>)
}
