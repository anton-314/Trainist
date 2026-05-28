package dev.antonlammers.macrotrac.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.macrotrac.data.backup.CsvExporter
import dev.antonlammers.macrotrac.data.backup.CsvImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    private val exporter: CsvExporter,
    private val importer: CsvImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

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
                    val msg = "${result.imported} Einträge importiert" +
                            if (result.skipped > 0) ", ${result.skipped} übersprungen" else ""
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
)
