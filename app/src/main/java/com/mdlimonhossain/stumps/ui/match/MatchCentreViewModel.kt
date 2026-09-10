package com.mdlimonhossain.stumps.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.local.db.match.MatchEntity
import com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.domain.repository.HeadToHeadRecord
import com.mdlimonhossain.stumps.domain.repository.InningsSummary
import com.mdlimonhossain.stumps.domain.repository.MatchRepository
import com.mdlimonhossain.stumps.domain.repository.TournamentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Everything the tabbed Match Centre screen needs, loaded once when it's opened for a given matchId. */
data class MatchCentreUiState(
    val isLoading: Boolean = true,
    val match: MatchEntity? = null,
    val teamAName: String = "",
    val teamBName: String = "",
    val innings: List<InningsSummary> = emptyList(),
    val tournament: TournamentEntity? = null,
    val teamAPlayers: List<PlayerEntity> = emptyList(),
    val teamBPlayers: List<PlayerEntity> = emptyList(),
    val headToHead: HeadToHeadRecord? = null // null = not computed yet (or this match's teams have never met before), see MatchRepository.headToHead
)

/**
 * Loads everything Match Centre's five tabs need about ONE match: the match/team/tournament
 * facts (for Info), and every innings played so far with its full ball log (for Summary,
 * Scorecard, Stats and Balls — all four of those tabs are just different VIEWS of the same
 * `innings` list, no separate loading needed per tab).
 *
 * This reads everything ONCE (not a live/reactive stream) — good enough for both a finished
 * match's scorecard and a quick look at a live one, without the extra complexity of keeping
 * five tabs all reactively in sync with balls arriving in real time.
 */
class MatchCentreViewModel(
    private val matchId: String,
    private val matchRepository: MatchRepository,
    private val tournamentRepository: TournamentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchCentreUiState())
    val uiState: StateFlow<MatchCentreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val match = matchRepository.getMatchOnce(matchId)
            val names = matchRepository.getTeamNames(matchId)
            val innings = matchRepository.getFinalInningsStates(matchId)
            // .first() on a Flow just grabs whatever the CURRENT value is and stops listening —
            // we want one snapshot here, not an ongoing subscription like the live scoring screen needs.
            val tournament = match?.tournamentId?.let { tournamentRepository.observeTournament(it).first() }
            val teamAPlayers = match?.teamAId?.let { matchRepository.observePlayersForTeam(it).first() } ?: emptyList()
            val teamBPlayers = match?.teamBId?.let { matchRepository.observePlayersForTeam(it).first() } ?: emptyList()
            val headToHead = if (match != null && names != null) {
                matchRepository.headToHead(match.createdByUid, names.first, names.second, excludeMatchId = matchId)
            } else null

            _uiState.value = MatchCentreUiState(
                isLoading = false,
                match = match,
                teamAName = names?.first ?: "Team A",
                teamBName = names?.second ?: "Team B",
                innings = innings,
                tournament = tournament,
                teamAPlayers = teamAPlayers,
                teamBPlayers = teamBPlayers,
                headToHead = headToHead
            )
        }
    }

    /** See AuthViewModel.Factory for what a Factory is and why. */
    class Factory(
        private val matchId: String,
        private val matchRepository: MatchRepository,
        private val tournamentRepository: TournamentRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MatchCentreViewModel(matchId, matchRepository, tournamentRepository) as T
    }
}
