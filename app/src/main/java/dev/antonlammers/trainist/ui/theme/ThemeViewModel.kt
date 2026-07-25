package dev.antonlammers.trainist.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.antonlammers.trainist.domain.model.ThemeMode
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app's appearance choice (system / light / dark).
 *
 * Deliberately a pass-through onto the repository's own [StateFlow] rather than a `stateIn` copy:
 * the flow is read by two separately-scoped composables (the settings row and `MainActivity`'s
 * whole-app wrapper), and re-hosting it per ViewModel would hand each its own initial value — a
 * cold start would then paint one frame in the wrong shade before the persisted choice arrived.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }
}
