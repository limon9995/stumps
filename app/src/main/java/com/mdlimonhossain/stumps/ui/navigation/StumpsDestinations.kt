package com.mdlimonhossain.stumps.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Every screen ("destination") the main, logged-in part of the app can navigate to, all
 * gathered together in ONE place.
 *
 * Before this file existed, the app just kept a single `screen` variable in memory and used one
 * giant `when` block to swap composables in and out — that worked, but it meant Android's
 * normal "system back button/gesture" and any kind of smooth animation between screens couldn't
 * hook into it at all; every screen change was an instant, jarring hard cut. Switching to a real
 * `NavController`/`NavHost` (from the `navigation-compose` library, which was already added to
 * this project's dependencies but never actually used until now) fixes both of those things —
 * and this file is where we describe, for each screen, what its "route" (a bit like a URL, e.g.
 * "tournament_detail/abc123") looks like, and what pieces of information ("arguments") it needs.
 *
 * A route with a `{placeholder}` in it means "this part gets filled in with a real value when
 * we actually navigate somewhere" — e.g. the pattern "tournament_detail/{tournamentId}" becomes
 * the real, concrete route "tournament_detail/abc123" once we navigate to tournament "abc123".
 */
object Destinations {
    // --- Simple destinations that never need any extra information ----------------------
    const val Home = "home"
    const val MatchFlow = "match_flow"
    const val WatchLive = "watch_live"
    const val Teams = "teams"
    const val Following = "following"
    const val Search = "search"
    const val Settings = "settings"
    const val ProfileEdit = "profile_edit"
    const val News = "news"
    const val ManageDevices = "manage_devices"
    const val Profile = "profile"

    // --- Destinations with an OPTIONAL argument (a "?name=value" style, like a website URL) ---

    // History (a.k.a. the bottom nav bar's "Matches" tab) can optionally be told to jump
    // straight into one particular match's scorecard — used when it's opened by tapping a
    // match card elsewhere in the app, instead of always showing the full match list first.
    const val HistoryRoute = "history?matchId={matchId}"
    fun history(matchId: String? = null) = if (matchId != null) "history?matchId=$matchId" else "history"
    val historyArgs = listOf(navArgument("matchId") { type = NavType.StringType; nullable = true; defaultValue = null })

    // Tournaments can optionally be told to open straight into the "create a new tournament"
    // form — used by the drawer's "Create Tournament" shortcut.
    const val TournamentsRoute = "tournaments?startWithCreateForm={startWithCreateForm}"
    fun tournaments(startWithCreateForm: Boolean = false) = "tournaments?startWithCreateForm=$startWithCreateForm"
    val tournamentsArgs = listOf(navArgument("startWithCreateForm") { type = NavType.BoolType; defaultValue = false })

    // Clubs can similarly be told to jump straight into the "register a new club" form.
    const val ClubsRoute = "clubs?startWithRegisterForm={startWithRegisterForm}"
    fun clubs(startWithRegisterForm: Boolean = false) = "clubs?startWithRegisterForm=$startWithRegisterForm"
    val clubsArgs = listOf(navArgument("startWithRegisterForm") { type = NavType.BoolType; defaultValue = false })

    // --- Destinations that always need a REQUIRED argument (a "/value" style path segment) ---

    const val TournamentDetailRoute = "tournament_detail/{tournamentId}"
    fun tournamentDetail(tournamentId: String) = "tournament_detail/$tournamentId"
    val tournamentDetailArgs = listOf(navArgument("tournamentId") { type = NavType.StringType })

    const val TeamDetailRoute = "team_detail/{teamId}"
    fun teamDetail(teamId: String) = "team_detail/$teamId"
    val teamDetailArgs = listOf(navArgument("teamId") { type = NavType.StringType })

    // A saved team's own player, reached by tapping "View Profile" on that team's Players tab.
    const val PlayerProfileRoute = "player_profile/{playerId}"
    fun playerProfile(playerId: String) = "player_profile/$playerId"
    val playerProfileArgs = listOf(navArgument("playerId") { type = NavType.StringType })

    // These three "preview" screens show ANOTHER user's club/tournament/match, found via
    // Search — kept as their own separate destinations (rather than reusing Clubs/Tournaments/
    // History above) because those three always show the SIGNED-IN user's own things.
    const val ClubPreviewRoute = "club_preview/{clubId}"
    fun clubPreview(clubId: String) = "club_preview/$clubId"
    val clubPreviewArgs = listOf(navArgument("clubId") { type = NavType.StringType })

    const val TournamentPreviewRoute = "tournament_preview/{tournamentId}"
    fun tournamentPreview(tournamentId: String) = "tournament_preview/$tournamentId"
    val tournamentPreviewArgs = listOf(navArgument("tournamentId") { type = NavType.StringType })

    const val MatchPreviewRoute = "match_preview/{matchId}"
    fun matchPreview(matchId: String) = "match_preview/$matchId"
    val matchPreviewArgs = listOf(navArgument("matchId") { type = NavType.StringType })
}
