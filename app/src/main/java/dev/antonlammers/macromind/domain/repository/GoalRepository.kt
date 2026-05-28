package dev.antonlammers.macromind.domain.repository

import dev.antonlammers.macromind.domain.model.DailyGoal
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun goal(): Flow<DailyGoal>
    suspend fun save(goal: DailyGoal)
}
