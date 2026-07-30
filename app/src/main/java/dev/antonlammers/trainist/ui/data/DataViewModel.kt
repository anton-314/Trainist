package dev.antonlammers.trainist.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.backup.BackupExporter
import dev.antonlammers.trainist.domain.backup.BackupImporter
import dev.antonlammers.trainist.domain.repository.SettingsRepository
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

    /** @param onUri receives the backup's URI in string form — the UI parses it back into a `Uri`. */
    fun export(onUri: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { exporter.export() }
                .onSuccess { uri ->
                    _uiState.update { it.copy(isLoading = false) }
                    onUri(uri)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, message = DataMessage.ExportFailed(e.message)) }
                }
        }
    }

    /**
     * @param onSuccess invoked (after the result message is set) only when the import completed
     * without throwing — used by the onboarding quick-start to advance into the app once a backup
     * has been restored. Callers that don't care (the Settings screen) omit it.
     */
    fun import(uri: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { importer.import(uri) }
                .onSuccess { result ->
                    val message = DataMessage.ImportResult(
                        foodImported = result.foodImported,
                        foodSkipped = result.foodSkipped,
                        weightImported = result.weightImported,
                        goalRestored = result.goalRestored,
                        customFoodsImported = result.customFoodsImported,
                        exercisesImported = result.exercisesImported,
                        templatesImported = result.templatesImported,
                        sessionsImported = result.sessionsImported,
                        measurementsImported = result.measurementsImported,
                    )
                    _uiState.update { it.copy(isLoading = false, message = message) }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, message = DataMessage.ImportFailed(e.message)) }
                }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

data class DataUiState(
    val isLoading: Boolean = false,
    val message: DataMessage? = null,
    val reminderEnabled: Boolean = true,
)

/**
 * Backup export/import result, kept as structured data rather than a formatted String — the
 * ViewModel has no Compose context to call `stringResource()`, so the UI layer resolves the
 * display text (see `DataMessage.toDisplayString()` in `DataMessageFormat.kt`).
 */
sealed interface DataMessage {
    data class ExportFailed(val error: String?) : DataMessage
    data class ImportFailed(val error: String?) : DataMessage
    data class ImportResult(
        val foodImported: Int,
        val foodSkipped: Int,
        val weightImported: Int,
        val goalRestored: Boolean,
        val customFoodsImported: Int,
        val exercisesImported: Int,
        val templatesImported: Int,
        val sessionsImported: Int,
        val measurementsImported: Int,
    ) : DataMessage
}
