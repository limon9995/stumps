package com.mdlimonhossain.stumps.ui.search

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.ListItemCard

private enum class SearchCategory { CLUB, TOURNAMENT, MATCH, TEAM, PLAYER }

/**
 * Club and Tournament search here reads from the SHARED CLOUD directory (see
 * ClubRepository.searchCloudByName / TournamentRepository.searchCloudByName) — this is what
 * lets you actually find clubs/tournaments other users registered, not just your own. Team,
 * Match, and Player search all stay LOCAL-only — a saved team's roster, your own matches, and
 * your own players are personal admin data, not something meant to be publicly discoverable by
 * other users (unlike a club/tournament, which exists specifically to be found and followed).
 */
@Composable
fun SearchScreen(
    uid: String,
    onBack: () -> Unit,
    onOpenClub: (String) -> Unit,
    onOpenTournament: (String) -> Unit,
    onOpenMatch: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var category by remember { mutableStateOf(SearchCategory.CLUB) }
    var query by remember { mutableStateOf("") }

    var clubResults by remember { mutableStateOf<List<com.mdlimonhossain.stumps.data.local.db.club.ClubEntity>>(emptyList()) }
    var tournamentResults by remember { mutableStateOf<List<com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity>>(emptyList()) }
    var teamResults by remember { mutableStateOf<List<com.mdlimonhossain.stumps.data.local.db.match.TeamEntity>>(emptyList()) }
    var matchResults by remember { mutableStateOf<List<com.mdlimonhossain.stumps.data.local.db.match.MatchEntity>>(emptyList()) }
    var playerResults by remember { mutableStateOf<List<com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity>>(emptyList()) }

    // Re-run the search whenever the query text OR the selected category changes. A blank query
    // just clears the results instead of listing everything.
    LaunchedEffect(query, category) {
        if (query.isBlank()) {
            clubResults = emptyList(); tournamentResults = emptyList(); teamResults = emptyList()
            matchResults = emptyList(); playerResults = emptyList()
            return@LaunchedEffect
        }
        when (category) {
            SearchCategory.CLUB -> clubResults = app.clubRepository.searchCloudByName(query)
            SearchCategory.TOURNAMENT -> tournamentResults = app.tournamentRepository.searchCloudByName(query)
            SearchCategory.TEAM -> teamResults = app.teamRepository.searchByName(uid, query)
            SearchCategory.MATCH -> matchResults = app.matchRepository.searchByTeamName(uid, query)
            SearchCategory.PLAYER -> playerResults = app.teamRepository.searchPlayersByName(uid, query)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("খুঁজো") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf(
                SearchCategory.CLUB to "Club",
                SearchCategory.TOURNAMENT to "Tournament",
                SearchCategory.MATCH to "Match",
                SearchCategory.TEAM to "Team",
                SearchCategory.PLAYER to "Player"
            ).forEach { (entry, label) ->
                AnimatedTabChip(label = label, selected = category == entry) { category = entry }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))

        when (category) {
            SearchCategory.CLUB -> clubResults.forEach { club ->
                ResultRow(club.name, "${club.city} • ${club.establishedYear}") { onOpenClub(club.id) }
            }
            SearchCategory.TOURNAMENT -> tournamentResults.forEach { t ->
                ResultRow(t.name, t.venue ?: "") { onOpenTournament(t.id) }
            }
            SearchCategory.TEAM -> teamResults.forEach { ResultRow(it.name, null, onClick = null) }
            SearchCategory.MATCH -> matchResults.forEach { match -> MatchResultRow(match = match, onClick = { onOpenMatch(match.id) }) }
            SearchCategory.PLAYER -> playerResults.forEach { player -> ResultRow(player.name, player.role, onClick = null) }
        }
        if (query.isNotBlank() &&
            when (category) {
                SearchCategory.CLUB -> clubResults.isEmpty()
                SearchCategory.TOURNAMENT -> tournamentResults.isEmpty()
                SearchCategory.TEAM -> teamResults.isEmpty()
                SearchCategory.MATCH -> matchResults.isEmpty()
                SearchCategory.PLAYER -> playerResults.isEmpty()
            }
        ) {
            EmptyState(icon = Icons.Filled.Search, title = "কোনো ফলাফল পাওয়া যায়নি")
        }
    }
}

/** A match search result — loads its two team names itself, since MatchEntity only stores team IDs. */
@Composable
private fun MatchResultRow(match: com.mdlimonhossain.stumps.data.local.db.match.MatchEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var title by remember(match.id) { mutableStateOf("...") }
    LaunchedEffect(match.id) {
        val names = app.matchRepository.getTeamNames(match.id)
        title = if (names != null) "${names.first} vs ${names.second}" else "?"
    }
    ResultRow(title, "${match.oversLimit} ওভার", onClick)
}

@Composable
private fun ResultRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
    ListItemCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        title = title,
        subtitle = subtitle?.takeIf { it.isNotBlank() },
        onClick = onClick
    )
}
