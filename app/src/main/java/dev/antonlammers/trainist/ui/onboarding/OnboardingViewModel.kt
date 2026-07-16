package dev.antonlammers.trainist.ui.onboarding

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
 * Gates the first-launch welcome flow. Reads the persisted "onboarding completed" flag once and
 * decides whether the app shows the [OnboardingScreen] or goes straight to the main navigation.
 * Owns only the flag — the goals guide reuses [dev.antonlammers.trainist.ui.goals.GoalsViewModel]
 * and the backup quick-start reuses [dev.antonlammers.trainist.ui.data.DataViewModel].
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Loading)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value =
                if (settingsRepository.isOnboardingCompleted()) OnboardingState.Completed
                else OnboardingState.Onboarding
        }
    }

    /** Marks onboarding done (persisted) and drops the welcome flow — called by every path out. */
    fun complete() {
        _state.value = OnboardingState.Completed
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }
}

/** Which root surface to show. [Loading] avoids a welcome-screen flash before the flag is read. */
enum class OnboardingState { Loading, Onboarding, Completed }
