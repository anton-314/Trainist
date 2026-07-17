package dev.antonlammers.trainist.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Settings hub's language picker: reads/writes the per-app language via [SettingsRepository],
 * which delegates to `AppCompatDelegate` (already persists the choice itself). `tag` is a BCP-47
 * language tag ("de", "en"), or `null` to follow the system language.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _language = MutableStateFlow<String?>(null)
    val language: StateFlow<String?> = _language.asStateFlow()

    init {
        viewModelScope.launch { _language.value = settingsRepository.getAppLanguage() }
    }

    fun setLanguage(tag: String?) {
        _language.value = tag
        viewModelScope.launch { settingsRepository.setAppLanguage(tag) }
    }
}
