package dev.antonlammers.macrotrac.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.model.DailyGoal
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.GoalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val foodEntryRepository: FoodEntryRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    val uiState: StateFlow<OverviewUiState> = combine(
        foodEntryRepository.entriesForDate(today),
        goalRepository.goal(),
    ) { entries, goal ->
        OverviewUiState(entries = entries, goal = goal, date = today)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewUiState(),
    )

    fun delete(id: Long) {
        viewModelScope.launch { foodEntryRepository.delete(id) }
    }
}

data class OverviewUiState(
    val entries: List<FoodEntry> = emptyList(),
    val goal: DailyGoal = DailyGoal(),
    val date: LocalDate = LocalDate.now(),
) {
    val totalKcal get() = entries.sumOf { it.kcal }
    val totalProtein get() = entries.sumOf { it.proteinG }
    val totalCarbs get() = entries.sumOf { it.carbsG }
    val totalFat get() = entries.sumOf { it.fatG }
}
