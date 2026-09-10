package com.mdlimonhossain.stumps.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mdlimonhossain.stumps.R

/**
 * One entry in the bottom navigation bar: what it's called, which icon to show, which "pattern"
 * route to compare the current screen against (to know if this tab is the selected one), and
 * which real route to actually navigate to when it's tapped.
 */
private data class BottomNavTab(
    val label: String,
    val patternRoute: String,
    val navigateRoute: String,
    val useStumpsIcon: Boolean = false
)

// The FOUR main areas always reachable straight from the bottom bar. Everything else the app
// can show (Settings, Teams, Following, Search, previews, and so on) is reached by tapping
// further into one of these four, or through the drawer — keeping the bottom bar itself short
// and uncluttered, the way a modern app's bottom bar usually is.
private val bottomNavTabs = listOf(
    BottomNavTab(label = "Home", patternRoute = Destinations.Home, navigateRoute = Destinations.Home),
    BottomNavTab(
        label = "Matches",
        patternRoute = Destinations.HistoryRoute,
        navigateRoute = Destinations.history(),
        useStumpsIcon = true
    ),
    BottomNavTab(
        label = "Tournaments",
        patternRoute = Destinations.TournamentsRoute,
        navigateRoute = Destinations.tournaments()
    ),
    BottomNavTab(label = "Profile", patternRoute = Destinations.Profile, navigateRoute = Destinations.Profile)
)

/**
 * The row of tabs pinned to the bottom of the screen for quickly switching between the app's
 * four main areas. Material3's `NavigationBar`/`NavigationBarItem` already draw a soft, animated
 * "pill" behind whichever tab is currently selected and smoothly fade the icon/label colour in
 * and out on their own — we don't have to write any of that animation ourselves, we just need
 * to tell each item whether IT is the selected one right now.
 */
@Composable
fun StumpsBottomNavBar(navController: NavHostController) {
    // currentBackStackEntryAsState() gives us a little piece of state that automatically
    // updates itself every time the user navigates anywhere — that's what lets this bar redraw
    // itself (with the new tab highlighted) the instant the current screen changes.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        bottomNavTabs.forEach { tab ->
            // `hierarchy` includes the current screen AND all of the "parent" graphs it lives
            // inside — comparing against that (rather than an exact route string match) means a
            // tab still shows as selected even when the real, resolved route has extra
            // "?matchId=..." style details tacked onto the end of it.
            val selected = currentDestination?.hierarchy?.any { it.route == tab.patternRoute } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        // This used to try to be clever with popUpTo(...){saveState=true} +
                        // restoreState=true, so switching back to a previously-visited tab would
                        // return you to wherever you'd scrolled to — but that combination turned
                        // out to silently do nothing at all when leaving a screen whose route has
                        // an OPTIONAL argument (the "Matches" tab's `history?matchId={matchId}`),
                        // which made the whole bottom bar look broken from that screen. Home is
                        // always the very first, permanent entry at the bottom of the back stack
                        // (nothing in this app ever pops it off), so popping back down to it —
                        // then pushing the target tab fresh on top, if it isn't Home itself — is
                        // simpler and actually reliable, at the small cost of tabs no longer
                        // remembering their scroll position between visits.
                        navController.popBackStack(Destinations.Home, inclusive = false)
                        if (tab.navigateRoute != Destinations.Home) {
                            navController.navigate(tab.navigateRoute) { launchSingleTop = true }
                        }
                    }
                },
                icon = {
                    if (tab.useStumpsIcon) {
                        // No standard icon library has a ready-made "cricket" icon, so the
                        // Matches tab uses our own small hand-drawn one instead (see
                        // res/drawable/ic_stumps.xml).
                        Icon(painter = painterResource(R.drawable.ic_stumps), contentDescription = tab.label)
                    } else {
                        Icon(imageVector = tabIcon(tab.patternRoute), contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label) },
                alwaysShowLabel = true
            )
        }
    }
}

private fun tabIcon(patternRoute: String) = when (patternRoute) {
    Destinations.Home -> Icons.Filled.Home
    Destinations.TournamentsRoute -> Icons.Filled.Star // core's icon set has no trophy icon — a star reads as "featured/achievement" instead
    Destinations.Profile -> Icons.Filled.Person
    else -> Icons.Filled.Home
}
