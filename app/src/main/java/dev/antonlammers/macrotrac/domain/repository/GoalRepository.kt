package dev.antonlammers.macrotrac.domain.repository

import dev.antonlammers.macrotrac.domain.model.DailyGoal
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun goal(): Flow<DailyGoal>
    suspend fun save(goal: DailyGoal)
}
