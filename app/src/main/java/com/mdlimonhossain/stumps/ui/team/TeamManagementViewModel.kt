package com.mdlimonhossain.stumps.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
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

    /**
     * Called when the user finishes the "new team" form — just a name and an optional
     * location now (players are added afterwards from the new team's own Players tab, see
     * TeamDetailScreen.kt) — `onCreated` is handed the brand-new team's id, so the caller can
     * jump straight into it.
     */
    fun createTeam(uid: String, name: String, location: String?, onCreated: (teamId: String) -> Unit) {
        viewModelScope.launch {
            val teamId = repository.createTeam(uid, name, location = location)
            onCreated(teamId)
        }
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(private val repository: TeamRepository, private val uid: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TeamManagementViewModel(repository, uid) as T
    }
}
