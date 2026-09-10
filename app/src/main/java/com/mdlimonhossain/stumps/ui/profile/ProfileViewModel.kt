package com.mdlimonhossain.stumps.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.domain.repository.CareerStats
import com.mdlimonhossain.stumps.domain.repository.PlayerProfileStats
import com.mdlimonhossain.stumps.domain.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the Player Profile screen needs, on top of the UserEntity it's already given directly. */
data class ProfileUiState(
    val stats: PlayerProfileStats = PlayerProfileStats(CareerStats(), emptyMap(), emptyList())
)

/** Loads this user's career stats + recent form for the Player Profile screen — see StatsRepository for how those are calculated. */
class ProfileViewModel(
    uid: String,
    playerName: String,
    statsRepository: StatsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // A blank name (still finishing profile setup) can't match anything in match records.
            if (playerName.isNotBlank()) {
                // recentFormLimit = 50 covers both the small "Recent Form" strip on the Overview
                // tab (which only shows the first few) and the fuller Match-wise statistics list.
                _uiState.value = ProfileUiState(statsRepository.profileStatsFor(uid, playerName, recentFormLimit = 50))
            }
        }
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(
        private val uid: String,
        private val playerName: String,
        private val statsRepository: StatsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(uid, playerName, statsRepository) as T
    }
}
