package com.mdlimonhossain.stumps.ui.tournament

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity
import com.mdlimonhossain.stumps.domain.repository.TournamentLeaderboards
import com.mdlimonhossain.stumps.domain.tournament.TeamStanding
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.GradientHeroCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import com.mdlimonhossain.stumps.ui.match.CloudMatchScorecardScreen
import kotlinx.coroutines.launch

private enum class PreviewTab { HOME, MATCHES, POINTS, STATISTICS }

/**
 * A READ-ONLY view of a tournament this device does NOT organize — reached by tapping a
 * tournament in Search results. Every tournament match now auto-mirrors its ball-by-ball data to
 * Firestore as it's scored (see TournamentRepository.startFixtureMatch), the same mechanism the
 * "live broadcast" feature already used — so unlike the very first version of this screen, real
 * fixtures/scores/points/leaderboards ARE available here now, just sourced from the cloud
 * (TournamentRepository.getCloudFixtures/computeCloudStandings/computeCloudLeaderboards) instead
 * of local Room, since this device never ran these matches itself.
 *
 * This intentionally reuses PointsTab/StatisticsTab from TournamentDetailScreen.kt (same
 * package, same table layout) rather than duplicating that drawing code — only the DATA SOURCE
 * differs between "my own tournament" and "someone else's tournament I'm browsing".
 */
@Composable
fun TournamentPreviewScreen(tournamentId: String, viewerUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var tournament by remember { mutableStateOf<TournamentEntity?>(null) }
    var teams by remember { mutableStateOf<List<TournamentTeamEntity>>(emptyList()) }
    var fixtures by remember { mutableStateOf<List<TournamentFixtureEntity>>(emptyList()) }
    var standings by remember { mutableStateOf<List<TeamStanding>>(emptyList()) }
    var leaderboards by remember { mutableStateOf(TournamentLeaderboards(emptyList(), emptyList())) }
    var isLoading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(PreviewTab.HOME) }
    // Which match (if any) the viewer tapped in the Matches tab — null means show the tabs instead.
    var viewingMatchId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tournamentId) {
        tournament = app.tournamentRepository.getTournamentOnce(tournamentId) ?: app.tournamentRepository.getCloudTournament(tournamentId)
        teams = app.tournamentRepository.getCloudTeams(tournamentId)
        fixtures = app.tournamentRepository.getCloudFixtures(tournamentId)
        isLoading = false
    }
    // Points/Statistics replay every played match's ball log, so — same as TournamentDetailScreen
    // — only recompute them when the viewer actually switches to that tab, not on every redraw.
    LaunchedEffect(tab, fixtures) {
        if (tab == PreviewTab.POINTS) standings = app.tournamentRepository.computeCloudStandings(tournamentId)
        if (tab == PreviewTab.STATISTICS) leaderboards = app.tournamentRepository.computeCloudLeaderboards(tournamentId)
    }

    if (viewingMatchId != null) {
        CloudMatchScorecardScreen(matchId = viewingMatchId!!, onBack = { viewingMatchId = null })
        return
    }

    val isFollowing by app.followRepository.observeIsFollowing(viewerUid, FollowTargetType.TOURNAMENT, tournamentId).collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        }

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            tournament == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "এই টুর্নামেন্ট খুঁজে পাওয়া যায়নি")
            }
            else -> {
                val t = tournament!!
                Text(text = t.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    listOf(
                        PreviewTab.HOME to "Home",
                        PreviewTab.MATCHES to "Matches",
                        PreviewTab.POINTS to "Points",
                        PreviewTab.STATISTICS to "Statistics"
                    ).forEach { (entry, label) ->
                        AnimatedTabChip(label = label, selected = tab == entry) { tab = entry }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))

                when (tab) {
                    PreviewTab.HOME -> PreviewHomeTab(
                        tournament = t,
                        teams = teams,
                        isFollowing = isFollowing,
                        onToggleFollow = {
                            scope.launch { app.followRepository.toggleFollow(viewerUid, FollowTargetType.TOURNAMENT, t.id, t.name, isFollowing) }
                        }
                    )
                    PreviewTab.MATCHES -> PreviewMatchesTab(fixtures = fixtures, teams = teams, onOpenMatch = { viewingMatchId = it })
                    PreviewTab.POINTS -> PointsTab(standings = standings)
                    PreviewTab.STATISTICS -> StatisticsTab(leaderboards = leaderboards)
                }
            }
        }
    }
}

@Composable
private fun PreviewHomeTab(tournament: TournamentEntity, teams: List<TournamentTeamEntity>, isFollowing: Boolean, onToggleFollow: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        GradientHeroCard(modifier = Modifier.height(100.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = tournament.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        tournament.venue?.let { Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(text = "${tournament.oversPerMatch} ওভার প্রতি ম্যাচ • ${teams.size} দল", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onToggleFollow) { Text(if (isFollowing) "Following ✓" else "Follow") }
    }
}

@Composable
private fun PreviewMatchesTab(fixtures: List<TournamentFixtureEntity>, teams: List<TournamentTeamEntity>, onOpenMatch: (String) -> Unit) {
    if (fixtures.isEmpty()) {
        EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "এখনো কোনো ফিক্সচার নেই")
        return
    }
    LazyColumn(modifier = Modifier.padding(horizontal = 20.dp)) {
        itemsIndexed(fixtures, key = { _, f -> f.id }) { index, fixture ->
            val teamAName = teams.firstOrNull { it.teamId == fixture.teamAId }?.teamName ?: "?"
            val teamBName = teams.firstOrNull { it.teamId == fixture.teamBId }?.teamName ?: "?"
            val matchId = fixture.matchId
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .staggeredEntrance(index),
                onClick = if (matchId != null) ({ onOpenMatch(matchId) }) else null
            ) {
                Text(text = "$teamAName vs $teamBName", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (matchId == null) "শুরু হয়নি" else "স্কোরকার্ড দেখতে ট্যাপ করো",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
