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
 * The Settings hub's "country for food search" picker. The stored value is an ISO 3166-1 alpha-2
 * code, or `null` for automatic detection (mobile network → SIM → device region).
 *
 * The list of countries is **not** held here: it is derived from `Locale` in the UI layer, where the
 * display names have to be localized anyway, and a ViewModel holding 250 translated strings would
 * break the app-wide "no display text in a ViewModel" rule for no gain.
 */
@HiltViewModel
class SearchCountryViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _country = MutableStateFlow<String?>(null)
    /** The user's explicit choice, or null while detection is left automatic. */
    val country: StateFlow<String?> = _country.asStateFlow()

    init {
        viewModelScope.launch { _country.value = settingsRepository.getSearchCountry() }
    }

    fun setCountry(code: String?) {
        _country.value = code
        viewModelScope.launch { settingsRepository.setSearchCountry(code) }
    }
}
