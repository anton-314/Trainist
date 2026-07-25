package dev.antonlammers.trainist.ui.bmr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.BmrCalculator
import dev.antonlammers.trainist.domain.MacroCalculator
import dev.antonlammers.trainist.domain.model.ActivityLevel
import dev.antonlammers.trainist.domain.model.BiologicalSex
import dev.antonlammers.trainist.domain.model.BmrProfile
import dev.antonlammers.trainist.domain.model.WeightGoal
import dev.antonlammers.trainist.domain.repository.GoalRepository
import dev.antonlammers.trainist.domain.repository.WeightRepository
import dev.antonlammers.trainist.ui.goals.formatWeight
import dev.antonlammers.trainist.util.normalizeDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The six steps of the BMR/TDEE wizard, walked in order. */
enum class BmrStep { SEX, AGE, HEIGHT, WEIGHT, ACTIVITY, GOAL }

data class BmrCalculatorUiState(
    val step: BmrStep = BmrStep.SEX,
    val isLoading: Boolean = true,
    val sex: BiologicalSex? = null,
    val ageInput: String = "",
    val heightInput: String = "",
    val weightInput: String = "",
    val activityLevel: ActivityLevel? = null,
    val goal: WeightGoal? = null,
)

data class BmrResult(
    val bmrKcal: Double,
    val tdeeKcal: Double,
    val goalKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/**
 * The BMR/TDEE wizard's step machine: per-step validation, prefill (persisted [BmrProfile] +
 * live latest body weight), and the final [BmrResult] computation. Hosted identically by
 * onboarding's local step machine and the pushed `Screen.BmrCalculator` route — this VM takes no
 * nav args, and navigating out of the wizard is left to the caller (see [BmrCalculatorWizard]).
 */
@HiltViewModel
class BmrCalculatorViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val weightRepository: WeightRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BmrCalculatorUiState())
    val uiState: StateFlow<BmrCalculatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val goal = goalRepository.goal().first()
            val latestWeight = weightRepository.allEntries().maxByOrNull { it.date }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    sex = goal.bmrProfile?.sex,
                    ageInput = goal.bmrProfile?.ageYears?.toString() ?: "",
                    heightInput = goal.bmrProfile?.heightCm?.let(::formatWeight) ?: "",
                    weightInput = latestWeight?.weightKg?.let(::formatWeight) ?: "",
                    activityLevel = goal.bmrProfile?.activityLevel,
                )
            }
        }
    }

    fun setSex(sex: BiologicalSex) = _uiState.update { it.copy(sex = sex) }
    fun setAgeInput(value: String) = _uiState.update { it.copy(ageInput = value) }
    fun setHeightInput(value: String) = _uiState.update { it.copy(heightInput = value) }
    fun setWeightInput(value: String) = _uiState.update { it.copy(weightInput = value) }
    fun setActivityLevel(level: ActivityLevel) = _uiState.update { it.copy(activityLevel = level) }
    fun setGoal(goal: WeightGoal) = _uiState.update { it.copy(goal = goal) }

    fun canProceed(state: BmrCalculatorUiState = uiState.value): Boolean = when (state.step) {
        BmrStep.SEX -> state.sex != null
        BmrStep.AGE -> state.ageInput.normalizeDecimal().toIntOrNull()?.let { it in MIN_AGE..MAX_AGE } ?: false
        BmrStep.HEIGHT -> state.heightInput.normalizeDecimal().toDoubleOrNull()
            ?.let { it in MIN_HEIGHT_CM..MAX_HEIGHT_CM } ?: false
        BmrStep.WEIGHT -> state.weightInput.normalizeDecimal().toDoubleOrNull()
            ?.let { it in MIN_WEIGHT_KG..MAX_WEIGHT_KG } ?: false
        BmrStep.ACTIVITY -> state.activityLevel != null
        BmrStep.GOAL -> state.goal != null
    }

    /**
     * Advances one step; a no-op if the current step isn't valid yet. Persists the profile the
     * moment it becomes complete (leaving ACTIVITY), independent of whether GOAL/result is ever
     * reached — so a user who backs out after this point still gets the prefill next time.
     */
    fun goNext() {
        val state = _uiState.value
        if (!canProceed(state)) return
        if (state.step == BmrStep.ACTIVITY) persistProfile(state)
        val next = BmrStep.entries.getOrNull(state.step.ordinal + 1) ?: return
        _uiState.update { it.copy(step = next) }
    }

    /** Steps back one; returns false at the first step (caller should exit the wizard). */
    fun goBack(): Boolean {
        val prevOrdinal = _uiState.value.step.ordinal - 1
        if (prevOrdinal < 0) return false
        _uiState.update { it.copy(step = BmrStep.entries[prevOrdinal]) }
        return true
    }

    /** Full computed result, or null until every input is valid. Pure w.r.t. [state]. */
    fun result(state: BmrCalculatorUiState = uiState.value): BmrResult? {
        val sex = state.sex ?: return null
        val age = state.ageInput.normalizeDecimal().toIntOrNull() ?: return null
        val height = state.heightInput.normalizeDecimal().toDoubleOrNull() ?: return null
        val weight = state.weightInput.normalizeDecimal().toDoubleOrNull() ?: return null
        val activity = state.activityLevel ?: return null
        val goal = state.goal ?: return null

        val bmr = BmrCalculator.bmrKcal(sex, weight, height, age)
        val tdee = BmrCalculator.tdeeKcal(bmr, activity)
        val kcal = BmrCalculator.goalKcal(tdee, bmr, goal)
        val protein = MacroCalculator.recommendedProteinG(weight)
        val fat = MacroCalculator.recommendedFatG(weight)
        val carbs = MacroCalculator.carbsFromKcalAndMacros(kcal, protein, fat)
        return BmrResult(bmr, tdee, kcal, protein, carbs, fat)
    }

    private fun persistProfile(state: BmrCalculatorUiState) {
        val sex = state.sex ?: return
        val age = state.ageInput.normalizeDecimal().toIntOrNull() ?: return
        val height = state.heightInput.normalizeDecimal().toDoubleOrNull() ?: return
        val activity = state.activityLevel ?: return
        viewModelScope.launch {
            val current = goalRepository.goal().first()
            goalRepository.save(current.copy(bmrProfile = BmrProfile(sex, age, height, activity)))
        }
    }

    companion object {
        const val MIN_AGE = 10
        const val MAX_AGE = 120
        const val MIN_HEIGHT_CM = 100.0
        const val MAX_HEIGHT_CM = 250.0
        const val MIN_WEIGHT_KG = 20.0
        const val MAX_WEIGHT_KG = 300.0
    }
}
