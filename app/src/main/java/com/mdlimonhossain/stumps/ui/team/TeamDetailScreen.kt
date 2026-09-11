package com.mdlimonhossain.stumps.ui.team

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.mdlimonhossain.stumps.data.export.ShareUtils
import com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.ListItemCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import com.mdlimonhossain.stumps.ui.theme.PitchGreen
import kotlinx.coroutines.launch

/** Which of the six tabs on a team's detail page is currently showing. */
private enum class TeamTab { OVERVIEW, PLAYERS, MATCHES, TOURNAMENTS, STATISTICS, COMPARE }

/**
 * The full detail page for ONE saved team — reached by tapping a team card on "আমার টিম" or
 * Search. Matches, Tournaments, and the deeper Statistics/Compare numbers all need this app to
 * be able to look up "every match/tournament a given TEAM has played in", which nothing in the
 * data layer does yet (matches only remember two team IDS, not an indexed-by-team lookup) — so
 * those tabs are honest, empty placeholders for now rather than faked charts. Team Overview and
 * Players ARE fully real: this app already tracks a team's own name/location and its saved
 * roster of players.
 */
@Composable
fun TeamDetailScreen(teamId: String, viewerUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val scope = rememberCoroutineScope()

    var team by remember { mutableStateOf<TeamEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(teamId) {
        team = app.teamRepository.getTeamOnce(teamId)
        isLoading = false
    }
    val players by app.teamRepository.observePlayersForTeam(teamId).collectAsState(initial = emptyList())

    var showSettings by remember { mutableStateOf(false) }
    var showAddPlayer by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(TeamTab.OVERVIEW) }

    val t = team
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (t == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Filled.AccountBox, title = "এই টিম খুঁজে পাওয়া যায়নি")
        }
        return
    }

    // Settings takes over the WHOLE screen while open, same pattern as this app's other
    // "create/edit" forms (e.g. ClubScreen's register form) rather than a popup dialog — makes
    // sense here since it has several fields, not just a quick one-tap action.
    if (showSettings) {
        TeamSettingsScreen(
            team = t,
            onSave = { newName, newLocation ->
                scope.launch {
                    app.teamRepository.updateTeamDetails(t, newName, newLocation)
                    team = t.copy(name = newName, location = newLocation)
                    showSettings = false
                }
            },
            onDelete = {
                scope.launch {
                    app.teamRepository.deleteTeam(t)
                    showSettings = false
                    onBack()
                }
            },
            onClose = { showSettings = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "ফিরে যাও",
                modifier = Modifier.clickable(onClick = onBack)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedTabChip("Team Overview", tab == TeamTab.OVERVIEW) { tab = TeamTab.OVERVIEW }
            AnimatedTabChip("Players", tab == TeamTab.PLAYERS) { tab = TeamTab.PLAYERS }
            AnimatedTabChip("Matches", tab == TeamTab.MATCHES) { tab = TeamTab.MATCHES }
            AnimatedTabChip("Tournaments", tab == TeamTab.TOURNAMENTS) { tab = TeamTab.TOURNAMENTS }
            AnimatedTabChip("Statistics", tab == TeamTab.STATISTICS) { tab = TeamTab.STATISTICS }
            AnimatedTabChip("Compare", tab == TeamTab.COMPARE) { tab = TeamTab.COMPARE }
        }
        Spacer(Modifier.height(20.dp))

        when (tab) {
            TeamTab.OVERVIEW -> TeamOverviewTab(team = t, onOpenSettings = { showSettings = true })
            TeamTab.PLAYERS -> TeamPlayersTab(players = players, onAddPlayer = { showAddPlayer = true })
            TeamTab.MATCHES -> EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = "No Matches",
                subtitle = "This team has not played in any matches."
            )
            TeamTab.TOURNAMENTS -> EmptyState(
                icon = Icons.Filled.Star,
                title = "No Tournaments",
                subtitle = "This team has not participated in any tournaments."
            )
            TeamTab.STATISTICS -> TeamStatisticsTab()
            TeamTab.COMPARE -> TeamCompareTab(team = t)
        }
    }

    if (showAddPlayer) {
        AddPlayerDialog(
            onDismiss = { showAddPlayer = false },
            onAdd = { name ->
                scope.launch { app.teamRepository.addPlayer(teamId, name, PlayerRole.BATSMAN) }
                showAddPlayer = false
            }
        )
    }
}

/** The header (avatar/name/location/id/share/settings) plus a single honest "not enough match
 * data yet" block standing in for the reference app's Win/Loss ratio, Top Performers, Scheduled
 * Matches, and recent-runs/wickets charts — all four need a "matches this team has played"
 * lookup this app doesn't have yet, so one clear empty state is more honest than four fake ones. */
@Composable
private fun TeamOverviewTab(team: TeamEntity, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(PitchGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = team.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = team.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                team.location?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "শেয়ার করো",
                modifier = Modifier.clickable {
                    ShareUtils.shareText(context, "${team.name} টিম দেখো Stumps অ্যাপে!")
                }.padding(8.dp)
            )
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "টিম সেটিংস",
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text = "Team ID : ${team.id.take(8)}",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        EmptyState(
            icon = Icons.Filled.Star,
            title = "এখনো কোনো ম্যাচের তথ্য নেই",
            subtitle = "এই টিম দিয়ে ম্যাচ/টুর্নামেন্ট খেললে win/loss, top performer আর recent form এখানে দেখা যাবে।"
        )
    }
}

/** The real, functional Players tab — list of saved players, with a dialog to add more. */
@Composable
private fun TeamPlayersTab(players: List<PlayerEntity>, onAddPlayer: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onAddPlayer) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("ADD PLAYER")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (players.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AccountBox,
                title = "No Players",
                subtitle = "উপরের বাটনে চেপে এই টিমের প্রথম খেলোয়াড় যোগ করো।"
            )
        } else {
            players.forEachIndexed { index, player ->
                ListItemCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).staggeredEntrance(index),
                    title = player.name,
                    subtitle = player.role,
                    leading = {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = player.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

/** A small popup for adding one player by name — matches the reference app's "Create New
 * Player" half of its Add Player dialog. Its OTHER half ("Add Player (Using Profile ID)") isn't
 * offered here: that needs players to be full linked accounts searchable by a shareable ID,
 * which this app's simpler name-only player model doesn't support. */
@Composable
private fun AddPlayerDialog(onDismiss: () -> Unit, onAdd: (name: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন খেলোয়াড়") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("পুরো নাম") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name.trim()) }) { Text("ADD / CREATE PLAYER") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

/** TEAM/BAT/BOWL/FIELD/MVP sub-tabs — every one of them genuinely has no data to show yet
 * without a team-level match history, so this matches the reference app's OWN "No data to show"
 * empty state rather than trying to invent a fuller table full of zeroes. */
@Composable
private fun TeamStatisticsTab() {
    var subTab by remember { mutableStateOf(0) }
    val subTabs = listOf("TEAM", "BAT", "BOWL", "FIELD", "MVP")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subTabs.forEachIndexed { index, label ->
                AnimatedTabChip(label, subTab == index) { subTab = index }
            }
        }
        Spacer(Modifier.height(24.dp))
        EmptyState(icon = Icons.Filled.Star, title = "No data to show.")
    }
}

/** Search box + "select one of my teams" — same treatment as ProfileScreen's Compare tab: the
 * UI structure is real, but actually comparing two teams' numbers needs a team search/lookup
 * this app doesn't have yet, so no comparison can actually run yet. */
@Composable
private fun TeamCompareTab(team: TeamEntity) {
    var query by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search a Team") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Search a team to compare, or use one of your own teams (currently: ${team.name}).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Team Settings — the fields this app can actually back for real (name, location) plus a
 * delete button. The reference app's social-media-ID fields and "Show Team ID"/"Restrict Edit
 * Access" toggles are left out on purpose rather than shown as fields that would silently not
 * save anything: those need new database columns and (for edit-access) a real multi-admin
 * permissions model neither of which exist yet. */
@Composable
private fun TeamSettingsScreen(team: TeamEntity, onSave: (name: String, location: String?) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf(team.name) }
    var location by remember { mutableStateOf(team.location ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = "মুছে ফেলো", tint = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { showDeleteConfirm = true })
            Spacer(Modifier.weight(1f))
            Text(text = "Team Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("✕") }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Team Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = name.isNotBlank(),
            onClick = { onSave(name.trim(), location.trim().ifBlank { null }) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("SAVE") }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("টিম মুছে ফেলবে?") },
            text = { Text("${team.name} টিম আর এর প্লেয়ার লিস্ট স্থায়ীভাবে মুছে যাবে — এটা আর ফিরিয়ে আনা যাবে না।") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("মুছে ফেলো", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("বাতিল") } }
        )
    }
}
