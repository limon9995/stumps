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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
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
fun TournamentDetailScreen(tournamentId: String, organizerUid: String, onBack: () -> Unit, onOpenCreateTeam: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: TournamentDetailViewModel = viewModel(factory = TournamentDetailViewModel.Factory(app.tournamentRepository, tournamentId))

    val tournament by viewModel.tournament.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val fixtures by viewModel.fixtures.collectAsState()
    val standings by viewModel.standings.collectAsState()
    val leaderboards by viewModel.leaderboards.collectAsState()
    val boundaryCounts by viewModel.boundaryCounts.collectAsState()

    var tab by remember { mutableStateOf(DetailTab.HOME) }
    // Which fixture (if any) is currently being scored/viewed — null means show the tab view instead.
    var activeFixture by remember { mutableStateOf<TournamentFixtureEntity?>(null) }

    // Recalculate the points table / leaderboard whenever the user switches TO that tab, or
    // whenever the fixture list changes (e.g. a match just finished) while already on that tab.
    // HOME also needs leaderboards + boundary counts loaded, since its own preview sections reuse that same data.
    LaunchedEffect(tab, fixtures) {
        if (tab == DetailTab.POINTS) viewModel.refreshStandings(tournamentId)
        if (tab == DetailTab.STATISTICS || tab == DetailTab.HOME) viewModel.refreshLeaderboards(tournamentId)
        if (tab == DetailTab.HOME) viewModel.refreshBoundaryCounts(tournamentId)
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
                boundaryCounts = boundaryCounts,
                onSeeTeams = { tab = DetailTab.TEAMS },
                onSeeMatches = { tab = DetailTab.MATCHES },
                viewerUid = organizerUid,
                onRename = { newName -> viewModel.rename(t, newName) },
                onDelete = { viewModel.delete(t, onBack) }
            )
            DetailTab.TEAMS -> TeamsTab(
                teams = teams,
                viewerUid = organizerUid,
                onOpenCreateTeam = onOpenCreateTeam,
                onAddTeam = { teamId -> viewModel.addTeam(tournamentId, teamId) },
                onGoToMatches = { tab = DetailTab.MATCHES }
            )
            DetailTab.MATCHES -> MatchesTab(fixtures = fixtures, teams = teams, onOpenFixture = { activeFixture = it }, onGoToTeams = { tab = DetailTab.TEAMS })
            DetailTab.POINTS -> PointsTab(standings = standings)
            DetailTab.STATISTICS -> StatisticsTab(leaderboards = leaderboards)
        }
    }
}


/**
 * The tournament's own "front page" — a ball-type-tinted banner (there's no image-upload feature
 * for a real banner photo, so a coloured gradient + a big ball icon stands in for the reference
 * app's artwork, the SAME honest substitution TeamDetailScreen already makes for team avatars),
 * club/city/date-range facts, Tour ID, Follow/Share/More, a teams preview, top-players preview,
 * and the tournament's total sixes/fours so far ("Tournament Boundaries").
 */
@Composable
private fun HomeTab(
    tournament: com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity,
    teams: List<com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity>,
    leaderboards: com.mdlimonhossain.stumps.domain.repository.TournamentLeaderboards,
    boundaryCounts: Pair<Int, Int>,
    onSeeTeams: () -> Unit,
    onSeeMatches: () -> Unit,
    viewerUid: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.mdlimonhossain.stumps.StumpsApplication
    val isFollowing by app.followRepository.observeIsFollowing(
        viewerUid, com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType.TOURNAMENT, tournament.id
    ).collectAsState(initial = false)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isLeather = tournament.ballType == "LEATHER"
    val (bannerA, bannerB) = if (isLeather) {
        MaterialTheme.colorScheme.error to Color(0xFF5C1A1A)
    } else {
        com.mdlimonhossain.stumps.ui.theme.PitchGreen to MaterialTheme.colorScheme.tertiary
    }
    val dateFormat = remember { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.large)
                .background(Brush.horizontalGradient(listOf(bannerA, bannerB))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isLeather) MaterialTheme.colorScheme.error else com.mdlimonhossain.stumps.ui.theme.PitchGreen))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tournament.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val subtitle = listOfNotNull(tournament.clubName, tournament.city).joinToString(", ")
                if (subtitle.isNotBlank()) Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                if (tournament.startDate != null) {
                    val range = if (tournament.endDate != null) {
                        "${dateFormat.format(java.util.Date(tournament.startDate))} - ${dateFormat.format(java.util.Date(tournament.endDate))}"
                    } else {
                        dateFormat.format(java.util.Date(tournament.startDate))
                    }
                    Text(text = range, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Text(text = "Tour ID : ${tournament.id.take(8)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "শেয়ার করো",
                modifier = Modifier.clickable { ShareUtils.shareText(context, "${tournament.name} টুর্নামেন্ট দেখো Stumps অ্যাপে!") }.padding(8.dp)
            )
            Box {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "আরও অপশন",
                    modifier = Modifier.clickable { showMoreMenu = true }.padding(8.dp)
                )
                androidx.compose.material3.DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("নাম পরিবর্তন করো") }, onClick = { showMoreMenu = false; showRename = true })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("মুছে ফেলো") }, onClick = { showMoreMenu = false; showDeleteConfirm = true })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TournamentBallTypeBadge(ballType = tournament.ballType ?: "LEATHER")
            Spacer(Modifier.weight(1f))
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
        }
        Spacer(Modifier.height(4.dp))
        // A match needs two teams. So until the tournament has at least 2 teams, this big
        // button takes the organizer to the Teams tab (the real next step) instead of an empty
        // Matches list that would just look broken.
        val needsTeams = teams.size < 2
        if (needsTeams) {
            NextStepHint(
                text = if (teams.isEmpty()) {
                    "পরের ধাপ: টুর্নামেন্টে কমপক্ষে ২টা টিম যোগ করো। টিম যোগ করলেই ম্যাচগুলো নিজে নিজে তৈরি হয়ে যাবে।"
                } else {
                    "আর ১টা টিম যোগ করো — তাহলেই প্রথম ম্যাচ তৈরি হয়ে যাবে।"
                }
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = if (needsTeams) onSeeTeams else onSeeMatches, modifier = Modifier.weight(1f)) {
                Text(if (needsTeams) "টিম যোগ করো" else "START / SCHEDULE MATCH", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Teams", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(text = "সব দেখো »", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onSeeTeams))
        }
        Spacer(Modifier.height(10.dp))
        if (teams.isEmpty()) {
            Text(
                text = "টুর্নামেন্টে টিম যোগ করে ম্যাচ শুরু করো।",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onSeeTeams)
            )
        } else {
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
        Text(text = "Tournament Boundaries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Sixes", value = boundaryCounts.first.toString(), playerName = "")
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Fours", value = boundaryCounts.second.toString(), playerName = "")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showRename) {
        var newName by remember { mutableStateOf(tournament.name) }
        // Turns on the red "name required" message once they've tried to save with it blank.
        var attemptedSubmit by remember { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRename = false },
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
                        if (newName.isNotBlank()) { onRename(newName.trim()); showRename = false }
                    }
                ) { Text("সেভ করো") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("বাতিল") } }
        )
    }
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("টুর্নামেন্ট মুছে ফেলবে?") },
            text = { Text("${tournament.name} টুর্নামেন্ট স্থায়ীভাবে মুছে যাবে — এটা আর ফিরিয়ে আনা যাবে না।") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("মুছে ফেলো", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("বাতিল") } }
        )
    }
}

/** A tiny coloured-dot + label badge for a tournament's ball type — same visual convention as HomeScreen.kt's own BallTypeBadge (kept as its own small copy here rather than a shared extraction, since it's this simple). */
@Composable
private fun TournamentBallTypeBadge(ballType: String) {
    val isLeather = ballType == "LEATHER"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isLeather) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary))
        Spacer(Modifier.width(6.dp))
        Text(text = if (isLeather) "লেদার বল" else "টেনিস বল", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * A grid of every team taking part — names + initials circles, since no team-logo-upload feature
 * exists yet — plus an "ADD TEAM" flow with two real options: create a brand new saved team, or
 * pick one of the organizer's own saved teams that isn't in this tournament yet. The reference
 * app's third option ("Search by Team ID") is left out on purpose — this app's saved teams are
 * private to whoever made them, with no cross-user team directory to search, so a "search by ID"
 * box here would never actually find anything.
 */
@Composable
private fun TeamsTab(
    teams: List<com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity>,
    viewerUid: String,
    onOpenCreateTeam: () -> Unit,
    onAddTeam: (String) -> Unit,
    onGoToMatches: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val myTeams by app.teamRepository.observeTeamsForUser(viewerUid).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("+ টিম যোগ করো") }
        Spacer(Modifier.height(8.dp))
        // Tell the organizer where they are in the setup: under 2 teams = keep adding,
        // 2 or more = matches are ready, go play them.
        if (teams.size < 2) {
            NextStepHint(text = "মোট ${teams.size}টা টিম। ম্যাচ তৈরি হতে কমপক্ষে ২টা টিম লাগবে।")
        } else {
            NextStepHint(
                text = "মোট ${teams.size}টা টিম — ম্যাচগুলো তৈরি হয়ে গেছে! Matches ট্যাবে গিয়ে ম্যাচ শুরু করো।",
                actionLabel = "ম্যাচ দেখো",
                onAction = onGoToMatches
            )
        }
        Spacer(Modifier.height(8.dp))
        if (teams.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AccountBox,
                title = "কোনো দল যোগ করা হয়নি",
                subtitle = "উপরের বাটনে চেপে তোমার সেভ করা টিম বেছে নাও, অথবা নতুন টিম বানাও।",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                items(teams, key = { it.id }) { team ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamInitialsCircle(name = team.teamName)
                        Spacer(Modifier.width(10.dp))
                        Text(text = team.teamName, fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val alreadyInIds = teams.map { it.teamId }.toSet()
        val pickable = myTeams.filter { it.id !in alreadyInIds }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("টিম যোগ করো") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    TextButton(onClick = { showAddDialog = false; onOpenCreateTeam() }) { Text("নতুন টিম তৈরি করো") }
                    // Creating a team opens the Teams screen. It doesn't join this tournament by
                    // itself, so remind the user to come back here and pick it from this list.
                    Text(
                        text = "নতুন টিম বানিয়ে ফিরে এসে এই তালিকা থেকে সেটা বেছে নিও।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    if (pickable.isEmpty()) {
                        Text(
                            text = if (myTeams.isEmpty()) "তোমার কোনো সেভ করা টিম নেই — আগে উপরের বাটনে একটা নতুন টিম বানাও।" else "তোমার সব টিম এই টুর্নামেন্টে যোগ হয়ে গেছে।",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            pickable.forEach { team ->
                                Text(
                                    text = team.name,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth().clickable { showAddDialog = false; onAddTeam(team.id) }.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddDialog = false }) { Text("বন্ধ করো") } }
        )
    }
}


/**
 * A small highlighted box that tells the organizer what to do NEXT in setting up the tournament
 * (e.g. "add at least 2 teams"). It can have an optional button, like "ম্যাচ দেখো" (see matches).
 */
@Composable
private fun NextStepHint(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        // Only show the button if the caller gave us both a label and something to do.
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
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
    onOpenFixture: (TournamentFixtureEntity) -> Unit,
    onGoToTeams: () -> Unit
) {
    if (fixtures.isEmpty()) {
        // No fixtures yet always means "fewer than 2 teams" — fixtures are made automatically
        // the moment a second (third, fourth...) team is added. So tell the user exactly that,
        // and give them a button that takes them straight to where they can fix it.
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = "এখনো কোনো ম্যাচ তৈরি হয়নি",
            subtitle = "কমপক্ষে ২টা টিম যোগ করো — তাহলে প্রতিটা টিমের সাথে প্রতিটা টিমের ম্যাচ নিজে থেকেই এখানে চলে আসবে। এখন আছে ${teams.size}টা টিম।",
            actionLabel = "টিম যোগ করো",
            onAction = onGoToTeams
        )
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
    var showMore by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        // A quick-glance card grid, matching the reference app's Statistics page layout — each
        // card shows the tournament's single current leader for that number.
        Row(modifier = Modifier.fillMaxWidth()) {
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Most Runs", value = (leaderboards.orangeCap.firstOrNull()?.value ?: 0).toString(), playerName = leaderboards.orangeCap.firstOrNull()?.playerName ?: "-")
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Most Wickets", value = (leaderboards.purpleCap.firstOrNull()?.value ?: 0).toString(), playerName = leaderboards.purpleCap.firstOrNull()?.playerName ?: "-")
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Highest Score", value = (leaderboards.highestScore?.value ?: 0).toString(), playerName = leaderboards.highestScore?.playerName ?: "-")
            val bb = leaderboards.bestBowling
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Best Bowling", value = if (bb == null) "0-0" else "${bb.wickets}-${bb.runsConceded}", playerName = bb?.playerName ?: "-")
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Most Sixes", value = (leaderboards.mostSixes.firstOrNull()?.value ?: 0).toString(), playerName = leaderboards.mostSixes.firstOrNull()?.playerName ?: "-")
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Most Fours", value = (leaderboards.mostFours.firstOrNull()?.value ?: 0).toString(), playerName = leaderboards.mostFours.firstOrNull()?.playerName ?: "-")
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Stumpings", value = (leaderboards.mostStumpings.firstOrNull()?.value ?: 0).toString(), playerName = leaderboards.mostStumpings.firstOrNull()?.playerName ?: "-")
            TopPlayerStat(modifier = Modifier.weight(1f), label = "Catches", value = (leaderboards.mostCatches.firstOrNull()?.value ?: 0).toString(), playerName = leaderboards.mostCatches.firstOrNull()?.playerName ?: "-")
        }
        Spacer(Modifier.height(16.dp))

        if (showMore) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Fastest Fifty",
                    value = leaderboards.fastestFifty?.value?.toString() ?: "-",
                    playerName = leaderboards.fastestFifty?.playerName ?: "-"
                )
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Fastest Hundred",
                    value = leaderboards.fastestHundred?.value?.toString() ?: "-",
                    playerName = leaderboards.fastestHundred?.playerName ?: "-"
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                val bp = leaderboards.bestPartnership
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Best Partnership",
                    value = (bp?.runs ?: 0).toString(),
                    playerName = if (bp == null) "-" else "${bp.playerAName} & ${bp.playerBName}"
                )
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Most Balls Faced",
                    value = (leaderboards.mostBallsFaced.firstOrNull()?.value ?: 0).toString(),
                    playerName = leaderboards.mostBallsFaced.firstOrNull()?.playerName ?: "-"
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Innings Most Sixes",
                    value = (leaderboards.inningsMostSixes?.value ?: 0).toString(),
                    playerName = leaderboards.inningsMostSixes?.playerName ?: "-"
                )
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Innings Most Fours",
                    value = (leaderboards.inningsMostFours?.value ?: 0).toString(),
                    playerName = leaderboards.inningsMostFours?.playerName ?: "-"
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                val be = leaderboards.bestEconomy
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Best Economy",
                    value = if (be == null) "-" else String.format(java.util.Locale.US, "%.1f", be.economy),
                    playerName = be?.playerName ?: "-"
                )
                TopPlayerStat(
                    modifier = Modifier.weight(1f), label = "Most Maidens",
                    value = (leaderboards.mostMaidens.firstOrNull()?.value ?: 0).toString(),
                    playerName = leaderboards.mostMaidens.firstOrNull()?.playerName ?: "-"
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            androidx.compose.material3.OutlinedButton(onClick = { showMore = !showMore }) {
                Text(if (showMore) "Show Less" else "More Statistics")
            }
        }
        Spacer(Modifier.height(24.dp))

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
