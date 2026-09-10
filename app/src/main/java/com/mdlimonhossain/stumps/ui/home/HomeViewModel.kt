package com.mdlimonhossain.stumps.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.domain.repository.CareerStats
import com.mdlimonhossain.stumps.domain.repository.MatchRepository
import com.mdlimonhossain.stumps.domain.repository.StatsRepository
import com.mdlimonhossain.stumps.domain.repository.TournamentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the Home dashboard screen needs to draw one match card — just the bits worth
 * showing in a small carousel card, not the FULL match detail (that lives in Match Centre).
 */
data class HomeMatchCard(
    val matchId: String,
    val tournamentId: String?,
    val teamAName: String,
    val teamBName: String,
    val status: String, // "LIVE", "COMPLETED", etc. — copied straight from MatchEntity.status
    val isLive: Boolean,
    val oversLimit: Int
)

/** Everything the Home dashboard screen needs to draw itself. */
data class HomeUiState(
    val isLoadingMatches: Boolean = true,
    val matches: List<HomeMatchCard> = emptyList(),
    val tournaments: List<TournamentEntity> = emptyList(),
    val careerStats: CareerStats = CareerStats()
)

/**
 * Feeds the Home dashboard screen (the app's main "front page" once logged in) with three
 * things: this user's recent matches, this user's tournaments, and a quick career-stats summary
 * for the little profile card. It reads from three different repositories and combines them
 * into one simple HomeUiState the screen can just display, without the screen needing to know
 * anything about matches/tournaments/stats being separate systems underneath.
 */
class HomeViewModel(
    private val uid: String,
    private val playerName: String,
    private val matchRepository: MatchRepository,
    private val tournamentRepository: TournamentRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Three separate coroutines, one per data source — they don't depend on each other, so
        // there's no reason to make one wait for another to finish.
        viewModelScope.launch {
            matchRepository.observeMatchesForUser(uid).collect { matches ->
                // Only the newest handful belong on a dashboard carousel — the full list lives
                // on the "My Matches" history screen instead.
                val recent = matches.sortedByDescending { it.createdAt }.take(6)
                val cards = recent.map { match ->
                    // Team names aren't stored on MatchEntity itself (only team ids are), so we
                    // look them up separately for each match.
                    val names = matchRepository.getTeamNames(match.id)
                    HomeMatchCard(
                        matchId = match.id,
                        tournamentId = match.tournamentId,
                        teamAName = names?.first ?: "Team A",
                        teamBName = names?.second ?: "Team B",
                        status = match.status,
                        isLive = match.isLive,
                        oversLimit = match.oversLimit
                    )
                }
                _uiState.value = _uiState.value.copy(isLoadingMatches = false, matches = cards)
            }
        }
        viewModelScope.launch {
            tournamentRepository.observeTournamentsForUser(uid).collect { tournaments ->
                _uiState.value = _uiState.value.copy(tournaments = tournaments.sortedByDescending { it.createdAt })
            }
        }
        viewModelScope.launch {
            // Career stats are keyed by the exact player name used at scoring time (see
            // StatsRepository) — we use this user's own profile name as a best-effort match.
            // A blank name (still finishing profile setup) means "0 of everything", so skip it.
            if (playerName.isNotBlank()) {
                _uiState.value = _uiState.value.copy(careerStats = statsRepository.careerStatsFor(uid, playerName))
            }
        }
    }

    /** Android's required way to hand this ViewModel its constructor arguments — see AuthViewModel.Factory for the fuller explanation. */
    class Factory(
        private val uid: String,
        private val playerName: String,
        private val matchRepository: MatchRepository,
        private val tournamentRepository: TournamentRepository,
        private val statsRepository: StatsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(uid, playerName, matchRepository, tournamentRepository, statsRepository) as T
    }
}
