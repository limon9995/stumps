package com.mdlimonhossain.stumps.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.domain.repository.TeamRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The ViewModel behind "আমার টিম" (my teams) — the list of saved teams, and creating new ones. */
class TeamManagementViewModel(private val repository: TeamRepository, uid: String) : ViewModel() {
    // A live, always up-to-date list of every team this user has saved.
    val teams: StateFlow<List<TeamEntity>> = repository.observeTeamsForUser(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Called when the user finishes the "new team" form — saves the team and all its players. */
    fun createTeam(uid: String, name: String, playerNames: List<String>, onCreated: () -> Unit) {
        viewModelScope.launch {
            // Every player typed in on this screen is given the same default role (BATSMAN) for
            // now — there's no per-player role picker on this simple form yet.
            repository.createTeam(uid, name, playerNames.map { it to PlayerRole.BATSMAN })
            onCreated()
        }
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(private val repository: TeamRepository, private val uid: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TeamManagementViewModel(repository, uid) as T
    }
}
