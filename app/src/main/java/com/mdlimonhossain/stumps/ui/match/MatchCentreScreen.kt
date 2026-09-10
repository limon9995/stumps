package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.domain.commentary.CommentaryEngine
import com.mdlimonhossain.stumps.domain.repository.InningsSummary
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.match.charts.JerseyBadge
import com.mdlimonhossain.stumps.ui.match.charts.OverRunsChart
import com.mdlimonhossain.stumps.ui.match.charts.WagonWheelChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The six tabs Match Centre offers. */
private enum class MatchCentreTab { SUMMARY, SCORECARD, STATS, BALLS, SUPER_STARS, INFO }

/**
 * The full "Match Centre" screen for one match — a tabbed view (Summary / Scorecard / Stats /
 * Balls / Info) that replaces the old single flat MatchSummaryScreen for anywhere a match's
 * FULL detail is needed (tapping a match from Home or from match history). It loads its own
 * data via MatchCentreViewModel, given just the matchId.
 */
@Composable
fun MatchCentreScreen(matchId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: MatchCentreViewModel = viewModel(
        key = matchId,
        factory = MatchCentreViewModel.Factory(matchId, app.matchRepository, app.tournamentRepository)
    )
    val uiState by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(MatchCentreTab.SUMMARY) }
    // Which innings (1st or 2nd) the Summary/Scorecard/Balls tabs are currently showing — shared
    // across those three tabs so switching tabs doesn't lose your place. Defaults to the LAST
    // innings (the most recent/current one), which is what you'd want to see first.
    var inningsIndex by remember(uiState.innings.size) { mutableStateOf((uiState.innings.size - 1).coerceAtLeast(0)) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Spacer(Modifier.width(4.dp))
            Text(text = "Match Centre", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // A horizontally-scrolling tab row — five tabs don't comfortably fit on a phone screen
        // at once, same as the reference app's own Match Centre.
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            MatchCentreTab.entries.forEach { entry ->
                AnimatedTabChip(label = entry.label(), selected = tab == entry) { tab = entry }
                Spacer(Modifier.width(8.dp))
            }
        }
        HorizontalDivider()

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        val innings = uiState.innings
        if (innings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "এখনো কোনো বল হয়নি")
            }
            return
        }
        val selected = innings.getOrNull(inningsIndex) ?: innings.last()

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // Only worth showing an innings switcher once there actually IS a second innings —
            // and not on tabs (Info, Super Stars) that already look across the WHOLE match.
            if (innings.size > 1 && tab != MatchCentreTab.INFO && tab != MatchCentreTab.SUPER_STARS) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    innings.forEachIndexed { index, summary ->
                        AnimatedTabChip(
                            label = summary.battingTeamName,
                            selected = inningsIndex == index,
                            modifier = Modifier.weight(1f)
                        ) { inningsIndex = index }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (tab) {
                MatchCentreTab.SUMMARY -> SummaryTab(selected)
                MatchCentreTab.SCORECARD -> ScorecardTab(selected)
                MatchCentreTab.STATS -> StatsTab(selected)
                MatchCentreTab.BALLS -> BallsTab(selected)
                MatchCentreTab.SUPER_STARS -> SuperStarsTab(uiState)
                MatchCentreTab.INFO -> InfoTab(uiState)
            }
        }
    }
}

private fun MatchCentreTab.label(): String = when (this) {
    MatchCentreTab.SUMMARY -> "Summary"
    MatchCentreTab.SCORECARD -> "Scorecard"
    MatchCentreTab.STATS -> "Stats"
    MatchCentreTab.BALLS -> "Balls"
    MatchCentreTab.SUPER_STARS -> "Super Stars"
    MatchCentreTab.INFO -> "Info"
}


/** The live-scoreboard-style tab: big score, extras/overs/run-rate line, current batsmen + bowler, recent overs. */
@Composable
private fun SummaryTab(summary: InningsSummary) {
    val s = summary.state
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(text = summary.battingTeamName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text = "${s.totalRuns}-${s.totalWickets}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Overs — ${s.oversDisplay} / ${s.oversLimit}    CRR — ${oneDecimalPlace(s.runRate)}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Text(text = "ব্যাটসম্যান", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        // Only the two batsmen still AT the crease when the innings ended/currently stands —
        // striker + non-striker — not the whole team's batting card (that's the Scorecard tab).
        listOfNotNull(s.strikerId, s.nonStrikerId).forEach { name ->
            val fig = s.batsmanFigures[name]
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = if (name == s.strikerId) "$name *" else name)
                Text(text = "${fig?.runs ?: 0} (${fig?.ballsFaced ?: 0})")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(text = "বোলার", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        s.currentBowlerId?.let { name ->
            val fig = s.bowlerFigures[name]
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = name)
                Text(text = "${fig?.overs ?: "0.0"}-${fig?.runsConceded ?: 0}-${fig?.wickets ?: 0}")
            }
        }

        if (s.runsPerOver.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(text = "Recent Overs", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OverRunsChart(runsPerOver = s.runsPerOver)
        }
    }
}

/** The full batting + bowling card for the selected innings — same figures the old MatchSummaryScreen showed, just in its own tab now. */
@Composable
private fun ScorecardTab(summary: InningsSummary) {
    val s = summary.state
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(text = "ব্যাটিং", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (s.batsmanFigures.isEmpty()) {
            Text(text = "No data to show", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.batsmanFigures.forEach { (name, fig) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = name, modifier = Modifier.weight(1f))
                Text(text = "${fig.runs} (${fig.ballsFaced}) ${if (fig.isOut) fig.dismissalType?.name ?: "out" else "not out"}", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(text = "বোলিং", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (s.bowlerFigures.isEmpty()) {
            Text(text = "No data to show", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.bowlerFigures.forEach { (name, fig) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = name, modifier = Modifier.weight(1f))
                Text(text = "${fig.overs}-${fig.runsConceded}-${fig.wickets}", fontSize = 12.sp)
            }
        }
    }
}

/** The Wagon Wheel + over-runs chart, with a dropdown to focus on one batsman's shots only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsTab(summary: InningsSummary) {
    val s = summary.state
    var expanded by remember { mutableStateOf(false) }
    var selectedBatsman by remember { mutableStateOf("সব খেলোয়াড়") }
    var viewMode by remember { mutableStateOf(com.mdlimonhossain.stumps.ui.match.charts.WagonWheelViewMode.RUNS) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(text = "Wagon Wheel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.mdlimonhossain.stumps.ui.common.AnimatedTabChip(
                label = "Runs",
                selected = viewMode == com.mdlimonhossain.stumps.ui.match.charts.WagonWheelViewMode.RUNS,
                modifier = Modifier.weight(1f)
            ) { viewMode = com.mdlimonhossain.stumps.ui.match.charts.WagonWheelViewMode.RUNS }
            com.mdlimonhossain.stumps.ui.common.AnimatedTabChip(
                label = "Percentage",
                selected = viewMode == com.mdlimonhossain.stumps.ui.match.charts.WagonWheelViewMode.PERCENTAGE,
                modifier = Modifier.weight(1f)
            ) { viewMode = com.mdlimonhossain.stumps.ui.match.charts.WagonWheelViewMode.PERCENTAGE }
        }
        Spacer(Modifier.height(10.dp))

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedBatsman,
                onValueChange = {},
                readOnly = true,
                label = { Text("ব্যাটসম্যান") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (listOf("সব খেলোয়াড়") + s.batsmanFigures.keys.toList()).forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { selectedBatsman = option; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        val shots = if (selectedBatsman == "সব খেলোয়াড়") s.shotEvents else s.shotEvents.filter { it.batsmanId == selectedBatsman }
        if (shots.isEmpty()) {
            Text(text = "এই খেলোয়াড়ের কোনো শট রেকর্ড করা হয়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            WagonWheelChart(shots = shots, modifier = Modifier.fillMaxWidth(), viewMode = viewMode)
        }

        if (s.runsPerOver.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(text = "ওভার-প্রতি রান", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OverRunsChart(runsPerOver = s.runsPerOver)
        }
    }
}

/** The ball-by-ball commentary feed for the selected innings, newest ball first (see CommentaryEngine). */
@Composable
private fun BallsTab(summary: InningsSummary) {
    if (summary.ballLog.isEmpty()) {
        EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "এখনো কোনো বল হয়নি")
        return
    }
    // remember(...) means this list is only rebuilt if the ball log actually changes, not on
    // every unrelated redraw — CommentaryEngine.generate() walks the whole ball log each time.
    val lines = remember(summary.ballLog) { CommentaryEngine.generate(summary.ballLog) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Text(text = line.overBallLabel, fontSize = 12.dp.value.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = line.text, fontSize = 13.sp)
                }
                Text(text = line.scoreAfter, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

/** Which slice of the match Super Stars is currently highlighting a standout performer for. */
private enum class SuperStarsFilter { ALL, TEAM_A, TEAM_B }

/**
 * A jersey-badge highlight of the match's best individual performance so far — the reference
 * app's "Super Stars" tab. "All Players" shows the single most impressive performance across
 * BOTH teams; filtering to one team narrows it to that team's own best performance. This is
 * computed fresh from the same batting/bowling figures every other tab already has — there's no
 * separate "man of the match" field stored anywhere, it's always just whoever currently has the
 * best figures.
 */
@Composable
private fun SuperStarsTab(uiState: MatchCentreUiState) {
    var filter by remember { mutableStateOf(SuperStarsFilter.ALL) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedTabChip(label = "All Players", selected = filter == SuperStarsFilter.ALL, modifier = Modifier.weight(1f)) { filter = SuperStarsFilter.ALL }
            AnimatedTabChip(label = uiState.teamAName, selected = filter == SuperStarsFilter.TEAM_A, modifier = Modifier.weight(1f)) { filter = SuperStarsFilter.TEAM_A }
            AnimatedTabChip(label = uiState.teamBName, selected = filter == SuperStarsFilter.TEAM_B, modifier = Modifier.weight(1f)) { filter = SuperStarsFilter.TEAM_B }
        }
        Spacer(Modifier.height(24.dp))

        val restrictToTeam = when (filter) {
            SuperStarsFilter.ALL -> null
            SuperStarsFilter.TEAM_A -> uiState.teamAName
            SuperStarsFilter.TEAM_B -> uiState.teamBName
        }
        val star = computeSuperStar(uiState, restrictToTeam)
        if (star == null) {
            EmptyState(icon = Icons.Filled.Star, title = "এখনো কোনো পারফরম্যান্স রেকর্ড হয়নি")
            return
        }
        val jerseyColor = if (star.team == uiState.teamAName) PitchGreenJerseyColor else BallRedJerseyColor
        val initials = star.playerName.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            JerseyBadge(color = jerseyColor, initials = initials, modifier = Modifier.width(180.dp))
            Spacer(Modifier.height(12.dp))
            Text(text = star.playerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = star.team, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(text = star.statLine, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val PitchGreenJerseyColor = Color(0xFF1B6E43)
private val BallRedJerseyColor = Color(0xFFB3122B)

private data class SuperStarPerformance(val playerName: String, val team: String, val statLine: String, val score: Int)

/**
 * Ranks every batting AND bowling figure across the whole match (both innings) and picks the
 * single most impressive one — a rough "runs, or ~20 points per wicket" scoring so a 3-wicket
 * haul roughly outranks a 50, matching cricket intuition without needing a precise formula.
 */
private fun computeSuperStar(uiState: MatchCentreUiState, restrictToTeam: String?): SuperStarPerformance? {
    val performances = mutableListOf<SuperStarPerformance>()
    uiState.innings.forEach { summary ->
        val battingTeam = summary.battingTeamName
        val bowlingTeam = if (battingTeam == uiState.teamAName) uiState.teamBName else uiState.teamAName
        summary.state.batsmanFigures.forEach { (name, fig) ->
            if (fig.runs > 0) {
                performances.add(SuperStarPerformance(name, battingTeam, "${fig.runs} রান (${fig.ballsFaced} বলে)", fig.runs))
            }
        }
        summary.state.bowlerFigures.forEach { (name, fig) ->
            if (fig.wickets > 0) {
                performances.add(SuperStarPerformance(name, bowlingTeam, "${fig.wickets} উইকেট (${fig.overs} ওভারে ${fig.runsConceded})", fig.wickets * 20))
            }
        }
    }
    val candidates = if (restrictToTeam != null) performances.filter { it.team == restrictToTeam } else performances
    return candidates.maxByOrNull { it.score }
}

/** Which of the Info tab's four sub-tabs is currently showing — matches the reference app's own INFO/SQUAD/COMPARE/HEAD TO HEAD row. */
private enum class InfoSubTab { INFO, SQUAD, COMPARE, HEAD_TO_HEAD }

/** Match facts, each team's squad, a player-vs-player stat comparison, and the two teams' head-to-head record — as four sub-tabs. */
@Composable
private fun InfoTab(uiState: MatchCentreUiState) {
    val match = uiState.match ?: return
    var subTab by remember { mutableStateOf(InfoSubTab.INFO) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                InfoSubTab.INFO to "Info",
                InfoSubTab.SQUAD to "Squad",
                InfoSubTab.COMPARE to "Compare",
                InfoSubTab.HEAD_TO_HEAD to "Head to Head"
            ).forEach { (entry, label) ->
                AnimatedTabChip(label = label, selected = subTab == entry) { subTab = entry }
            }
        }
        Spacer(Modifier.height(16.dp))

        when (subTab) {
            InfoSubTab.INFO -> InfoFactsSection(uiState, match)
            InfoSubTab.SQUAD -> SquadSection(uiState)
            InfoSubTab.COMPARE -> CompareSection(uiState, match)
            InfoSubTab.HEAD_TO_HEAD -> HeadToHeadSection(uiState)
        }
    }
}

@Composable
private fun InfoFactsSection(uiState: MatchCentreUiState, match: com.mdlimonhossain.stumps.data.local.db.match.MatchEntity) {
    val date = remember(match.createdAt) { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(match.createdAt)) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        InfoRow("Tournament", uiState.tournament?.name ?: "স্বাধীন ম্যাচ (কোনো টুর্নামেন্ট নেই)")
        uiState.tournament?.venue?.let { InfoRow("Venue", it) }
        InfoRow("Overs", match.oversLimit.toString())
        match.format?.let { InfoRow("Format", it) }
        InfoRow("Date & Time", date)
        InfoRow("Toss Decision", match.tossDecision ?: "-")
        InfoRow("Status", match.status)
        if (match.isLive) InfoRow("Live", "হ্যাঁ" + (match.shareCode?.let { " (কোড: $it)" } ?: ""))
    }
}

@Composable
private fun SquadSection(uiState: MatchCentreUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(text = uiState.teamAName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        uiState.teamAPlayers.forEach { Text(text = "• ${it.name}", modifier = Modifier.padding(vertical = 2.dp)) }

        Spacer(Modifier.height(20.dp))
        Text(text = uiState.teamBName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        uiState.teamBPlayers.forEach { Text(text = "• ${it.name}", modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@Composable
private fun HeadToHeadSection(uiState: MatchCentreUiState) {
    val h2h = uiState.headToHead
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (h2h == null || h2h.played == 0) {
            Text(
                text = "${uiState.teamAName} আর ${uiState.teamBName} এর আগে কখনো একে অপরের বিপক্ষে খেলেনি (এই organizer-এর রেকর্ড অনুযায়ী)",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            InfoRow("মোট খেলা হয়েছে", "${h2h.played}")
            InfoRow("${uiState.teamAName} জিতেছে", "${h2h.teamAWins}")
            InfoRow("${uiState.teamBName} জিতেছে", "${h2h.teamBWins}")
            val ties = h2h.played - h2h.teamAWins - h2h.teamBWins
            if (ties > 0) InfoRow("টাই হয়েছে", "$ties")
        }
    }
}

/**
 * Lets you pick one player from each squad and see their career numbers side by side — batting
 * and bowling both, pulled from the same StatsRepository the Player Profile screen itself uses,
 * scoped to whoever organizes this match (same "identity = display name" model used everywhere
 * else in this app — see MatchRepository's big comment for why).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareSection(uiState: MatchCentreUiState, match: com.mdlimonhossain.stumps.data.local.db.match.MatchEntity) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var playerA by remember(uiState.teamAPlayers) { mutableStateOf(uiState.teamAPlayers.firstOrNull()?.name ?: "") }
    var playerB by remember(uiState.teamBPlayers) { mutableStateOf(uiState.teamBPlayers.firstOrNull()?.name ?: "") }
    var statsA by remember { mutableStateOf(com.mdlimonhossain.stumps.domain.repository.CareerStats()) }
    var statsB by remember { mutableStateOf(com.mdlimonhossain.stumps.domain.repository.CareerStats()) }

    androidx.compose.runtime.LaunchedEffect(playerA) {
        if (playerA.isNotBlank()) statsA = app.statsRepository.careerStatsFor(match.createdByUid, playerA)
    }
    androidx.compose.runtime.LaunchedEffect(playerB) {
        if (playerB.isNotBlank()) statsB = app.statsRepository.careerStatsFor(match.createdByUid, playerB)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (uiState.teamAPlayers.isEmpty() || uiState.teamBPlayers.isEmpty()) {
            Text(text = "স্কোয়াড লোড হচ্ছে...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerDropdown(label = uiState.teamAName, options = uiState.teamAPlayers.map { it.name }, selected = playerA, modifier = Modifier.weight(1f)) { playerA = it }
            PlayerDropdown(label = uiState.teamBName, options = uiState.teamBPlayers.map { it.name }, selected = playerB, modifier = Modifier.weight(1f)) { playerB = it }
        }
        Spacer(Modifier.height(20.dp))

        CompareRow("Matches", statsA.matchesPlayed.toString(), statsB.matchesPlayed.toString())
        CompareRow("Runs", statsA.runs.toString(), statsB.runs.toString())
        CompareRow("Batting Avg", oneDecimalPlace(statsA.battingAverage), oneDecimalPlace(statsB.battingAverage))
        CompareRow("Strike Rate", oneDecimalPlace(statsA.strikeRate), oneDecimalPlace(statsB.strikeRate))
        CompareRow("Wickets", statsA.wickets.toString(), statsB.wickets.toString())
        CompareRow("Economy", oneDecimalPlace(statsA.economy), oneDecimalPlace(statsB.economy))
        CompareRow("Catches", statsA.catches.toString(), statsB.catches.toString())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDropdown(label: String, options: List<String>, selected: String, modifier: Modifier = Modifier, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, valueA: String, valueB: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = valueA, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(text = valueB, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider()
}

private fun oneDecimalPlace(value: Double): String = String.format(Locale.US, "%.1f", value)
