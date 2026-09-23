package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import kotlinx.coroutines.flow.first

/** What ResumeMatchScreen has figured out so far about whether/how this match can be resumed. */
private sealed interface ResumeLoadState {
    data object Loading : ResumeLoadState
    data class Error(val message: String) : ResumeLoadState
    data class Ready(
        val battingNames: Map<String, String>,
        val bowlingNames: Map<String, String>,
        val battingNamesList: List<String>
    ) : ResumeLoadState
}

/**
 * Picks back up scoring a match that was exited mid-innings (see ScoringScreen's exit-confirm
 * dialog — it always said the match's score is saved, but never actually offered a way back in
 * until now). This only supports resuming a match that's still ON its current innings — if the
 * first innings already finished and the organizer exited before picking the second innings'
 * opening lineup, that specific in-between moment isn't resumable yet (a much rarer case than
 * "exited mid-over"), and this screen says so plainly instead of pretending to handle it.
 */
@Composable
fun ResumeMatchScreen(matchId: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: MatchViewModel = viewModel(factory = MatchViewModel.Factory(app.matchRepository, app.liveBroadcastRepository))
    val liveInnings by viewModel.liveInnings.collectAsState()
    var loadState by remember { mutableStateOf<ResumeLoadState>(ResumeLoadState.Loading) }

    LaunchedEffect(matchId) {
        val match = app.matchRepository.getMatchOnce(matchId)
        val innings = app.matchRepository.observeInningsForMatch(matchId).first()
        val lastInnings = innings.lastOrNull()
        if (match == null || lastInnings == null) {
            loadState = ResumeLoadState.Error("এই ম্যাচ খুঁজে পাওয়া যায়নি")
            return@LaunchedEffect
        }
        val finalStates = app.matchRepository.getFinalInningsStates(matchId)
        val lastFinalState = finalStates.lastOrNull { it.innings.id == lastInnings.id }
        if (lastFinalState != null && lastFinalState.state.isInningsComplete) {
            loadState = ResumeLoadState.Error(
                "এই ম্যাচের বর্তমান ইনিংস ইতিমধ্যে শেষ। " +
                    if (innings.size < 2) "দ্বিতীয় ইনিংসের লাইনআপ বেছে নেওয়ার এই মুহূর্তটা এখনো resume করার সাপোর্ট করা হয়নি — Match Centre থেকে দেখা যাবে।"
                    else "পুরো ম্যাচই শেষ — Match Centre থেকে পূর্ণ স্কোরকার্ড দেখো।"
            )
            return@LaunchedEffect
        }

        val teamAPlayers = app.matchRepository.observePlayersForTeam(match.teamAId).first().map { it.name }
        val teamBPlayers = app.matchRepository.observePlayersForTeam(match.teamBId).first().map { it.name }
        val battingIsTeamA = lastInnings.battingTeamId == match.teamAId
        val battingNames = if (battingIsTeamA) teamAPlayers else teamBPlayers
        val bowlingNames = if (battingIsTeamA) teamBPlayers else teamAPlayers

        loadState = ResumeLoadState.Ready(
            battingNames = battingNames.associateBy { it },
            bowlingNames = bowlingNames.associateBy { it },
            battingNamesList = battingNames
        )
        viewModel.watchInnings(lastInnings.id)
    }

    when (val state = loadState) {
        is ResumeLoadState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ResumeLoadState.Error -> Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            TextButton(onClick = onFinished) { Text("← ফিরে যাও") }
            Spacer(Modifier.height(20.dp))
            Text(text = state.message, style = MaterialTheme.typography.bodyLarge)
        }
        is ResumeLoadState.Ready -> {
            val live = liveInnings
            ScoringScreen(
                live = live,
                battingPlayerNames = state.battingNames,
                bowlingPlayerNames = state.bowlingNames,
                onRuns = viewModel::recordRuns,
                onExtra = viewModel::recordExtra,
                onWicket = { type, dismissedId, explicitIncomingId, fielderId, selectedBowlerId ->
                    // See the matching comment in MatchFlow.kt for why explicitIncomingId is
                    // checked first, and isRetiredHurt is excluded from the auto-pick fallback.
                    val nextBatsman = explicitIncomingId ?: state.battingNamesList.firstOrNull { name ->
                        val fig = live?.state?.batsmanFigures?.get(name)
                        fig?.isOut != true && fig?.isRetiredHurt != true &&
                            name != live?.state?.strikerId && name != live?.state?.nonStrikerId
                    }
                    viewModel.recordWicket(type, dismissedId, nextBatsman, selectedBowlerId = selectedBowlerId, fielderId = fielderId)
                },
                onRetiredHurt = viewModel::recordRetiredHurt,
                onUndo = viewModel::undoLastBall,
                // If the innings finishes RIGHT HERE during a resumed session, just exit to home
                // rather than trying to chain into the second-innings-lineup flow — the same
                // scoping choice as the "not resumable yet" message above.
                onInningsComplete = onFinished,
                onGoLive = { viewModel.goLive {} },
                onStopLive = viewModel::stopLive,
                onExit = onFinished
            )
        }
    }
}
