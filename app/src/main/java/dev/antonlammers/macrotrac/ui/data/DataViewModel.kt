package dev.antonlammers.macrotrac.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.data.backup.BackupExporter
import dev.antonlammers.macrotrac.data.backup.BackupImporter
import dev.antonlammers.macrotrac.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(reminderEnabled = settingsRepository.isReminderEnabled()) }
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        _uiState.update { it.copy(reminderEnabled = enabled) }
        viewModelScope.launch { settingsRepository.setReminderEnabled(enabled) }
    }

    fun export(onUri: (Uri) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { exporter.export() }
                .onSuccess { uri ->
                    _uiState.update { it.copy(isLoading = false) }
                    onUri(uri)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, message = "Export fehlgeschlagen: ${e.message}") }
                }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { importer.import(uri) }
                .onSuccess { result ->
                    val parts = buildList {
                        val food = "${result.foodImported} Lebensmitteleinträge" +
                            if (result.foodSkipped > 0) " (${result.foodSkipped} übersprungen)" else ""
                        if (result.foodImported > 0 || result.foodSkipped > 0) add(food)
                        if (result.weightImported > 0) add("${result.weightImported} Gewichtseinträge")
                        if (result.goalRestored) add("Ziele wiederhergestellt")
                        if (result.customFoodsImported > 0) add("${result.customFoodsImported} eigene Lebensmittel")
                        if (result.exercisesImported > 0) add("${result.exercisesImported} eigene Übungen")
                        if (result.templatesImported > 0) add("${result.templatesImported} Vorlagen")
                        if (result.sessionsImported > 0) add("${result.sessionsImported} Einheiten")
                    }
                    val msg = if (parts.isEmpty()) "Nichts importiert" else parts.joinToString(", ")
                    _uiState.update { it.copy(isLoading = false, message = msg) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, message = "Import fehlgeschlagen: ${e.message}") }
                }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

data class DataUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val reminderEnabled: Boolean = true,
)
