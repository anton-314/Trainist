package dev.antonlammers.macrotrac.data.repository

import dev.antonlammers.macrotrac.data.local.dao.WorkoutTemplateDao
import dev.antonlammers.macrotrac.data.repository.WorkoutMappers.exerciseEntities
import dev.antonlammers.macrotrac.data.repository.WorkoutMappers.toDomain
import dev.antonlammers.macrotrac.data.repository.WorkoutMappers.toEntity
import dev.antonlammers.macrotrac.domain.model.WorkoutTemplate
import dev.antonlammers.macrotrac.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkoutTemplateRepositoryImpl @Inject constructor(
    private val dao: WorkoutTemplateDao,
    private val runner: TransactionRunner,
) : WorkoutTemplateRepository {

    override fun templates(): Flow<List<WorkoutTemplate>> =
        dao.allTemplates().map { list -> list.map { it.toDomain() } }

    override fun template(id: Long): Flow<WorkoutTemplate?> =
        dao.templateById(id).map { it?.toDomain() }

    override suspend fun save(template: WorkoutTemplate): Long = runner.transaction {
        // A new template (id == 0) is appended to the end of the manual order; editing an existing
        // one preserves its current position (the REPLACE insert would otherwise overwrite it).
        val position = if (template.id != 0L) {
            dao.positionOf(template.id) ?: dao.nextPosition()
        } else {
            dao.nextPosition()
        }
        val templateId = dao.insertTemplate(template.toEntity(position))
        // On replace the FK already cleared old slots; the explicit delete also covers a plain
        // update where the id was reused without a cascade. Then rewrite the ordered slots.
        dao.deleteTemplateExercises(templateId)
        dao.insertTemplateExercises(template.exerciseEntities(templateId))
        templateId
    }

    override suspend fun delete(id: Long) = dao.deleteTemplate(id)

    override suspend fun reorder(templateIds: List<Long>) = runner.transaction {
        templateIds.forEachIndexed { index, id -> dao.updatePosition(id, index) }
    }
}
