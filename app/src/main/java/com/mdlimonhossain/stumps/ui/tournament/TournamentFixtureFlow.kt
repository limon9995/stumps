package com.mdlimonhossain.stumps.ui.tournament

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity
import com.mdlimonhossain.stumps.domain.repository.InningsSummary
import com.mdlimonhossain.stumps.domain.repository.LiveInnings
import com.mdlimonhossain.stumps.ui.match.OpeningLineupScreen
import com.mdlimonhossain.stumps.ui.match.QuickMatchInput
import com.mdlimonhossain.stumps.ui.match.MatchSummaryScreen
import com.mdlimonhossain.stumps.ui.match.MatchViewModel
import com.mdlimonhossain.stumps.ui.match.ScoringScreen
import com.mdlimonhossain.stumps.ui.match.battingIsTeamA
import kotlinx.coroutines.launch

/**
 * The "traffic controller" for scoring ONE tournament fixture — very similar in spirit to
 * MatchFlow.kt's standalone match flow (toss -> lineup -> score 1st innings -> lineup for 2nd
 * innings -> score 2nd innings -> summary), but this version pulls its two teams' rosters from
 * SAVED teams (via TournamentRepository) instead of letting the organizer freely type names in,
 * since a tournament team's roster should stay consistent match after match.
 */
private sealed interface FixtureFlowStep {
    data object Toss : FixtureFlowStep
    data class Lineup(val input: QuickMatchInput) : FixtureFlowStep
    data class Scoring(val matchId: String, val input: QuickMatchInput, val firstInnings: LiveInnings? = null) : FixtureFlowStep
    data class SecondLineup(val matchId: String, val input: QuickMatchInput, val firstInnings: LiveInnings) : FixtureFlowStep
    data class Summary(val matchId: String) : FixtureFlowStep
}

@Composable
fun TournamentFixtureFlow(
    fixture: TournamentFixtureEntity,
    tournament: TournamentEntity,
    tournamentTeams: List<TournamentTeamEntity>,
    organizerUid: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val matchViewModel: MatchViewModel = viewModel(factory = MatchViewModel.Factory(app.matchRepository, app.liveBroadcastRepository))
    val liveInnings by matchViewModel.liveInnings.collectAsState()
    // rememberCoroutineScope gives us a coroutine scope tied to this composable's lifetime,
    // for launching suspend function calls (like startFixtureMatch below) from inside a button's
    // onClick, which itself can't be a suspend function.
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf<FixtureFlowStep>(FixtureFlowStep.Toss) }
    // null while we're still loading each team's saved player roster from the database.
    var teamAPlayers by remember { mutableStateOf<List<String>?>(null) }
    var teamBPlayers by remember { mutableStateOf<List<String>?>(null) }

    val teamAName = tournamentTeams.firstOrNull { it.teamId == fixture.teamAId }?.teamName ?: "Team A"
    val teamBName = tournamentTeams.firstOrNull { it.teamId == fixture.teamBId }?.teamName ?: "Team B"

    // Load both teams' rosters once, as soon as this fixture is opened.
    LaunchedEffect(fixture.id) {
        teamAPlayers = app.teamRepository.getPlayersOnce(fixture.teamAId).map { it.name }
        teamBPlayers = app.teamRepository.getPlayersOnce(fixture.teamBId).map { it.name }
    }

    val aPlayers = teamAPlayers
    val bPlayers = teamBPlayers
    // Don't let the organizer start the toss screen until both rosters have actually loaded.
    if (step is FixtureFlowStep.Toss && (aPlayers == null || bPlayers == null)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    when (val s = step) {
        FixtureFlowStep.Toss -> TossScreen(
            teamAName = teamAName,
            teamBName = teamBName,
            onConfirm = { tossWinnerIsTeamA, tossDecisionIsBat ->
                step = FixtureFlowStep.Lineup(
                    QuickMatchInput(
                        teamAName = teamAName, teamAPlayers = aPlayers ?: emptyList(),
                        teamBName = teamBName, teamBPlayers = bPlayers ?: emptyList(),
                        oversLimit = tournament.oversPerMatch,
                        tossWinnerIsTeamA = tossWinnerIsTeamA, tossDecisionIsBat = tossDecisionIsBat
                    )
                )
            },
            onBack = onDone
        )

        is FixtureFlowStep.Lineup -> OpeningLineupScreen(
            input = s.input,
            onConfirm = { striker, nonStriker, bowler ->
                scope.launch {
                    // This is the key difference from a standalone match: startFixtureMatch
                    // creates the match using the saved teams' real rosters, and links it back
                    // to this fixture, instead of inventing brand-new ad-hoc teams.
                    val (matchId, inningsId) = app.tournamentRepository.startFixtureMatch(
                        fixture = fixture,
                        tournament = tournament,
                        organizerUid = organizerUid,
                        tossWinnerIsTeamA = s.input.tossWinnerIsTeamA,
                        tossDecisionIsBat = s.input.tossDecisionIsBat,
                        openingStrikerId = striker,
                        openingNonStrikerId = nonStriker,
                        openingBowlerId = bowler
                    )
                    matchViewModel.watchInnings(inningsId)
                    step = FixtureFlowStep.Scoring(matchId, s.input)
                }
            },
            // No match has been created yet, so it's safe to just go back to the toss step.
            onBack = { step = FixtureFlowStep.Toss }
        )

        is FixtureFlowStep.Scoring -> {
            val battingIsA = battingIsTeamA(s.input)
            val battingNames = if (battingIsA) s.input.teamAPlayers else s.input.teamBPlayers
            val bowlingNames = if (battingIsA) s.input.teamBPlayers else s.input.teamAPlayers
            // Two SEPARATE maps (not one combined one) — see the matching comment in MatchFlow.kt
            // for why: the wicket dialog's "who fielded it" step should only ever offer players
            // from the BOWLING side.
            val battingNameMap = battingNames.associateBy { it }
            val bowlingNameMap = bowlingNames.associateBy { it }
            val live = liveInnings

            // From here on, this is identical to how MatchFlow.kt drives ScoringScreen — once
            // a match+innings exists, tournament matches and standalone matches are scored
            // exactly the same way.
            ScoringScreen(
                live = live,
                battingPlayerNames = battingNameMap,
                bowlingPlayerNames = bowlingNameMap,
                onRuns = matchViewModel::recordRuns,
                onExtra = matchViewModel::recordExtra,
                onWicket = { type, dismissedId, _, fielderId ->
                    val nextBatsman = battingNames.firstOrNull { name ->
                        live?.state?.batsmanFigures?.get(name)?.isOut != true && name != live?.state?.strikerId && name != live?.state?.nonStrikerId
                    }
                    matchViewModel.recordWicket(type, dismissedId, nextBatsman, fielderId = fielderId)
                },
                onUndo = matchViewModel::undoLastBall,
                onInningsComplete = {
                    val finished = live ?: return@ScoringScreen
                    if (s.firstInnings == null) {
                        step = FixtureFlowStep.SecondLineup(s.matchId, s.input, finished)
                    } else {
                        step = FixtureFlowStep.Summary(s.matchId)
                    }
                },
                onGoLive = { matchViewModel.goLive {} },
                onStopLive = matchViewModel::stopLive,
                onExit = onDone
            )

            if (live == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }

        is FixtureFlowStep.SecondLineup -> {
            val chasingIsTeamA = !battingIsTeamA(s.input)
            val flippedInput = s.input.copy(tossWinnerIsTeamA = chasingIsTeamA, tossDecisionIsBat = true)
            OpeningLineupScreen(
                input = flippedInput,
                onConfirm = { striker, nonStriker, bowler ->
                    matchViewModel.startNextInnings(
                        matchId = s.matchId,
                        battingIsTeamA = chasingIsTeamA,
                        openingStrikerId = striker, openingNonStrikerId = nonStriker, openingBowlerId = bowler,
                        targetRuns = s.firstInnings.state.totalRuns + 1
                    ) { newInningsId ->
                        matchViewModel.watchInnings(newInningsId)
                        step = FixtureFlowStep.Scoring(s.matchId, s.input, s.firstInnings)
                    }
                },
                onBack = onDone
            )
        }

        is FixtureFlowStep.Summary -> {
            var innings by remember { mutableStateOf<List<InningsSummary>?>(null) }
            LaunchedEffect(s.matchId) { innings = app.matchRepository.getFinalInningsStates(s.matchId) }
            innings?.let { MatchSummaryScreen(innings = it, onDone = onDone) }
                ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

/** The toss screen for one fixture: who won, and did they choose to bat or bowl. */
@Composable
private fun TossScreen(
    teamAName: String,
    teamBName: String,
    onConfirm: (winnerIsTeamA: Boolean, decisionIsBat: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var winnerIsTeamA by remember { mutableStateOf(true) }
    var decisionIsBat by remember { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Text(text = "$teamAName vs $teamBName", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))
        Text(text = "টস", style = MaterialTheme.typography.titleLarge)
        Row {
            FilterChip(selected = winnerIsTeamA, onClick = { winnerIsTeamA = true }, label = { Text("$teamAName জিতেছে") })
            Spacer(Modifier.padding(4.dp))
            FilterChip(selected = !winnerIsTeamA, onClick = { winnerIsTeamA = false }, label = { Text("$teamBName জিতেছে") })
        }
        Spacer(Modifier.height(8.dp))
        Row {
            FilterChip(selected = decisionIsBat, onClick = { decisionIsBat = true }, label = { Text("ব্যাটিং") })
            Spacer(Modifier.padding(4.dp))
            FilterChip(selected = !decisionIsBat, onClick = { decisionIsBat = false }, label = { Text("বোলিং") })
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onConfirm(winnerIsTeamA, decisionIsBat) }, modifier = Modifier.fillMaxWidth()) {
            Text("এগিয়ে যাও")
        }
    }
}
