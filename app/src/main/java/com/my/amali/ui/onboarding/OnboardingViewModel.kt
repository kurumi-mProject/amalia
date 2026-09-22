package com.my.amali.ui.onboarding

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.core.di.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state of the onboarding flow.
 *
 * @property currentPage zero-based index of the currently visible slide.
 * @property pageCount   total number of slides in the onboarding pager.
 * @property isFinished  true when the user has completed (or skipped)
 *                       onboarding and navigation to the assistant should start.
 * @property showGuide   true while the step-by-step guide overlay is displayed.
 */
data class OnboardingState(
    val currentPage: Int = 0,
    val pageCount: Int = 4,
    val isFinished: Boolean = false,
    val showGuide: Boolean = false,
)

/**
 * ViewModel backing [OnboardingScreen].
 *
 * Owns the pager position, the finished flag and the guide visibility.
 * On [finish] the "onboarding completed" flag is persisted to DataStore
 * (resolved via [ServiceLocator]) so onboarding is not shown again on
 * the next launch.
 */
class OnboardingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingState())

    /** Observable onboarding state. */
    val uiState: StateFlow<OnboardingState> = _uiState.asStateFlow()

    /**
     * Called by the UI when the user settles on a page by swiping the pager.
     * Keeps the state in sync with manual gestures.
     */
    fun onPageChanged(page: Int) {
        _uiState.update { state ->
            val bounded = page.coerceIn(0, state.pageCount - 1)
            if (bounded != state.currentPage) {
                state.copy(currentPage = bounded)
            } else {
                state
            }
        }
    }

    /** Advances to the next slide, unless already on the last one. */
    fun nextPage() {
        _uiState.update { state ->
            if (state.currentPage < state.pageCount - 1) {
                state.copy(currentPage = state.currentPage + 1)
            } else {
                state
            }
        }
    }

    /** Returns to the previous slide, unless already on the first one. */
    fun prevPage() {
        _uiState.update { state ->
            if (state.currentPage > 0) {
                state.copy(currentPage = state.currentPage - 1)
            } else {
                state
            }
        }
    }

    /**
     * Completes onboarding: marks the state as finished (which triggers
     * navigation to the assistant) and persists the "completed" flag.
     *
     * Persistence is best-effort: a DataStore failure never blocks
     * navigation, and the guard makes repeated calls a no-op.
     */
    fun finish() {
        if (_uiState.value.isFinished) return
        _uiState.update { it.copy(isFinished = true) }

        viewModelScope.launch {
            try {
                ServiceLocator.dataStore.edit { preferences ->
                    preferences[KEY_ONBOARDING_COMPLETED] = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-fatal: the flag is best-effort, navigation is already done.
            }
        }
    }

    /** Shows the step-by-step guide overlay. */
    fun startGuide() {
        _uiState.update { it.copy(showGuide = true) }
    }

    /** Hides the step-by-step guide overlay. */
    fun dismissGuide() {
        _uiState.update { it.copy(showGuide = false) }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
