package dev.antonlammers.macromind.data.repository

import dev.antonlammers.macromind.data.local.dao.DailyGoalDao
import dev.antonlammers.macromind.data.local.entity.DailyGoalEntity
import dev.antonlammers.macromind.domain.model.DailyGoal
import dev.antonlammers.macromind.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val dao: DailyGoalDao,
) : GoalRepository {

    override fun goal(): Flow<DailyGoal> = dao.goal().map { entity ->
        entity?.toDomain() ?: DailyGoal()
    }

    override suspend fun save(goal: DailyGoal) = dao.save(goal.toEntity())

    private fun DailyGoalEntity.toDomain() = DailyGoal(
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
    )

    private fun DailyGoal.toEntity() = DailyGoalEntity(
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
    )
}
