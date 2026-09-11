package com.mdlimonhossain.stumps.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.ui.auth.AuthScreen
import com.mdlimonhossain.stumps.ui.auth.AuthViewModel
import com.mdlimonhossain.stumps.ui.club.ClubDetailScreen
import com.mdlimonhossain.stumps.ui.club.ClubScreen
import com.mdlimonhossain.stumps.ui.history.MatchHistoryScreen
import com.mdlimonhossain.stumps.ui.home.HomeScreen
import com.mdlimonhossain.stumps.ui.live.WatchLiveScreen
import com.mdlimonhossain.stumps.ui.match.MatchCentreScreen
import com.mdlimonhossain.stumps.ui.match.MatchFlowScreen
import com.mdlimonhossain.stumps.ui.navigation.Destinations
import com.mdlimonhossain.stumps.ui.navigation.StumpsBottomNavBar
import com.mdlimonhossain.stumps.ui.navigation.sharedAxisEnter
import com.mdlimonhossain.stumps.ui.navigation.sharedAxisExit
import com.mdlimonhossain.stumps.ui.navigation.sharedAxisPopEnter
import com.mdlimonhossain.stumps.ui.navigation.sharedAxisPopExit
import com.mdlimonhossain.stumps.ui.news.NewsScreen
import com.mdlimonhossain.stumps.ui.onboarding.OnboardingScreen
import com.mdlimonhossain.stumps.ui.onboarding.WelcomeScreen
import com.mdlimonhossain.stumps.ui.profile.ProfileScreen
import com.mdlimonhossain.stumps.ui.profile.ProfileSetupScreen
import com.mdlimonhossain.stumps.ui.search.SearchScreen
import com.mdlimonhossain.stumps.ui.settings.SettingsScreen
import com.mdlimonhossain.stumps.ui.settings.ThisDeviceScreen
import com.mdlimonhossain.stumps.ui.social.FollowingScreen
import com.mdlimonhossain.stumps.ui.team.TeamDetailScreen
import com.mdlimonhossain.stumps.ui.team.TeamManagementScreen
import com.mdlimonhossain.stumps.ui.tournament.TournamentDetailScreen
import com.mdlimonhossain.stumps.ui.tournament.TournamentListScreen
import com.mdlimonhossain.stumps.ui.tournament.TournamentPreviewScreen
import kotlinx.coroutines.launch

/**
 * This is the "root" of the whole app's UI — it doesn't draw much itself, but it decides WHICH
 * screen should be showing at any given moment, based on the user's current situation: have
 * they seen onboarding yet? Are they logged in? Have they finished their profile?
 *
 * The very first part of this decision (spinner -> Welcome -> Onboarding -> Auth -> profile
 * setup) still uses a plain Kotlin `when` block, on purpose: it's a one-way, one-time sequence
 * a person walks through exactly once, not something they should ever be able to "go back into"
 * with the system back button — so a full navigation graph would just be extra complexity for
 * no real benefit there.
 *
 * Once someone IS fully logged in, though, control hands off to [MainAppNavHost] below, which
 * uses a real `NavController`/`NavHost` (from the `navigation-compose` library) instead of a
 * manual `when` block. That's what gives the main app proper Android back-button support and
 * smooth animated transitions between screens, instead of the old instant hard-cut screen swaps.
 */
@Composable
fun StumpsApp() {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(app.authRepository, app.userRepository)
    )
    val uiState by authViewModel.uiState.collectAsState()
    val firebaseUser by app.userRepository.currentUser.collectAsState(initial = null)
    val cachedProfile by app.userRepository.cachedProfile.collectAsState(initial = null)
    val hasSeenOnboarding by app.onboardingStore.hasSeenOnboarding.collectAsState(initial = null)
    var watchingLiveWithoutLogin by remember { mutableStateOf(false) }

    when {
        // hasSeenOnboarding starts as null for a brief moment while we're still reading it
        // from local storage — show a spinner rather than guessing which screen to show.
        hasSeenOnboarding == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        // First-ever launch: show the branded welcome screen, then the feature-tour onboarding.
        hasSeenOnboarding == false -> {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var showWelcome by remember { mutableStateOf(true) }
            if (showWelcome) {
                WelcomeScreen(onGetStarted = { showWelcome = false })
            } else {
                OnboardingScreen(onDone = { scope.launch { app.onboardingStore.markSeen() } })
            }
        }

        // A signed-out visitor chose "just let me watch a live match" — skip login entirely.
        watchingLiveWithoutLogin -> WatchLiveScreen(onBack = { watchingLiveWithoutLogin = false })

        // Not logged in (and not just watching live) — show the login/signup screen.
        firebaseUser == null -> AuthScreen(
            uiState = uiState,
            onSignUpWithEmail = authViewModel::signUpWithEmail,
            onSignInWithEmail = authViewModel::signInWithEmail,
            onSignInWithGoogleIdToken = authViewModel::signInWithGoogle,
            onGoogleSignInFailed = authViewModel::onGoogleSignInFailed,
            onCheckEmailVerifiedAndContinue = authViewModel::checkEmailVerifiedAndContinue,
            onResendVerificationEmail = authViewModel::resendVerificationEmail,
            onWatchLive = { watchingLiveWithoutLogin = true }
        )

        // Logged in, but the local profile hasn't loaded from the database yet.
        cachedProfile == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        // Logged in, profile loaded, but it's still blank (a brand new account) — make them
        // fill in their name/role before letting them into the main app.
        cachedProfile?.name.isNullOrBlank() -> ProfileSetupScreen(
            onComplete = { name, role ->
                authViewModel.completeProfile(firebaseUser!!.uid, name, role, firebaseUser!!.photoUrl)
            }
        )

        // Everything checked out — show the real app.
        else -> MainAppNavHost(
            currentUid = firebaseUser!!.uid,
            cachedProfile = cachedProfile,
            onSignOut = authViewModel::signOut,
            onCompleteProfileEdit = { name, role ->
                authViewModel.completeProfile(firebaseUser!!.uid, name, role, firebaseUser!!.photoUrl)
            }
        )
    }
}

/**
 * The main, logged-in part of the app: a bottom navigation bar for the four main areas (Home,
 * Matches, Tournaments, Profile) plus a `NavHost` that swaps screens in and out with a smooth
 * sliding/fading animation (see ui/navigation/NavAnimations.kt) any time the user navigates,
 * instead of the old instant hard cut.
 *
 * Every screen's own parameters (onBack, onOpenX, etc.) are exactly the same as before this
 * migration — only WHAT WIRES those callbacks changed, from setting a `screen` variable to
 * calling `navController.navigate(...)`.
 */
@Composable
private fun MainAppNavHost(
    currentUid: String,
    cachedProfile: com.mdlimonhossain.stumps.data.local.db.UserEntity?,
    onSignOut: () -> Unit,
    onCompleteProfileEdit: (String, PlayerRole) -> Unit
) {
    val navController = rememberNavController()

    // Re-reads itself automatically every time the user navigates anywhere, which is what lets
    // the Scaffold below decide, live, whether the bottom bar should currently be showing.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isOnTopLevelTab = currentDestination?.hierarchy?.any {
        it.route == Destinations.Home || it.route == Destinations.HistoryRoute ||
            it.route == Destinations.TournamentsRoute || it.route == Destinations.Profile
    } == true

    // Every screen EXCEPT Home already has somewhere sensible for the system back button/
    // gesture to go — NavHost wires that up automatically. Home is different: it's the very
    // bottom of the whole back stack, so a single accidental back press there used to close
    // the entire app instantly with no warning at all. Requiring a SECOND press within 2
    // seconds (with a short toast explaining why, on the first press) is the standard, expected
    // pattern Android apps use for exactly this screen.
    val context = LocalContext.current
    var lastBackPressAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = currentDestination?.route == Destinations.Home) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt < 2000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "আবার ব্যাক চাপো, অ্যাপ থেকে বের হতে", Toast.LENGTH_SHORT).show()
        }
    }

    // Reused by every "tab root" screen's own back button (History/Tournaments/Profile), so
    // tapping it always lands back on Home — exactly like it did before this migration —
    // regardless of whether that screen was reached by tapping a bottom-nav tab or a drawer
    // item.
    //
    // This used to use a fancier popUpTo(...){saveState=true} + restoreState=true pattern (the
    // same one the bottom bar's own tab-switching still explains in its own comment below) —
    // but that combination turned out to silently do NOTHING when leaving a screen whose route
    // has an OPTIONAL argument, like History's "history?matchId={matchId}" (the "Matches" tab).
    // Home is always the very first, permanent entry at the bottom of this NavHost's back stack
    // (it's the `startDestination`, and nothing else in this file ever pops it off) — so simply
    // popping the stack back down to it is both simpler AND actually reliable.
    val goHome: () -> Unit = {
        navController.popBackStack(Destinations.Home, inclusive = false)
    }

    Scaffold(
        // MainActivity already wraps the WHOLE app (this screen included) in its own outer
        // Scaffold, which reserves space for the status bar/system nav bar once already. If
        // this INNER Scaffold also reserved that same space (its normal default behaviour),
        // every screen here would get an extra, unwanted gap of empty space at both the top
        // and bottom — so this one is told to reserve nothing for system bars itself, and
        // only reserve space for its own bottomBar (which Scaffold always does regardless of
        // this setting).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (isOnTopLevelTab) StumpsBottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.Home,
            modifier = Modifier.padding(innerPadding),
            enterTransition = sharedAxisEnter,
            exitTransition = sharedAxisExit,
            popEnterTransition = sharedAxisPopEnter,
            popExitTransition = sharedAxisPopExit
        ) {
            composable(Destinations.Home) {
                HomeScreen(
                    profile = cachedProfile,
                    onStartMatch = { navController.navigate(Destinations.MatchFlow) },
                    onOpenMatch = { matchId -> navController.navigate(Destinations.history(matchId)) },
                    onOpenHistory = { navController.navigate(Destinations.history()) },
                    onOpenTeams = { navController.navigate(Destinations.Teams) },
                    onOpenTournaments = { navController.navigate(Destinations.tournaments()) },
                    onOpenTournamentDetail = { tournamentId -> navController.navigate(Destinations.tournamentDetail(tournamentId)) },
                    onWatchLive = { navController.navigate(Destinations.WatchLive) },
                    onOpenProfile = { navController.navigate(Destinations.Profile) },
                    onOpenClubs = { navController.navigate(Destinations.clubs()) },
                    onOpenClubDetail = { clubId -> navController.navigate(Destinations.clubPreview(clubId)) },
                    onOpenCreateTournament = { navController.navigate(Destinations.tournaments(startWithCreateForm = true)) },
                    onOpenRegisterClub = { navController.navigate(Destinations.clubs(startWithRegisterForm = true)) },
                    onOpenFollowing = { navController.navigate(Destinations.Following) },
                    onOpenSettings = { navController.navigate(Destinations.Settings) },
                    onOpenSearch = { navController.navigate(Destinations.Search) },
                    onOpenNews = { navController.navigate(Destinations.News) }
                )
            }

            composable(Destinations.WatchLive) {
                WatchLiveScreen(onBack = { navController.popBackStack() })
            }

            composable(Destinations.MatchFlow) {
                MatchFlowScreen(currentUid = currentUid, onFinished = { navController.popBackStack() })
            }

            composable(route = Destinations.HistoryRoute, arguments = Destinations.historyArgs) { backStackEntry ->
                MatchHistoryScreen(
                    uid = currentUid,
                    onBack = goHome,
                    initialMatchId = backStackEntry.arguments?.getString("matchId")
                )
            }

            composable(Destinations.Teams) {
                TeamManagementScreen(
                    uid = currentUid,
                    onBack = { navController.popBackStack() },
                    onOpenTeam = { teamId -> navController.navigate(Destinations.teamDetail(teamId)) }
                )
            }

            composable(route = Destinations.TeamDetailRoute, arguments = Destinations.teamDetailArgs) { backStackEntry ->
                val teamId = backStackEntry.arguments?.getString("teamId") ?: return@composable
                TeamDetailScreen(
                    teamId = teamId,
                    viewerUid = currentUid,
                    onBack = { navController.popBackStack() },
                    onOpenMatch = { matchId -> navController.navigate(Destinations.history(matchId)) },
                    onOpenTournament = { tournamentId -> navController.navigate(Destinations.tournamentDetail(tournamentId)) }
                )
            }

            composable(route = Destinations.TournamentsRoute, arguments = Destinations.tournamentsArgs) { backStackEntry ->
                TournamentListScreen(
                    uid = currentUid,
                    onOpenTournament = { id -> navController.navigate(Destinations.tournamentDetail(id)) },
                    onBack = goHome,
                    onOpenCreateTeam = { navController.navigate(Destinations.Teams) },
                    startWithCreateForm = backStackEntry.arguments?.getBoolean("startWithCreateForm") ?: false
                )
            }

            composable(route = Destinations.TournamentDetailRoute, arguments = Destinations.tournamentDetailArgs) { backStackEntry ->
                val tournamentId = backStackEntry.arguments?.getString("tournamentId") ?: return@composable
                TournamentDetailScreen(
                    tournamentId = tournamentId,
                    organizerUid = currentUid,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Destinations.Profile) {
                ProfileScreen(
                    profile = cachedProfile,
                    onBack = goHome,
                    onSignOut = onSignOut,
                    onStartMatch = { navController.navigate(Destinations.MatchFlow) },
                    onOpenTeams = { navController.navigate(Destinations.Teams) },
                    onOpenCreateTournament = { navController.navigate(Destinations.tournaments(startWithCreateForm = true)) },
                    onOpenRegisterClub = { navController.navigate(Destinations.clubs(startWithRegisterForm = true)) }
                )
            }

            composable(route = Destinations.ClubsRoute, arguments = Destinations.clubsArgs) { backStackEntry ->
                ClubScreen(
                    uid = currentUid,
                    onBack = { navController.popBackStack() },
                    startWithRegisterForm = backStackEntry.arguments?.getBoolean("startWithRegisterForm") ?: false
                )
            }

            composable(Destinations.Following) {
                FollowingScreen(uid = currentUid, onBack = { navController.popBackStack() })
            }

            composable(Destinations.Search) {
                SearchScreen(
                    uid = currentUid,
                    onBack = { navController.popBackStack() },
                    onOpenClub = { id -> navController.navigate(Destinations.clubPreview(id)) },
                    onOpenTournament = { id -> navController.navigate(Destinations.tournamentPreview(id)) },
                    onOpenMatch = { id -> navController.navigate(Destinations.matchPreview(id)) }
                )
            }

            composable(route = Destinations.MatchPreviewRoute, arguments = Destinations.matchPreviewArgs) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable
                MatchCentreScreen(matchId = matchId, onBack = { navController.popBackStack() })
            }

            composable(route = Destinations.ClubPreviewRoute, arguments = Destinations.clubPreviewArgs) { backStackEntry ->
                val clubId = backStackEntry.arguments?.getString("clubId") ?: return@composable
                ClubDetailScreen(clubId = clubId, viewerUid = currentUid, onBack = { navController.popBackStack() })
            }

            composable(route = Destinations.TournamentPreviewRoute, arguments = Destinations.tournamentPreviewArgs) { backStackEntry ->
                val tournamentId = backStackEntry.arguments?.getString("tournamentId") ?: return@composable
                TournamentPreviewScreen(tournamentId = tournamentId, viewerUid = currentUid, onBack = { navController.popBackStack() })
            }

            composable(Destinations.News) {
                NewsScreen(onBack = { navController.popBackStack() })
            }

            composable(Destinations.Settings) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfileEdit = { navController.navigate(Destinations.ProfileEdit) },
                    onOpenManageDevices = { navController.navigate(Destinations.ManageDevices) },
                    onSignOut = onSignOut
                )
            }

            composable(Destinations.ManageDevices) {
                ThisDeviceScreen(onBack = { navController.popBackStack() }, onSignOut = onSignOut)
            }

            composable(Destinations.ProfileEdit) {
                ProfileSetupScreen(
                    initialName = cachedProfile?.name ?: "",
                    initialRole = cachedProfile?.role?.let { runCatching { PlayerRole.valueOf(it) }.getOrNull() } ?: PlayerRole.BATSMAN,
                    onComplete = { name, role ->
                        onCompleteProfileEdit(name, role)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
