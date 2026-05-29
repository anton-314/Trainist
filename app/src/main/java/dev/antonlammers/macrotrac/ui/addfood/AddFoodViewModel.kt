package dev.antonlammers.macrotrac.ui.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.domain.model.Food
import dev.antonlammers.macrotrac.domain.model.FoodEntry
import dev.antonlammers.macrotrac.domain.model.MealCategory
import dev.antonlammers.macrotrac.domain.repository.CustomFoodRepository
import dev.antonlammers.macrotrac.domain.repository.FoodEntryRepository
import dev.antonlammers.macrotrac.domain.repository.FoodSearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val foodSearchRepository: FoodSearchRepository,
    private val foodEntryRepository: FoodEntryRepository,
    private val customFoodRepository: CustomFoodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddFoodUiState())
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    val recentFoods: StateFlow<List<FoodEntry>> = foodEntryRepository.recentFoods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customFoods: StateFlow<List<Food>> = customFoodRepository.allFoods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, results = emptyList()) }
            foodSearchRepository.search(query)
                .onSuccess { results -> _uiState.update { it.copy(results = results, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message ?: "Unbekannter Fehler", isLoading = false) } }
        }
    }

    fun selectFood(food: Food) = _uiState.update { it.copy(selectedFood = food, amountGrams = "100") }

    fun selectRecentFood(entry: FoodEntry) {
        val factor = if (entry.amountGrams > 0) 100.0 / entry.amountGrams else 1.0
        val food = Food(
            id = entry.foodName,
            name = entry.foodName,
            brand = entry.brand,
            kcalPer100g = entry.kcal * factor,
            proteinPer100g = entry.proteinG * factor,
            carbsPer100g = entry.carbsG * factor,
            fatPer100g = entry.fatG * factor,
            sugarPer100g = entry.sugarG * factor,
            fiberPer100g = entry.fiberG * factor,
        )
        val prevAmount = if (entry.amountGrams % 1.0 == 0.0) entry.amountGrams.toInt().toString()
                         else entry.amountGrams.toString()
        _uiState.update { it.copy(selectedFood = food, amountGrams = prevAmount) }
    }

    fun saveCustomFood(food: Food) {
        viewModelScope.launch {
            val saved = customFoodRepository.save(food)
            _uiState.update { it.copy(selectedFood = saved, amountGrams = "100") }
        }
    }

    fun deleteCustomFood(food: Food) {
        food.id.toLongOrNull()?.let { id ->
            viewModelScope.launch { customFoodRepository.delete(id) }
        }
    }

    fun dismissSelection() = _uiState.update { it.copy(selectedFood = null) }

    fun onAmountChange(amount: String) = _uiState.update { it.copy(amountGrams = amount) }

    fun onMealCategoryChange(category: MealCategory) = _uiState.update { it.copy(mealCategory = category) }

    fun confirmAdd() {
        val state = _uiState.value
        val food = state.selectedFood ?: return
        val amount = state.amountGrams.toDoubleOrNull()?.takeIf { it > 0 } ?: return
        val factor = amount / 100.0
        viewModelScope.launch {
            foodEntryRepository.add(
                FoodEntry(
                    foodName = food.name,
                    brand = food.brand,
                    amountGrams = amount,
                    kcal = food.kcalPer100g * factor,
                    proteinG = food.proteinPer100g * factor,
                    carbsG = food.carbsPer100g * factor,
                    fatG = food.fatPer100g * factor,
                    sugarG = food.sugarPer100g * factor,
                    fiberG = food.fiberPer100g * factor,
                    mealCategory = state.mealCategory,
                    date = LocalDate.now(),
                    timestampMs = System.currentTimeMillis(),
                )
            )
            _uiState.update { it.copy(selectedFood = null, amountGrams = "100", entryAdded = true) }
        }
    }

    fun handleBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            foodSearchRepository.getByBarcode(barcode)
                .onSuccess { food ->
                    if (food != null) {
                        _uiState.update { it.copy(isLoading = false, selectedFood = food, amountGrams = "100") }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Produkt nicht gefunden") }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unbekannter Fehler") }
                }
        }
    }

    fun entryAddedHandled() = _uiState.update { it.copy(entryAdded = false) }
}

data class AddFoodUiState(
    val query: String = "",
    val results: List<Food> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedFood: Food? = null,
    val amountGrams: String = "100",
    val mealCategory: MealCategory = MealCategory.SNACK,
    val entryAdded: Boolean = false,
)
