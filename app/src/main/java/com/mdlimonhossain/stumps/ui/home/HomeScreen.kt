package com.mdlimonhossain.stumps.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.UserEntity
import com.mdlimonhossain.stumps.data.local.db.club.ClubEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.GradientHeroCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import com.mdlimonhossain.stumps.ui.theme.PitchGreen
import kotlinx.coroutines.launch

/**
 * The main dashboard screen a signed-in user lands on — a scrollable page of "sections", each
 * one a horizontally-scrolling row of cards (Matches, then a Profile summary, then
 * Tournaments), finishing with a static help/support block. This mirrors the home page layout
 * of the reference app this project is modelled on.
 *
 * This screen builds its OWN ViewModel (HomeViewModel) instead of being handed data directly,
 * the same pattern MatchHistoryScreen.kt uses — it reads `app.matchRepository` etc. straight off
 * StumpsApplication via LocalContext, so StumpsApp.kt doesn't need to know anything about what
 * data this particular screen needs.
 */
@Composable
fun HomeScreen(
    profile: UserEntity?,
    onStartMatch: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTeams: () -> Unit,
    onOpenTournaments: () -> Unit,
    onOpenTournamentDetail: (tournamentId: String) -> Unit,
    onWatchLive: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenClubs: () -> Unit,
    onOpenClubDetail: (clubId: String) -> Unit,
    onOpenCreateTournament: () -> Unit,
    onOpenRegisterClub: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNews: () -> Unit
) {
    // profile can briefly be null right after login, before it's finished loading from the
    // local database — show a spinner rather than crashing on a missing value.
    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    // `key = profile.uid` makes sure a NEW ViewModel (and fresh data) is built if a different
    // user ever signs in during the same app session, instead of Compose reusing a stale one.
    val viewModel: HomeViewModel = viewModel(
        key = profile.uid,
        factory = HomeViewModel.Factory(
            uid = profile.uid,
            playerName = profile.name,
            matchRepository = app.matchRepository,
            tournamentRepository = app.tournamentRepository,
            statsRepository = app.statsRepository
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    // The clubs THIS user has registered — read straight off the repository (same simple
    // pattern ClubScreen.kt itself uses) rather than adding a whole new field to HomeViewModel
    // just for one rail of cards.
    val clubs by app.clubRepository.observeClubsForUser(profile.uid).collectAsState(initial = emptyList())

    // The side drawer's open/closed state, and a coroutine scope to animate it open when the
    // hamburger button is tapped (opening/closing a drawer is an animation, so it needs a
    // coroutine, not just a plain state flip).
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HomeDrawerContent(
                profile = profile,
                onClose = { scope.launch { drawerState.close() } },
                onOpenHistory = onOpenHistory,
                onOpenTournaments = onOpenTournaments,
                onOpenProfile = onOpenProfile,
                onOpenTeams = onOpenTeams,
                onOpenClubs = onOpenClubs,
                onStartMatch = onStartMatch,
                onOpenCreateTournament = onOpenCreateTournament,
                onOpenRegisterClub = onOpenRegisterClub,
                onOpenFollowing = onOpenFollowing,
                onOpenSettings = onOpenSettings
            )
        }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // ---- Top bar: hamburger (opens the side drawer), app name, search, profile avatar. ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "মেনু খোলো",
                modifier = Modifier.clickable { scope.launch { drawerState.open() } }.padding(end = 4.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(text = "Stumps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "খুঁজো",
                modifier = Modifier.clickable(onClick = onOpenSearch).padding(end = 12.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "খবর",
                modifier = Modifier.clickable(onClick = onOpenNews).padding(end = 12.dp)
            )
            InitialsAvatar(name = profile.name, onClick = onOpenProfile)
        }

        SectionHeader(title = "Matches", onSeeAll = onOpenHistory)
        Spacer(Modifier.height(10.dp))
        if (uiState.matches.isEmpty()) {
            EmptySectionCard(
                message = "এখনো কোনো ম্যাচ নেই",
                actionLabel = "প্রথম ম্যাচ শুরু করো",
                onAction = onStartMatch
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                uiState.matches.forEachIndexed { index, match ->
                    MatchCard(
                        modifier = Modifier.staggeredEntrance(index),
                        match = match,
                        onClick = { onOpenMatch(match.matchId) },
                        onViewTournament = match.tournamentId?.let { id -> { onOpenTournamentDetail(id) } }
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        ProfileSummaryCard(profile = profile, uiState = uiState, onClick = onOpenProfile)

        Spacer(Modifier.height(24.dp))
        SectionHeader(title = "Tournaments", onSeeAll = onOpenTournaments)
        Spacer(Modifier.height(10.dp))
        if (uiState.tournaments.isEmpty()) {
            EmptySectionCard(
                message = "এখনো কোনো টুর্নামেন্ট নেই",
                actionLabel = "টুর্নামেন্ট দেখো",
                onAction = onOpenTournaments
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                uiState.tournaments.forEachIndexed { index, tournament ->
                    TournamentCard(
                        modifier = Modifier.staggeredEntrance(index),
                        tournament = tournament,
                        onClick = { onOpenTournamentDetail(tournament.id) }
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader(title = "Clubs", onSeeAll = onOpenClubs)
        Spacer(Modifier.height(10.dp))
        if (clubs.isEmpty()) {
            EmptySectionCard(
                message = "এখনো কোনো ক্লাব রেজিস্টার করা হয়নি",
                actionLabel = "ক্লাব রেজিস্টার করো",
                onAction = onOpenRegisterClub
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                clubs.forEachIndexed { index, club ->
                    ClubCard(
                        modifier = Modifier.staggeredEntrance(index).padding(bottom = 12.dp),
                        club = club,
                        onClick = { onOpenClubDetail(club.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Button(onClick = onOpenTeams, modifier = Modifier.weight(1f)) { Text("আমার টিম") }
            Spacer(Modifier.width(10.dp))
            Button(onClick = onWatchLive, modifier = Modifier.weight(1f)) { Text("লাইভ দেখো") }
        }

        Spacer(Modifier.height(24.dp))
        SupportCard()
    }
    }
}

/**
 * The side drawer's contents — the reference app's full navigation menu, connecting every
 * screen this whole "phase E" of work added (Clubs, Following, Settings) together with the
 * screens that already existed, all in one place.
 */
@Composable
private fun HomeDrawerContent(
    profile: UserEntity,
    onClose: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTournaments: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTeams: () -> Unit,
    onOpenClubs: () -> Unit,
    onStartMatch: () -> Unit,
    onOpenCreateTournament: () -> Unit,
    onOpenRegisterClub: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // A slightly different tonal shade than the main screen's own background (rather than the
    // plain default), so the drawer visibly reads as its own "layer" sitting on top when open.
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(name = profile.name, onClick = {})
                Spacer(Modifier.width(12.dp))
                Text(text = profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Each row closes the drawer first, THEN navigates — otherwise the drawer would
            // still be open (just invisible) underneath whatever screen opens next.
            DrawerItem("My Matches", icon = Icons.AutoMirrored.Filled.List) { onClose(); onOpenHistory() }
            DrawerItem("My Tournaments", icon = Icons.Filled.Star) { onClose(); onOpenTournaments() }
            DrawerItem("Profile", icon = Icons.Filled.Person) { onClose(); onOpenProfile() }
            DrawerItem("My Teams", icon = Icons.Filled.AccountBox) { onClose(); onOpenTeams() }
            DrawerItem("My Clubs", icon = Icons.Filled.Place) { onClose(); onOpenClubs() }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            DrawerItem("Start Match", icon = Icons.Filled.PlayArrow) { onClose(); onStartMatch() }
            DrawerItem("Create Tournament", icon = Icons.Filled.Add) { onClose(); onOpenCreateTournament() }
            DrawerItem("Register As Club", icon = Icons.Filled.Create) { onClose(); onOpenRegisterClub() }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            DrawerItem("Following", icon = Icons.Filled.FavoriteBorder) { onClose(); onOpenFollowing() }
            DrawerItem("Settings", icon = Icons.Filled.Settings) { onClose(); onOpenSettings() }
        }
    }
}

/** One tappable row in the drawer — a small leading icon (so the menu doesn't have to be "read", it can be scanned by shape) plus a label. */
@Composable
private fun DrawerItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text = label, fontWeight = FontWeight.Medium)
    }
}

/** A section title with a "সব দেখো" (see all) link on the right — reused for Matches and Tournaments. */
@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "সব দেখো »",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onSeeAll)
        )
    }
}

/** Shown instead of a card row when a section has nothing in it yet, with a button to go fix that. */
@Composable
private fun EmptySectionCard(message: String, actionLabel: String, onAction: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = PaddingValues(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** One match card in the horizontally-scrolling Matches row. Uses the shared [AppCard] look
 * (see ui/designsystem/AppCard.kt) instead of building its own Card by hand, so it automatically
 * lines up with every other card in the app that's already been switched over to it. */
@Composable
private fun MatchCard(match: HomeMatchCard, onClick: () -> Unit, onViewTournament: (() -> Unit)?, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.width(230.dp), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${match.oversLimit} ওভার",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            // A small red "LIVE" badge only shows up while the match is actually being broadcast.
            if (match.isLive) {
                Text(text = "LIVE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            } else {
                Text(text = match.status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = match.teamAName, fontWeight = FontWeight.SemiBold)
        Text(text = match.teamBName, fontWeight = FontWeight.SemiBold)
        // Only matches created as part of a tournament have somewhere to link to.
        if (onViewTournament != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "টুর্নামেন্ট দেখো",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onViewTournament)
            )
        }
    }
}

/** The dark-green gradient card summarising the signed-in user's own quick stats. Built on the
 * shared [GradientHeroCard] (see ui/designsystem/GradientHeroCard.kt) — this used to hand-roll
 * its own gradient Box, which is exactly the pattern that component was pulled out of. */
@Composable
private fun ProfileSummaryCard(profile: UserEntity, uiState: HomeUiState, onClick: () -> Unit) {
    GradientHeroCard(
        modifier = Modifier.padding(horizontal = 20.dp),
        gradientColors = listOf(PitchGreen, Color(0xFF0F4A2C)),
        onClick = onClick
    ) {
        Text(text = profile.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(label = "Matches", value = uiState.careerStats.matchesPlayed.toString())
            StatColumn(label = "Runs", value = uiState.careerStats.runs.toString())
            StatColumn(label = "Wickets", value = uiState.careerStats.wickets.toString())
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatColumn(label: String, value: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

/**
 * One tournament card in the horizontally-scrolling Tournaments row — a colourful gradient
 * "banner" up top (this app has no tournament-poster upload feature, so a gradient + a big
 * trophy icon stands in for one, the same idea [ClubCard] uses for its own initials avatar),
 * then venue, name, and overs — in that order, matching the reference app's card layout.
 */
@Composable
private fun TournamentCard(tournament: TournamentEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.width(200.dp), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.tertiary, PitchGreen))),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(36.dp))
        }
        Column(modifier = Modifier.padding(16.dp)) {
            tournament.venue?.let { Text(text = it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            Text(text = tournament.name, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(text = "${tournament.oversPerMatch} ওভার প্রতি ম্যাচ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One club row on Home's "Clubs" section — a leading initials avatar, the club's name/city, and
 * a small coloured badge showing which ball the club plays with (this app doesn't have club
 * logo photos or a "most recent match" summary line yet, unlike the fuller reference-app card
 * this is modelled on, so those two pieces are left out rather than faked). Tapping the whole
 * row opens that club's full detail page.
 */
@Composable
private fun ClubCard(club: ClubEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(PitchGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = club.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = club.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(text = club.city, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                BallTypeBadge(ballType = club.ballType)
            }
        }
    }
}

/** A tiny coloured-dot + label badge: red for a leather ball, green/yellow for a tennis ball — matching the two ball colours used everywhere else in the app (wagon wheel, scoring buttons). */
@Composable
private fun BallTypeBadge(ballType: String) {
    val isLeather = ballType == "LEATHER"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isLeather) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isLeather) "লেদার বল" else "টেনিস বল",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The static "need help?" block at the bottom of the dashboard — no backend behind this, just
 * contact info. Uses [GradientHeroCard] with the SAME colour twice (so it renders as one flat
 * brand-green block, not an actual gradient) purely to inherit the same shape/padding/motion
 * every other hero card in the app now shares, without losing its deliberately solid look. */
@Composable
private fun SupportCard() {
    GradientHeroCard(modifier = Modifier.padding(horizontal = 20.dp), gradientColors = listOf(PitchGreen, PitchGreen)) {
        Text(text = "Need help?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(text = "Mail us at support@stumpsapp.com", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
    }
}

/** A simple circular avatar made from the user's first initial — no photo upload feature exists yet, so this is the stand-in. */
@Composable
private fun InitialsAvatar(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(PitchGreen)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
