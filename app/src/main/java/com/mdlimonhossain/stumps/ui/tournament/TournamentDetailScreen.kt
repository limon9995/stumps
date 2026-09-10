package com.mdlimonhossain.stumps.ui.tournament

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.export.ShareUtils
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.GradientHeroCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import com.mdlimonhossain.stumps.ui.theme.PitchGreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DetailTab { HOME, TEAMS, MATCHES, POINTS, STATISTICS }

/**
 * The main tournament screen — five tabs (Home overview, Teams grid, Matches/fixtures, Points
 * table, Statistics/leaderboards), matching the reference app's Tournament page layout. All
 * five tabs share the SAME data this screen already loads via TournamentDetailViewModel — Home
 * and Teams are just new ways of looking at `teams`/`leaderboards` that already existed for the
 * other tabs, not new data sources.
 */
@Composable
fun TournamentDetailScreen(tournamentId: String, organizerUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: TournamentDetailViewModel = viewModel(factory = TournamentDetailViewModel.Factory(app.tournamentRepository, tournamentId))

    val tournament by viewModel.tournament.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val fixtures by viewModel.fixtures.collectAsState()
    val standings by viewModel.standings.collectAsState()
    val leaderboards by viewModel.leaderboards.collectAsState()

    var tab by remember { mutableStateOf(DetailTab.HOME) }
    // Which fixture (if any) is currently being scored/viewed — null means show the tab view instead.
    var activeFixture by remember { mutableStateOf<TournamentFixtureEntity?>(null) }

    // Recalculate the points table / leaderboard whenever the user switches TO that tab, or
    // whenever the fixture list changes (e.g. a match just finished) while already on that tab.
    // HOME also needs leaderboards loaded, since its "Top Players" preview reuses that same data.
    LaunchedEffect(tab, fixtures) {
        if (tab == DetailTab.POINTS) viewModel.refreshStandings(tournamentId)
        if (tab == DetailTab.STATISTICS || tab == DetailTab.HOME) viewModel.refreshLeaderboards(tournamentId)
    }

    val t = tournament
    if (t == null) {
        Text(text = "লোড হচ্ছে...", modifier = Modifier.padding(24.dp))
        return
    }

    // If a fixture has been tapped, show its scoring/summary flow FULL SCREEN instead of the
    // tabs — we return early here so nothing below this gets drawn at the same time.
    if (activeFixture != null) {
        TournamentFixtureFlow(
            fixture = activeFixture!!,
            tournament = t,
            tournamentTeams = teams,
            organizerUid = organizerUid,
            onDone = {
                activeFixture = null
                // Refresh the table and leaderboard now that this fixture's result is in.
                viewModel.refreshStandings(tournamentId)
                viewModel.refreshLeaderboards(tournamentId)
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        }
        Text(text = t.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            listOf(
                DetailTab.HOME to "Home",
                DetailTab.TEAMS to "Teams",
                DetailTab.MATCHES to "Matches",
                DetailTab.POINTS to "Points",
                DetailTab.STATISTICS to "Statistics"
            ).forEach { (entry, label) ->
                AnimatedTabChip(label = label, selected = tab == entry) { tab = entry }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        when (tab) {
            DetailTab.HOME -> HomeTab(
                tournament = t,
                teams = teams,
                leaderboards = leaderboards,
                onSeeTeams = { tab = DetailTab.TEAMS },
                viewerUid = organizerUid
            )
            DetailTab.TEAMS -> TeamsTab(teams = teams)
            DetailTab.MATCHES -> MatchesTab(fixtures = fixtures, teams = teams, onOpenFixture = { activeFixture = it })
            DetailTab.POINTS -> PointsTab(standings = standings)
            DetailTab.STATISTICS -> StatisticsTab(leaderboards = leaderboards)
        }
    }
}


/** The tournament's own "front page" — quick facts, a Follow button, a teams preview, and the current top run-scorer/wicket-taker. */
@Composable
private fun HomeTab(
    tournament: com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity,
    teams: List<com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity>,
    leaderboards: com.mdlimonhossain.stumps.domain.repository.TournamentLeaderboards,
    onSeeTeams: () -> Unit,
    viewerUid: String
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.mdlimonhossain.stumps.StumpsApplication
    val isFollowing by app.followRepository.observeIsFollowing(
        viewerUid, com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType.TOURNAMENT, tournament.id
    ).collectAsState(initial = false)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        // No banner-image upload feature exists yet — a gradient header (the same shared "hero"
        // look used on Home's profile summary card) stands in for the reference app's tournament
        // banner artwork.
        GradientHeroCard(modifier = Modifier.height(120.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = tournament.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                tournament.venue?.let { Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                Text(text = "Tour ID : ${tournament.id.take(8)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            TextButton(onClick = {
                scope.launch {
                    app.followRepository.toggleFollow(
                        uid = viewerUid,
                        type = com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType.TOURNAMENT,
                        targetId = tournament.id,
                        targetName = tournament.name,
                        isCurrentlyFollowing = isFollowing
                    )
                }
            }) { Text(if (isFollowing) "Following ✓" else "Follow") }
            TextButton(onClick = {
                ShareUtils.shareText(context, "${tournament.name} টুর্নামেন্ট দেখো Stumps অ্যাপে!")
            }) { Text("Share") }
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Teams", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(text = "সব দেখো »", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onSeeTeams))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            teams.forEach { team ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                    TeamInitialsCircle(name = team.teamName)
                    Spacer(Modifier.height(4.dp))
                    Text(text = team.teamName, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.width(12.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(text = "Top Players", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TopPlayerStat(
                modifier = Modifier.weight(1f),
                label = "Most Runs",
                value = leaderboards.orangeCap.firstOrNull()?.value?.toString() ?: "0",
                playerName = leaderboards.orangeCap.firstOrNull()?.playerName ?: "-"
            )
            TopPlayerStat(
                modifier = Modifier.weight(1f),
                label = "Most Wickets",
                value = leaderboards.purpleCap.firstOrNull()?.value?.toString() ?: "0",
                playerName = leaderboards.purpleCap.firstOrNull()?.playerName ?: "-"
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TopPlayerStat(modifier: Modifier, label: String, value: String, playerName: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = playerName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A grid of every team taking part — just names + initials circles, since no team-logo-upload feature exists yet. */
@Composable
private fun TeamsTab(teams: List<com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity>) {
    if (teams.isEmpty()) {
        EmptyState(icon = Icons.Filled.AccountBox, title = "কোনো দল যোগ করা হয়নি")
        return
    }
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        items(teams, key = { it.id }) { team ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamInitialsCircle(name = team.teamName)
                Spacer(Modifier.width(10.dp))
                Text(text = team.teamName, fontWeight = FontWeight.Medium, maxLines = 2)
            }
        }
    }
}

@Composable
private fun TeamInitialsCircle(name: String) {
    Box(
        modifier = Modifier.size(56.dp).clip(CircleShape).background(PitchGreen),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

/** The fixture schedule — tap one to score/view it. Same content the old single "সূচি" tab had. */
@Composable
private fun MatchesTab(
    fixtures: List<TournamentFixtureEntity>,
    teams: List<com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity>,
    onOpenFixture: (TournamentFixtureEntity) -> Unit
) {
    if (fixtures.isEmpty()) {
        EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "এখনো কোনো ফিক্সচার তৈরি হয়নি")
        return
    }
    LazyColumn(modifier = Modifier.padding(horizontal = 20.dp)) {
        itemsIndexed(fixtures, key = { _, f -> f.id }) { index, fixture ->
            val teamAName = teams.firstOrNull { it.teamId == fixture.teamAId }?.teamName ?: "?"
            val teamBName = teams.firstOrNull { it.teamId == fixture.teamBId }?.teamName ?: "?"
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .staggeredEntrance(index),
                onClick = { onOpenFixture(fixture) }
            ) {
                Text(text = "$teamAName vs $teamBName", style = MaterialTheme.typography.titleLarge)
                // matchId is null until someone actually taps this fixture to start scoring it.
                Text(
                    text = if (fixture.matchId == null) "শুরু হয়নি — স্কোর করতে ট্যাপ করো" else "সম্পন্ন / চলমান — দেখতে ট্যাপ করো",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** The points table — same content the old "পয়েন্ট টেবিল" tab had. */
@Composable
internal fun PointsTab(standings: List<com.mdlimonhossain.stumps.domain.tournament.TeamStanding>) {
    // The points table can have quite a few columns, so it's wrapped in horizontalScroll to let
    // it scroll sideways on a narrow phone screen instead of squashing everything.
    Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            HeaderCell("দল", 140)
            HeaderCell("খে", 40)
            HeaderCell("জ", 40)
            HeaderCell("হা", 40)
            HeaderCell("ড্র", 40)
            HeaderCell("পয়েন্ট", 60)
            HeaderCell("NRR", 70)
        }
        // standings is already sorted correctly (points, then NRR) by TournamentEngine — this
        // screen just draws them in the order it's given, top to bottom. The team currently at
        // the TOP of the table gets a soft gold highlight (the theme's "trophy" tertiary
        // colour) so the current table leader visually stands out at a glance.
        standings.forEachIndexed { index, s ->
            val isLeader = index == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isLeader) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                        MaterialTheme.shapes.medium
                    )
                    .padding(vertical = 4.dp)
            ) {
                Cell(s.teamName, 140, bold = isLeader)
                Cell("${s.played}", 40, bold = isLeader)
                Cell("${s.won}", 40, bold = isLeader)
                Cell("${s.lost}", 40, bold = isLeader)
                Cell("${s.tied}", 40, bold = isLeader)
                Cell("${s.points}", 60, bold = isLeader)
                Cell(formatNrr(s.netRunRate), 70, bold = isLeader)
            }
        }
    }
}

/** Orange Cap / Purple Cap — same content the old "লিডারবোর্ড" tab had. */
@Composable
internal fun StatisticsTab(leaderboards: com.mdlimonhossain.stumps.domain.repository.TournamentLeaderboards) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(text = "🟠 Orange Cap — সর্বোচ্চ রান", style = MaterialTheme.typography.titleLarge)
        leaderboards.orangeCap.forEachIndexed { index, entry ->
            // The single current leader's number is picked out in the theme's gold "trophy"
            // colour, so the very top of each leaderboard reads as a little "award" moment.
            val isLeader = index == 0
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(entry.playerName, modifier = Modifier.padding(end = 12.dp), fontWeight = if (isLeader) FontWeight.Bold else null)
                Text("${entry.value}", color = if (isLeader) MaterialTheme.colorScheme.tertiary else Color.Unspecified, fontWeight = if (isLeader) FontWeight.Bold else null)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text = "🟣 Purple Cap — সর্বোচ্চ উইকেট", style = MaterialTheme.typography.titleLarge)
        leaderboards.purpleCap.forEachIndexed { index, entry ->
            val isLeader = index == 0
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(entry.playerName, modifier = Modifier.padding(end = 12.dp), fontWeight = if (isLeader) FontWeight.Bold else null)
                Text("${entry.value}", color = if (isLeader) MaterialTheme.colorScheme.tertiary else Color.Unspecified, fontWeight = if (isLeader) FontWeight.Bold else null)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One bold, small-text header cell in the points table, at a fixed width so columns line up. */
@Composable
internal fun HeaderCell(text: String, width: Int) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(width.dp))
}

/** One normal data cell in the points table — same fixed-width idea as HeaderCell. `bold` is
 * used to make the current table leader's whole row stand out a little more than the rest. */
@Composable
internal fun Cell(text: String, width: Int, bold: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else null,
        modifier = Modifier.width(width.dp)
    )
}

/** Formats a Net Run Rate nicely: rounded to 3 decimal places, with an explicit "+" for positive values. */
internal fun formatNrr(value: Double): String {
    val rounded = (value * 1000).roundToInt() / 1000.0
    return if (rounded >= 0) "+$rounded" else "$rounded"
}
