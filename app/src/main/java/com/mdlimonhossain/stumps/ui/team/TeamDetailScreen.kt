package com.mdlimonhossain.stumps.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.mdlimonhossain.stumps.data.local.db.match.MatchEntity
import com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.ListItemCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import com.mdlimonhossain.stumps.ui.theme.PitchGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which of the six tabs on a team's detail page is currently showing. */
private enum class TeamTab { OVERVIEW, PLAYERS, MATCHES, TOURNAMENTS, STATISTICS, COMPARE }

/**
 * The full detail page for ONE saved team — reached by tapping a team card on "আমার টিম" or
 * Search. Every tab now shows real data computed from this app's own match/tournament records:
 * matches are found with a database lookup for "any match where this team was teamAId OR
 * teamBId", tournaments with a lookup through the tournament_teams table, win/loss record with
 * MatchRepository.teamRecord (replays every finished match's two innings and compares run
 * totals), and each player's Matches/Runs/Wickets summary with StatsRepository.careerStatsFor
 * (the same career-stats engine the signed-in user's own Profile page uses). `onOpenMatch`/
 * `onOpenTournament`/`onOpenPlayer` let the caller navigate away when a row in those tabs is
 * tapped.
 */
@Composable
fun TeamDetailScreen(
    teamId: String,
    viewerUid: String,
    onBack: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit = {},
    onOpenTournament: (tournamentId: String) -> Unit = {},
    onOpenPlayer: (playerId: String) -> Unit = {}
) {
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
    val matches by app.matchRepository.observeMatchesForTeam(teamId).collectAsState(initial = emptyList())
    val tournaments by app.tournamentRepository.observeTournamentsForTeam(teamId).collectAsState(initial = emptyList())

    // A career-stats lookup for every player currently on the roster, keyed by their row id —
    // recomputed whenever the roster itself changes (a player added/removed). Kept up here, one
    // level above the Players AND Statistics tabs, so both can share the same numbers instead of
    // each re-walking every match's ball-by-ball data a second time.
    var playerStats by remember { mutableStateOf<Map<String, com.mdlimonhossain.stumps.domain.repository.CareerStats>>(emptyMap()) }
    LaunchedEffect(players) {
        playerStats = players.associate { it.id to app.statsRepository.careerStatsFor(viewerUid, it.name) }
    }
    // This team's own Played/Won/Lost/Tied record — recomputed whenever its match list changes.
    var teamRecord by remember { mutableStateOf<com.mdlimonhossain.stumps.domain.repository.TeamRecord?>(null) }
    LaunchedEffect(matches) {
        teamRecord = app.matchRepository.teamRecord(teamId)
    }

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
            TeamTab.OVERVIEW -> TeamOverviewTab(team = t, teamRecord = teamRecord, onOpenSettings = { showSettings = true })
            TeamTab.PLAYERS -> TeamPlayersTab(
                players = players,
                playerStats = playerStats,
                onAddPlayer = { showAddPlayer = true },
                onOpenPlayer = onOpenPlayer
            )
            TeamTab.MATCHES -> TeamMatchesTab(team = t, matches = matches, onOpenMatch = onOpenMatch)
            TeamTab.TOURNAMENTS -> TeamTournamentsTab(tournaments = tournaments, onOpenTournament = onOpenTournament)
            TeamTab.STATISTICS -> TeamStatisticsTab(players = players, playerStats = playerStats, teamRecord = teamRecord)
            TeamTab.COMPARE -> TeamCompareTab(team = t, ourRecord = teamRecord, viewerUid = viewerUid)
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

/**
 * The header (avatar/name/location/id/share/settings) plus this team's real Played/Won/Lost/Tied
 * record. The reference app's other Overview widgets — Top Performers, Scheduled Matches, and
 * recent-runs/wickets charts — still aren't shown here: those need PER-MATCH highlights (who
 * scored the most in each game) rather than the simple career totals this pass computes, so
 * they're left for a later pass instead of being faked.
 */
@Composable
private fun TeamOverviewTab(team: TeamEntity, teamRecord: com.mdlimonhossain.stumps.domain.repository.TeamRecord?, onOpenSettings: () -> Unit) {
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
        if (teamRecord == null || teamRecord.played == 0) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = "এখনো কোনো ম্যাচের তথ্য নেই",
                subtitle = "এই টিম দিয়ে ম্যাচ/টুর্নামেন্ট খেললে win/loss এখানে দেখা যাবে।"
            )
        } else {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OverviewRecordStat("Played", teamRecord.played.toString())
                    OverviewRecordStat("Won", teamRecord.won.toString())
                    OverviewRecordStat("Lost", teamRecord.lost.toString())
                    OverviewRecordStat("Tied", teamRecord.tied.toString())
                    OverviewRecordStat("Win %", com.mdlimonhossain.stumps.ui.designsystem.oneDecimal(teamRecord.winPercent))
                }
            }
        }
    }
}

@Composable
private fun OverviewRecordStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The real, functional Players tab — list of saved players, each shown as a summary card
 * (Matches/Runs/Wickets, Captain/Vice-Captain/Wicket-Keeper badges, a link into their full
 * profile), plus a dialog to add more players.
 */
@Composable
private fun TeamPlayersTab(
    players: List<PlayerEntity>,
    playerStats: Map<String, com.mdlimonhossain.stumps.domain.repository.CareerStats>,
    onAddPlayer: () -> Unit,
    onOpenPlayer: (String) -> Unit
) {
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
                TeamPlayerRow(
                    player = player,
                    stats = playerStats[player.id] ?: com.mdlimonhossain.stumps.domain.repository.CareerStats(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).staggeredEntrance(index),
                    onOpenProfile = { onOpenPlayer(player.id) }
                )
            }
        }
    }
}

/**
 * One player's summary card on the Players tab — matches the reference app's row: an avatar +
 * name + "Unregistered Player" badge up top (edit/delete icons alongside), a Matches/Runs/
 * Wickets strip in the middle (from this team's shared `playerStats` lookup), and a row of C/VC/
 * WK badges plus a "View Profile" button at the bottom. C and VC are real, tappable toggles
 * (TeamRepository.toggleCaptain/toggleViceCaptain enforce "only one per team"); WK just reflects
 * this player's saved role (PlayerRole.WICKET_KEEPER) rather than being independently toggleable,
 * since that's a roster fact set when the player was added/edited, not a separate flag.
 */
@Composable
private fun TeamPlayerRow(
    player: PlayerEntity,
    stats: com.mdlimonhossain.stumps.domain.repository.CareerStats,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(text = player.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = player.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showInfo = true }) {
                    Text(text = "Unregistered Player", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "এর মানে কী?",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "নাম পরিবর্তন করো",
                modifier = Modifier.size(20.dp).clickable { showEdit = true }
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "খেলোয়াড় মুছে ফেলো",
                modifier = Modifier.size(20.dp).clickable { showDeleteConfirm = true }
            )
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OverviewRecordStat("Matches", stats.matchesPlayed.toString())
            OverviewRecordStat("Runs", stats.runs.toString())
            OverviewRecordStat("Wickets", stats.wickets.toString())
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoleBadgeCircle(label = "C", active = player.isCaptain) {
                scope.launch { app.teamRepository.toggleCaptain(player) }
            }
            Spacer(Modifier.width(10.dp))
            RoleBadgeCircle(label = "VC", active = player.isViceCaptain) {
                scope.launch { app.teamRepository.toggleViceCaptain(player) }
            }
            Spacer(Modifier.width(10.dp))
            RoleBadgeCircle(label = "WK", active = player.role == PlayerRole.WICKET_KEEPER.name, onClick = null)
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.OutlinedButton(onClick = onOpenProfile) { Text("View Profile") }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Unregistered Player") },
            text = { Text("এই খেলোয়াড়কে শুধু নাম দিয়ে টিমে যোগ করা হয়েছে — এটা কোনো real Stumps অ্যাকাউন্টের সাথে যুক্ত না।") },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("বুঝেছি") } }
        )
    }
    if (showEdit) {
        var newName by remember { mutableStateOf(player.name) }
        // Turns on the red "name required" message once they've tried to save with it blank.
        var attemptedSubmit by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("নাম পরিবর্তন করো") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    isError = attemptedSubmit && newName.isBlank(),
                    supportingText = { if (attemptedSubmit && newName.isBlank()) Text("নাম খালি রাখা যাবে না") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                // Always tappable — tapping with a blank name just shows the red message above.
                TextButton(
                    onClick = {
                        attemptedSubmit = true
                        if (newName.isNotBlank()) {
                            scope.launch { app.teamRepository.renamePlayer(player, newName.trim()) }
                            showEdit = false
                        }
                    }
                ) { Text("সেভ করো") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("বাতিল") } }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("খেলোয়াড় মুছে ফেলবে?") },
            text = { Text("${player.name}-কে এই টিম থেকে স্থায়ীভাবে মুছে ফেলা হবে।") },
            confirmButton = {
                TextButton(onClick = { scope.launch { app.teamRepository.deletePlayer(player) }; showDeleteConfirm = false }) {
                    Text("মুছে ফেলো", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("বাতিল") } }
        )
    }
}

/** One small circular C/VC/WK badge — filled when active, just outlined when not. `onClick` null means it's a read-only indicator (used for WK, which just mirrors the player's saved role). */
@Composable
private fun RoleBadgeCircle(label: String, active: Boolean, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The real Matches tab — every match this team has played (found via MatchDao.observeMatchesForTeam), newest first. */
@Composable
private fun TeamMatchesTab(team: TeamEntity, matches: List<MatchEntity>, onOpenMatch: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (matches.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = "No Matches",
                subtitle = "This team has not played in any matches."
            )
        } else {
            matches.forEachIndexed { index, match ->
                TeamMatchRow(
                    team = team,
                    match = match,
                    modifier = Modifier.staggeredEntrance(index).padding(vertical = 6.dp),
                    onClick = { onOpenMatch(match.id) }
                )
            }
        }
    }
}

/**
 * One match row on a team's own Matches tab. `match.teamAId`/`teamBId` only stores the OTHER
 * team's raw id, not its name — so this row looks the opposing team's name up for itself
 * (same "figure it out on demand" pattern MatchHistoryScreen's own row already uses for its
 * resumable check) rather than the whole tab pre-loading every opponent up front.
 */
@Composable
private fun TeamMatchRow(team: TeamEntity, match: MatchEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val opponentId = if (match.teamAId == team.id) match.teamBId else match.teamAId
    var opponentName by remember(match.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(match.id) {
        opponentName = app.teamRepository.getTeamOnce(opponentId)?.name
    }
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(match.createdAt))

    ListItemCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        title = "vs ${opponentName ?: "..."}",
        subtitle = "${match.oversLimit}-over • $date • ${match.status}"
    )
}

/** The real Tournaments tab — every tournament this team has entered (found via TournamentDao.observeTournamentsForTeam). */
@Composable
private fun TeamTournamentsTab(tournaments: List<TournamentEntity>, onOpenTournament: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (tournaments.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = "No Tournaments",
                subtitle = "This team has not participated in any tournaments."
            )
        } else {
            tournaments.forEachIndexed { index, tournament ->
                ListItemCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).staggeredEntrance(index),
                    onClick = { onOpenTournament(tournament.id) },
                    title = tournament.name,
                    subtitle = tournament.venue ?: "${tournament.oversPerMatch}-over • ${tournament.format}"
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
    // Turns on the red "name required" message once they've tried to add with it blank.
    var attemptedSubmit by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন খেলোয়াড়") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("পুরো নাম") },
                singleLine = true,
                isError = attemptedSubmit && name.isBlank(),
                supportingText = { if (attemptedSubmit && name.isBlank()) Text("খেলোয়াড়ের নাম লিখতে হবে") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            // Always tappable — tapping with a blank name just shows the red message above.
            TextButton(
                onClick = {
                    attemptedSubmit = true
                    if (name.isNotBlank()) onAdd(name.trim())
                }
            ) { Text("ADD / CREATE PLAYER") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

/**
 * TEAM/BAT/BOWL/FIELD/MVP sub-tabs — all real now: TEAM shows the win/loss record computed by
 * MatchRepository.teamRecord, and BAT/BOWL/FIELD/MVP each rank this team's own roster by their
 * career stats (the same numbers the Players tab's summary cards use). MVP ranks by a simple,
 * clearly-labelled composite score (runs + wickets×20 + fielding dismissals×10) rather than any
 * official statistic, since cricket has no single standard "who was the MVP" formula.
 */
@Composable
private fun TeamStatisticsTab(
    players: List<PlayerEntity>,
    playerStats: Map<String, com.mdlimonhossain.stumps.domain.repository.CareerStats>,
    teamRecord: com.mdlimonhossain.stumps.domain.repository.TeamRecord?
) {
    var subTab by remember { mutableStateOf(0) }
    val subTabs = listOf("TEAM", "BAT", "BOWL", "FIELD", "MVP")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subTabs.forEachIndexed { index, label ->
                AnimatedTabChip(label, subTab == index) { subTab = index }
            }
        }
        Spacer(Modifier.height(20.dp))

        when (subTab) {
            0 -> {
                if (teamRecord == null || teamRecord.played == 0) {
                    EmptyState(icon = Icons.Filled.Star, title = "No data to show.", subtitle = "এই টিম দিয়ে ম্যাচ খেললে win/loss রেকর্ড এখানে দেখা যাবে।")
                } else {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            OverviewRecordStat("Played", teamRecord.played.toString())
                            OverviewRecordStat("Won", teamRecord.won.toString())
                            OverviewRecordStat("Lost", teamRecord.lost.toString())
                            OverviewRecordStat("Tied", teamRecord.tied.toString())
                            OverviewRecordStat("Win %", com.mdlimonhossain.stumps.ui.designsystem.oneDecimal(teamRecord.winPercent))
                        }
                    }
                }
            }
            1 -> PlayerStatRankingList(players, playerStats, sortBy = { it.runs }) { s -> "Runs: ${s.runs} • Avg: ${com.mdlimonhossain.stumps.ui.designsystem.oneDecimal(s.battingAverage)} • HS: ${s.highScore}" }
            2 -> PlayerStatRankingList(players, playerStats, sortBy = { it.wickets }) { s -> "Wickets: ${s.wickets} • Avg: ${if (s.wickets == 0) "-" else com.mdlimonhossain.stumps.ui.designsystem.oneDecimal(s.bowlingAverage)} • Best: ${s.bestBowlingFigures}" }
            3 -> PlayerStatRankingList(players, playerStats, sortBy = { it.catches + it.stumpings + it.runOuts }) { s -> "Catches: ${s.catches} • Stumpings: ${s.stumpings} • Runouts: ${s.runOuts}" }
            4 -> {
                Text(
                    text = "MVP Points = Runs + (Wickets × 20) + (Catches+Stumpings+Runouts × 10) — একটা সহজ মিলিত স্কোর, কোনো official cricket পরিসংখ্যান না।",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PlayerStatRankingList(
                    players, playerStats,
                    sortBy = { it.runs + it.wickets * 20 + (it.catches + it.stumpings + it.runOuts) * 10 }
                ) { s -> "MVP Points: ${s.runs + s.wickets * 20 + (s.catches + s.stumpings + s.runOuts) * 10}" }
            }
        }
    }
}

/** A players-of-this-team list ranked highest-first by whatever `sortBy` extracts from their CareerStats, with a caller-chosen subtitle line. Shared by all four ranked Statistics sub-tabs above. */
@Composable
private fun PlayerStatRankingList(
    players: List<PlayerEntity>,
    playerStats: Map<String, com.mdlimonhossain.stumps.domain.repository.CareerStats>,
    sortBy: (com.mdlimonhossain.stumps.domain.repository.CareerStats) -> Int,
    subtitleFor: (com.mdlimonhossain.stumps.domain.repository.CareerStats) -> String
) {
    if (players.isEmpty()) {
        EmptyState(icon = Icons.Filled.AccountBox, title = "No Players", subtitle = "এই টিমে এখনো কোনো খেলোয়াড় যোগ করা হয়নি।")
        return
    }
    val ranked = players
        .map { it to (playerStats[it.id] ?: com.mdlimonhossain.stumps.domain.repository.CareerStats()) }
        .sortedByDescending { (_, stats) -> sortBy(stats) }
    ranked.forEachIndexed { index, (player, stats) ->
        ListItemCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).staggeredEntrance(index),
            title = "${index + 1}. ${player.name}",
            subtitle = subtitleFor(stats)
        )
    }
}

/**
 * A search box to find another of the viewer's OWN saved teams, then a real side-by-side
 * Played/Won/Lost/Win% comparison against this team — both computed with the same
 * MatchRepository.teamRecord used by the TEAM stats sub-tab above.
 */
@Composable
private fun TeamCompareTab(team: TeamEntity, ourRecord: com.mdlimonhossain.stumps.domain.repository.TeamRecord?, viewerUid: String) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TeamEntity>>(emptyList()) }
    var selected by remember { mutableStateOf<TeamEntity?>(null) }
    var selectedRecord by remember { mutableStateOf<com.mdlimonhossain.stumps.domain.repository.TeamRecord?>(null) }

    LaunchedEffect(query) {
        results = if (query.isBlank()) emptyList() else app.teamRepository.searchByName(viewerUid, query).filter { it.id != team.id }
    }
    LaunchedEffect(selected) {
        selectedRecord = selected?.let { app.matchRepository.teamRecord(it.id) }
    }

    val other = selected
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (other == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search a Team") },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            if (query.isBlank()) {
                Text(
                    text = "নিজের সেভ করা অন্য কোনো টিমের নাম লিখে তুলনা করো।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else if (results.isEmpty()) {
                Text(
                    text = "কোনো টিম পাওয়া যায়নি।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                results.forEach { candidate ->
                    ListItemCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        title = candidate.name,
                        subtitle = candidate.location,
                        onClick = { selected = candidate }
                    )
                }
            }
        } else {
            TextButton(onClick = { selected = null; query = "" }) { Text("← অন্য টিম বেছে নাও") }
            Spacer(Modifier.height(8.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = team.name, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        CompareRecordColumn(ourRecord)
                    }
                    Spacer(Modifier.width(1.dp).height(80.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = other.name, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        CompareRecordColumn(selectedRecord)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareRecordColumn(record: com.mdlimonhossain.stumps.domain.repository.TeamRecord?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CompareRecordRow("Played", record?.played?.toString() ?: "-")
        CompareRecordRow("Won", record?.won?.toString() ?: "-")
        CompareRecordRow("Lost", record?.lost?.toString() ?: "-")
        CompareRecordRow("Win %", record?.let { com.mdlimonhossain.stumps.ui.designsystem.oneDecimal(it.winPercent) } ?: "-")
    }
}

@Composable
private fun CompareRecordRow(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = value, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    // Only shows the red "name required" message after the user has tried to save once.
    var attemptedSubmit by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = "মুছে ফেলো", tint = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { showDeleteConfirm = true })
            Spacer(Modifier.weight(1f))
            Text(text = "Team Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("✕") }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Team Name") },
            isError = attemptedSubmit && name.isBlank(),
            supportingText = { if (attemptedSubmit && name.isBlank()) Text("টিমের নাম খালি রাখা যাবে না") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        // Always tappable — tapping with a blank name just shows the red message above.
        Button(
            onClick = {
                attemptedSubmit = true
                if (name.isNotBlank()) onSave(name.trim(), location.trim().ifBlank { null })
            },
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
