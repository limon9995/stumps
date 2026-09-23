package com.mdlimonhossain.stumps.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.remote.sync.LiveBroadcastRepository
import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType
import com.mdlimonhossain.stumps.domain.repository.LiveInnings
import com.mdlimonhossain.stumps.domain.repository.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The ViewModel behind the live scoring screen. See AuthViewModel.kt for a general explanation
 * of what a ViewModel is — this one's job is: know which innings we're currently scoring, keep
 * the live scoreboard up to date, and forward every button tap (run scored, wicket, undo, go
 * live) to MatchRepository to actually save it.
 */
class MatchViewModel(
    private val repository: MatchRepository,
    private val liveBroadcastRepository: LiveBroadcastRepository
) : ViewModel() {

    // Which innings are we currently watching/scoring? Starts as null (nothing selected yet).
    private val inningsId = MutableStateFlow<String?>(null)

    // The live, always-current scoreboard for whichever innings is selected above. Every time
    // inningsId changes (e.g. moving to the 2nd innings), flatMapLatest automatically switches
    // to watching the NEW innings instead. stateIn turns this into something the screen can
    // read a current snapshot from at any time, not just "subscribe and wait for updates".
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val liveInnings: StateFlow<LiveInnings?> = inningsId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeLiveInnings(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Tells this ViewModel which innings to start watching/scoring. */
    fun watchInnings(id: String) {
        inningsId.value = id
    }

    /** Creates a brand new standalone match (not part of a tournament) and reports back its id + first innings id. */
    fun createQuickMatch(
        createdByUid: String,
        teamAName: String,
        teamAPlayers: List<String>,
        teamBName: String,
        teamBPlayers: List<String>,
        oversLimit: Int,
        tossWinnerIsTeamA: Boolean,
        tossDecisionIsBat: Boolean,
        openingStrikerId: String,
        openingNonStrikerId: String,
        openingBowlerId: String,
        format: com.mdlimonhossain.stumps.domain.model.MatchFormat? = null,
        onCreated: (matchId: String, inningsId: String) -> Unit
    ) {
        viewModelScope.launch {
            val matchId = repository.createQuickMatch(
                createdByUid, teamAName, teamAPlayers, teamBName, teamBPlayers,
                oversLimit, tossWinnerIsTeamA, tossDecisionIsBat,
                openingStrikerId, openingNonStrikerId, openingBowlerId,
                format = format
            )
            // first (and only, at creation time) innings row for this match
            val firstInningsId = repository.observeInningsForMatch(matchId).first().first().id
            onCreated(matchId, firstInningsId)
        }
    }

    /** Called when the scorer taps a run button (0, 1, 2, 3, 4, or 6). */
    fun recordRuns(runs: Int, shotAngleDegrees: Int? = null, selectedBowlerId: String? = null) {
        val current = liveInnings.value ?: return // can't record a ball if we don't know the current state yet
        viewModelScope.launch {
            val ball = repository.recordBall(
                innings = current.innings, state = current.state,
                runsOffBat = runs, extraType = null, extraRuns = 0, runsRun = runs,
                isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null,
                selectedBowlerId = selectedBowlerId, shotAngleDegrees = shotAngleDegrees
            )
            // If this match is being broadcast live, also copy this ball up to Firestore so
            // viewers see it too. mirrorIfLive just skips this entirely if the match isn't live.
            mirrorIfLive(current.match.isLive) { liveBroadcastRepository.pushBall(current.match.id, ball) }
        }
    }

    /** Called when the scorer records a wide, no-ball, bye, or leg-bye. */
    fun recordExtra(type: ExtraType, extraRuns: Int, runsRun: Int, selectedBowlerId: String? = null) {
        val current = liveInnings.value ?: return
        viewModelScope.launch {
            val ball = repository.recordBall(
                innings = current.innings, state = current.state,
                runsOffBat = 0, extraType = type, extraRuns = extraRuns, runsRun = runsRun,
                isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null,
                selectedBowlerId = selectedBowlerId
            )
            mirrorIfLive(current.match.isLive) { liveBroadcastRepository.pushBall(current.match.id, ball) }
        }
    }

    /** Called when the scorer records a wicket, picking who got out, how, and who's coming in next. */
    fun recordWicket(
        dismissalType: DismissalType,
        dismissedPlayerId: String,
        newBatsmanId: String?,
        runsCompletedBeforeWicket: Int = 0,
        selectedBowlerId: String? = null,
        fielderId: String? = null
    ) {
        val current = liveInnings.value ?: return
        viewModelScope.launch {
            val ball = repository.recordBall(
                innings = current.innings, state = current.state,
                runsOffBat = runsCompletedBeforeWicket, extraType = null, extraRuns = 0,
                runsRun = runsCompletedBeforeWicket,
                isWicket = true, dismissalType = dismissalType, dismissedPlayerId = dismissedPlayerId,
                newBatsmanId = newBatsmanId, selectedBowlerId = selectedBowlerId, fielderId = fielderId
            )
            mirrorIfLive(current.match.isLive) { liveBroadcastRepository.pushBall(current.match.id, ball) }
        }
    }

    /**
     * Called when a batsman retires hurt (feeling unwell/injured) and a replacement comes in.
     * Unlike recordWicket, this does NOT count as a dismissal (isWicket = false) — the real
     * cricket rule is that a retired batsman can come back later to resume their innings, so
     * they must not be shown as "out". dismissalType is still set to RETIRED_HURT purely so
     * ScoringEngine can recognise this ball as a retirement rather than an ordinary one; see its
     * own comment on how it's handled specially.
     */
    fun recordRetiredHurt(retiredPlayerId: String, replacementBatsmanId: String) {
        val current = liveInnings.value ?: return
        viewModelScope.launch {
            val ball = repository.recordBall(
                innings = current.innings, state = current.state,
                runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
                isWicket = false, dismissalType = DismissalType.RETIRED_HURT,
                dismissedPlayerId = retiredPlayerId, newBatsmanId = replacementBatsmanId,
                selectedBowlerId = null
            )
            mirrorIfLive(current.match.isLive) { liveBroadcastRepository.pushBall(current.match.id, ball) }
        }
    }

    /** Called when the scorer taps "Undo" — removes the most recent ball. */
    fun undoLastBall() {
        val current = liveInnings.value ?: return
        viewModelScope.launch {
            val removed = repository.undoLastBall(current.innings.id)
            if (removed != null) {
                // Also remove that same ball from Firestore, so viewers' scoreboards un-do too.
                mirrorIfLive(current.match.isLive) {
                    liveBroadcastRepository.removeBall(current.match.id, current.innings.id, removed.id)
                }
            }
        }
    }

    /** Called when the first innings finishes and the second team starts batting (chasing). */
    fun startNextInnings(
        matchId: String,
        battingIsTeamA: Boolean,
        openingStrikerId: String,
        openingNonStrikerId: String,
        openingBowlerId: String,
        targetRuns: Int,
        onStarted: (String) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.startNextInnings(
                matchId, battingIsTeamA,
                openingStrikerId, openingNonStrikerId, openingBowlerId, targetRuns
            )
            // If this match is live, also push the new innings' setup info to Firestore so
            // viewers' scoreboards know about the target and who's opening the chase.
            val match = repository.getMatchOnce(matchId)
            if (match?.isLive == true) {
                val innings = repository.observeInningsForMatch(matchId).first().first { it.id == id }
                val names = repository.getTeamNames(matchId)
                if (names != null) {
                    val (teamAName, teamBName) = names
                    val battingName = if (battingIsTeamA) teamAName else teamBName
                    val bowlingName = if (battingIsTeamA) teamBName else teamAName
                    liveBroadcastRepository.pushInnings(matchId, innings, battingName, bowlingName)
                }
            }
            onStarted(id)
        }
    }

    /** Turns on live broadcast for the match currently being scored and returns its share code. */
    fun goLive(onReady: (shareCode: String) -> Unit) {
        val current = liveInnings.value ?: return
        viewModelScope.launch {
            val names = repository.getTeamNames(current.match.id) ?: return@launch
            // Reuse the existing share code if this match somehow already had one, otherwise make a new one.
            val code = current.match.shareCode ?: liveBroadcastRepository.generateShareCode()
            repository.setLive(current.match.id, true, code) // save locally
            liveBroadcastRepository.goLive(current.match, names.first, names.second, code) // publish to Firestore
            liveBroadcastRepository.pushInnings(current.match.id, current.innings, names.first, names.second)
            onReady(code)
        }
    }

    /** Turns off live broadcast for the current match. */
    fun stopLive() {
        val current = liveInnings.value ?: return
        viewModelScope.launch {
            repository.setLive(current.match.id, false, null)
            liveBroadcastRepository.stopLive(current.match.id)
        }
    }

    // A tiny helper to avoid repeating "if (isLive) { ... }" everywhere above — just runs the
    // given block of code only when the match is actually being broadcast live.
    private inline fun mirrorIfLive(isLive: Boolean, action: () -> Unit) {
        if (isLive) action()
    }

    /** See AuthViewModel.Factory for what a Factory is and why ViewModels with constructor arguments need one. */
    class Factory(
        private val repository: MatchRepository,
        private val liveBroadcastRepository: LiveBroadcastRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MatchViewModel(repository, liveBroadcastRepository) as T
    }
}
