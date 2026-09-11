package com.mdlimonhossain.stumps.ui.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity
import com.mdlimonhossain.stumps.domain.repository.TournamentLeaderboards
import com.mdlimonhossain.stumps.domain.repository.TournamentRepository
import com.mdlimonhossain.stumps.domain.tournament.TeamStanding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The ViewModel behind the tournament LIST screen — every tournament this user runs, and creating a new one. */
class TournamentListViewModel(private val repository: TournamentRepository, uid: String) : ViewModel() {
    val tournaments: StateFlow<List<TournamentEntity>> = repository.observeTournamentsForUser(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Called when the "নতুন টুর্নামেন্ট" (new tournament) form is submitted. Teams are no longer picked here — they're added afterwards from the new tournament's own Teams tab (see TournamentDetailScreen.kt). */
    fun createTournament(
        uid: String,
        name: String,
        overs: Int,
        venue: String?,
        clubName: String?,
        city: String?,
        season: String?,
        startDate: Long?,
        endDate: Long?,
        ballType: String?,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.createTournament(
                organizerUid = uid, name = name, oversPerMatch = overs, venue = venue,
                clubName = clubName, city = city, season = season,
                startDate = startDate, endDate = endDate, ballType = ballType
            )
            onCreated(id)
        }
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(private val repository: TournamentRepository, private val uid: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TournamentListViewModel(repository, uid) as T
    }
}

/** The ViewModel behind ONE tournament's detail screen — its fixtures, points table, and leaderboards. */
class TournamentDetailViewModel(private val repository: TournamentRepository, tournamentId: String) : ViewModel() {
    val tournament: StateFlow<TournamentEntity?> = repository.observeTournament(tournamentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val teams: StateFlow<List<TournamentTeamEntity>> = repository.observeTournamentTeams(tournamentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val fixtures: StateFlow<List<TournamentFixtureEntity>> = repository.observeFixtures(tournamentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unlike tournament/teams/fixtures above, the points table and leaderboards are NOT
    // automatically live-updating Flows — they're calculated on demand (see refreshStandings
    // below) because working them out means replaying every match's ball log, which is too
    // expensive to redo constantly. Instead the screen asks for a fresh calculation whenever
    // the standings/leaderboard tab is opened.
    private val _standings = MutableStateFlow<List<TeamStanding>>(emptyList())
    val standings: StateFlow<List<TeamStanding>> = _standings

    private val _leaderboards = MutableStateFlow(TournamentLeaderboards(emptyList(), emptyList()))
    val leaderboards: StateFlow<TournamentLeaderboards> = _leaderboards

    // Same "calculate on demand, don't keep a live Flow" reasoning as standings/leaderboards
    // above — the Home tab's "Tournament Boundaries" numbers.
    private val _boundaryCounts = MutableStateFlow(0 to 0) // sixes to fours
    val boundaryCounts: StateFlow<Pair<Int, Int>> = _boundaryCounts

    /** Recalculates the points table right now and updates `standings` with the fresh result. */
    fun refreshStandings(tournamentId: String) {
        viewModelScope.launch { _standings.value = repository.computeStandings(tournamentId) }
    }

    /** Recalculates Orange Cap / Purple Cap (and the rest of the Statistics card grid) right now and updates `leaderboards` with the fresh result. */
    fun refreshLeaderboards(tournamentId: String) {
        viewModelScope.launch { _leaderboards.value = repository.computeLeaderboards(tournamentId) }
    }

    /** Recalculates total sixes/fours hit so far in this tournament. */
    fun refreshBoundaryCounts(tournamentId: String) {
        viewModelScope.launch { _boundaryCounts.value = repository.computeBoundaryCounts(tournamentId) }
    }

    /** Adds one more of the organizer's own saved teams to this tournament — see TournamentRepository.addTeamToTournament. */
    fun addTeam(tournamentId: String, teamId: String) {
        viewModelScope.launch { repository.addTeamToTournament(tournamentId, teamId) }
    }

    /** Renames this tournament — used by the Home tab's "More" menu. */
    fun rename(tournament: TournamentEntity, newName: String) {
        viewModelScope.launch { repository.renameTournament(tournament, newName) }
    }

    /** Permanently deletes this tournament, then calls `onDeleted` (the caller navigates back). */
    fun delete(tournament: TournamentEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTournament(tournament)
            onDeleted()
        }
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(private val repository: TournamentRepository, private val tournamentId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TournamentDetailViewModel(repository, tournamentId) as T
    }
}
