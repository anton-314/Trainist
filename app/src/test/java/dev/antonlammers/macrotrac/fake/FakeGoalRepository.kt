package dev.antonlammers.macrotrac.fake

import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeGoalRepository(initial: DailyGoal = DailyGoal()) : GoalRepository {

    private val _goal = MutableStateFlow(initial)

    override fun goal(): Flow<DailyGoal> = _goal.asStateFlow()

    override suspend fun save(goal: DailyGoal) {
        _goal.value = goal
    }
}
