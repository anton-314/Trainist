package dev.antonlammers.trainist.ui.tutorial

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
 * Runs the guided tour: which [TutorialStep] is showing, and nothing else — the spotlight geometry
 * lives in [TutorialAnchors], the texts in the step list, the navigation in `AppNavigation`.
 *
 * The tour is *pending* until it has been walked or skipped, and that flag is persisted: it is set
 * by the onboarding's "I'm new here" path (which finishes into the main app, where the tour then
 * starts by itself) and by [start] from the Help Center. Persisting it also means a process death
 * mid-tour resumes the tour instead of silently dropping it — the flag is only cleared once the
 * user actually reaches the end or skips out.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<TutorialStep?>(null)

    /** The step being shown, or null while no tour is running. */
    val step: StateFlow<TutorialStep?> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            if (settingsRepository.isTutorialPending()) _step.value = TutorialStep.first
        }
    }

    /** Starts the tour from the top — the Help Center's "show the tour again" row. */
    fun start() {
        _step.value = TutorialStep.first
        viewModelScope.launch { settingsRepository.setTutorialPending(true) }
    }

    /** Advances one step; ends the tour after the last one. */
    fun next() {
        val current = _step.value ?: return
        val next = current.next()
        if (next == null) finish() else _step.value = next
    }

    /** Steps back; leaving the tour when there is nothing to go back to (the system back gesture). */
    fun back() {
        val current = _step.value ?: return
        val previous = current.previous()
        if (previous == null) finish() else _step.value = previous
    }

    /** Leaves the tour for good — reachable from every step. */
    fun skip() = finish()

    private fun finish() {
        _step.value = null
        viewModelScope.launch { settingsRepository.setTutorialPending(false) }
    }
}
