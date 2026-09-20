package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.domain.repository.InningsSummary
import com.mdlimonhossain.stumps.domain.repository.LiveInnings

/**
 * This is the "traffic controller" for a whole standalone match: it decides WHICH screen to
 * show at each point (set up teams -> pick opening lineup -> score the 1st innings -> pick 2nd
 * innings lineup -> score the 2nd innings -> show the final scorecard). Each of those steps is
 * one entry in the MatchFlowStep list below — we keep track of "which step are we on" in a
 * single `step` variable, and swap the whole screen every time it changes.
 */
private sealed interface MatchFlowStep {
    data object Setup : MatchFlowStep
    data class Lineup(val input: QuickMatchInput) : MatchFlowStep
    data class Scoring(val matchId: String, val inningsId: String, val input: QuickMatchInput, val firstInnings: LiveInnings? = null) : MatchFlowStep
    data class SecondLineup(val matchId: String, val input: QuickMatchInput, val firstInnings: LiveInnings) : MatchFlowStep
    data class Summary(val matchId: String) : MatchFlowStep
}

@Composable
fun MatchFlowScreen(currentUid: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: MatchViewModel = viewModel(factory = MatchViewModel.Factory(app.matchRepository, app.liveBroadcastRepository))
    // collectAsState turns the ViewModel's live StateFlow into something this @Composable
    // function automatically redraws itself for, every time the value changes.
    val liveInnings by viewModel.liveInnings.collectAsState()

    var step by remember { mutableStateOf<MatchFlowStep>(MatchFlowStep.Setup) }

    // "when (val s = step)" both checks WHICH step we're on and gives us `s` as that step's
    // specific type inside each branch (e.g. inside "is MatchFlowStep.Lineup ->", `s.input` is
    // available because Kotlin knows for certain `s` is a Lineup at that point).
    when (val s = step) {
        is MatchFlowStep.Setup -> MatchSetupScreen(
            uid = currentUid,
            onStartMatch = { input -> step = MatchFlowStep.Lineup(input) },
            onBack = onFinished
        )

        is MatchFlowStep.Lineup -> OpeningLineupScreen(
            input = s.input,
            onConfirm = { striker, nonStriker, bowler ->
                viewModel.createQuickMatch(
                    createdByUid = currentUid,
                    teamAName = s.input.teamAName, teamAPlayers = s.input.teamAPlayers,
                    teamBName = s.input.teamBName, teamBPlayers = s.input.teamBPlayers,
                    oversLimit = s.input.oversLimit,
                    tossWinnerIsTeamA = s.input.tossWinnerIsTeamA,
                    tossDecisionIsBat = s.input.tossDecisionIsBat,
                    openingStrikerId = striker, openingNonStrikerId = nonStriker, openingBowlerId = bowler,
                    format = s.input.format
                ) { matchId, inningsId ->
                    viewModel.watchInnings(inningsId)
                    step = MatchFlowStep.Scoring(matchId, inningsId, s.input)
                }
            },
            // No match has been created yet at this point, so "back" can safely just return to
            // the setup form — nothing's been saved that would be lost.
            onBack = { step = MatchFlowStep.Setup }
        )

        is MatchFlowStep.Scoring -> {
            val battingIsA = battingIsTeamA(s.input)
            val battingNames = (if (battingIsA) s.input.teamAPlayers else s.input.teamBPlayers)
            val bowlingNames = (if (battingIsA) s.input.teamBPlayers else s.input.teamAPlayers)
            // A quick lookup map where the id AND the name are the same string (see the note
            // in MatchRepository — this app uses player display names as their "id" for
            // ad-hoc quick matches). associateBy { it } turns a list into a map keyed by itself.
            // Kept as TWO SEPARATE maps (not one combined one) so the wicket dialog's "who
            // fielded it" step only ever offers players from the BOWLING side, not the batting side.
            val battingNameMap = battingNames.associateBy { it }
            val bowlingNameMap = bowlingNames.associateBy { it }
            val live = liveInnings

            ScoringScreen(
                live = live,
                battingPlayerNames = battingNameMap,
                bowlingPlayerNames = bowlingNameMap,
                onRuns = viewModel::recordRuns,
                onExtra = viewModel::recordExtra,
                onWicket = { type, dismissedId, _, fielderId ->
                    // Automatically pick the next incoming batsman: the first player in the
                    // batting lineup who isn't already out and isn't already at the crease.
                    val nextBatsman = battingNames.firstOrNull { name ->
                        live?.state?.batsmanFigures?.get(name)?.isOut != true && name != live?.state?.strikerId && name != live?.state?.nonStrikerId
                    }
                    viewModel.recordWicket(type, dismissedId, nextBatsman, fielderId = fielderId)
                },
                onUndo = viewModel::undoLastBall,
                onInningsComplete = {
                    val finished = live ?: return@ScoringScreen
                    // If this was the FIRST innings finishing, move on to picking the 2nd
                    // innings' lineup. If it was the SECOND innings finishing, the whole match
                    // is over — jump straight to the final scorecard.
                    if (s.firstInnings == null) {
                        step = MatchFlowStep.SecondLineup(s.matchId, s.input, finished)
                    } else {
                        step = MatchFlowStep.Summary(s.matchId)
                    }
                },
                onGoLive = { viewModel.goLive {} },
                onStopLive = viewModel::stopLive,
                // Safe to just leave from here — every ball is already saved as it's recorded
                // (see ScoringEngine.kt's big comment on why), so nothing is lost by exiting.
                onExit = onFinished
            )
        }

        is MatchFlowStep.SecondLineup -> {
            // The team that bowled first now bats (they're the ones "chasing" the target).
            val chasingIsTeamA = !battingIsTeamA(s.input)
            // Reuse OpeningLineupScreen by pretending the chasing team "won the toss and chose
            // to bat" — a little trick so we don't need a second, near-identical lineup screen.
            val flippedInput = s.input.copy(tossWinnerIsTeamA = chasingIsTeamA, tossDecisionIsBat = true)

            OpeningLineupScreen(
                input = flippedInput,
                onConfirm = { striker, nonStriker, bowler ->
                    viewModel.startNextInnings(
                        matchId = s.matchId,
                        battingIsTeamA = chasingIsTeamA,
                        openingStrikerId = striker, openingNonStrikerId = nonStriker, openingBowlerId = bowler,
                        targetRuns = s.firstInnings.state.totalRuns + 1
                    ) { newInningsId ->
                        viewModel.watchInnings(newInningsId)
                        step = MatchFlowStep.Scoring(s.matchId, newInningsId, s.input, s.firstInnings)
                    }
                },
                // The first innings is already finished and saved at this point — there's no
                // meaningful "back" to go to, so this just exits to home like the live scoring
                // screen's own exit does.
                onBack = onFinished
            )
        }

        is MatchFlowStep.Summary -> {
            var innings by remember { mutableStateOf<List<InningsSummary>?>(null) }
            // LaunchedEffect runs its block of code once, the first time this composable
            // appears with this particular matchId (and again if matchId ever changes) — the
            // right place to trigger a one-off suspend function call like loading the final scorecard.
            LaunchedEffect(s.matchId) { innings = app.matchRepository.getFinalInningsStates(s.matchId) }
            innings?.let { MatchSummaryScreen(innings = it, onDone = onFinished) }
                ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }

    // Show a loading spinner ON TOP of the scoring screen while we're still waiting for the
    // very first bit of live innings data to arrive.
    if (step is MatchFlowStep.Scoring && liveInnings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
