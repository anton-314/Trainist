package dev.antonlammers.trainist.ui.foodimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.backup.FoodEntryFile
import dev.antonlammers.trainist.domain.backup.FoodEntryFileReader
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.repository.FoodEntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Imports the food entries of a CSV file opened with the app ("Öffnen mit Trainist"), e.g. one an
 * AI assistant generated: [open] reads the file into a preview, nothing is written until the user
 * [confirm]s. Entries are only ever **added** — an import can't replace or delete existing data.
 */
@HiltViewModel
class FoodImportViewModel @Inject constructor(
    private val reader: FoodEntryFileReader,
    private val foodEntryRepository: FoodEntryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodImportUiState())
    val uiState: StateFlow<FoodImportUiState> = _uiState.asStateFlow()

    /** @param uri the opened file's `Uri` in string form. */
    fun open(uri: String) {
        _uiState.update { it.copy(phase = FoodImportPhase.Loading) }
        viewModelScope.launch {
            val phase = when (val file = reader.read(uri)) {
                is FoodEntryFile.Entries -> FoodImportPhase.Preview(file.entries, file.skipped)
                FoodEntryFile.NoValidRows -> FoodImportPhase.Failed(FoodImportError.NO_VALID_ROWS)
                FoodEntryFile.NotFoodEntries -> FoodImportPhase.Failed(FoodImportError.NOT_FOOD_ENTRIES)
                FoodEntryFile.Unreadable -> FoodImportPhase.Failed(FoodImportError.UNREADABLE)
            }
            _uiState.update { it.copy(phase = phase) }
        }
    }

    /** Saves the previewed entries. A no-op unless a preview is showing, so a double tap saves once. */
    fun confirm() {
        val preview = _uiState.value.phase as? FoodImportPhase.Preview ?: return
        _uiState.update { it.copy(phase = FoodImportPhase.Idle) }
        viewModelScope.launch {
            runCatching { preview.entries.forEach { foodEntryRepository.add(it) } }
                .onSuccess { _uiState.update { it.copy(importedCount = preview.entries.size) } }
                .onFailure {
                    _uiState.update { it.copy(phase = FoodImportPhase.Failed(FoodImportError.SAVE_FAILED)) }
                }
        }
    }

    /** Cancels a preview or acknowledges an error — either way nothing is (further) imported. */
    fun dismiss() = _uiState.update { it.copy(phase = FoodImportPhase.Idle) }

    fun clearImportedCount() = _uiState.update { it.copy(importedCount = null) }
}

/** @property importedCount set once a confirmed import is saved; the UI reports it, then clears it. */
data class FoodImportUiState(
    val phase: FoodImportPhase = FoodImportPhase.Idle,
    val importedCount: Int? = null,
)

sealed interface FoodImportPhase {
    data object Idle : FoodImportPhase
    data object Loading : FoodImportPhase
    data class Preview(val entries: List<FoodEntry>, val skipped: Int) : FoodImportPhase
    data class Failed(val error: FoodImportError) : FoodImportPhase
}

enum class FoodImportError { UNREADABLE, NOT_FOOD_ENTRIES, NO_VALID_ROWS, SAVE_FAILED }
