package com.mdlimonhossain.stumps.domain.model

import java.time.Instant

/**
 * This file has two very different kinds of things in it:
 *
 * 1. The five ENUMS below marked "actively used" ARE used all over the app (in the scoring
 *    engine, the database, and the screens) — they're the small fixed lists of options like
 *    "what kind of extra was that ball" or "what role does this player play".
 *
 * 2. The DATA CLASSES further down (User, Player, Team, Ball, Over, Innings, Match, Tournament)
 *    were an early sketch of the app's data model from right at the start of the project — but
 *    as the app grew, we ended up storing data directly as Room database entities instead (look
 *    in data/local/db/ for UserEntity, TeamEntity, MatchEntity, BallEntity, etc. — those are the
 *    REAL, currently-used versions). These data classes are left over from that early sketch and
 *    aren't actually used by the running app any more, other than TournamentFormat and
 *    MatchStatus below which were never wired in either. They're kept here rather than deleted
 *    in case they're useful again later, but don't be confused if you don't see them called
 *    from anywhere else in the code.
 */

// ---- Actively used enums (a fixed list of allowed values, like a multiple-choice menu) ----

/** What role a player mainly plays — shown when setting up a profile or a saved team. */
enum class PlayerRole { BATSMAN, BOWLER, ALL_ROUNDER, WICKET_KEEPER }

/** The different ways a batsman can get out in cricket. */
enum class DismissalType {
    BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED, HIT_WICKET, RETIRED_HURT, OTHER
}

/** The different kinds of "extra" runs that aren't scored directly off the bat. */
enum class ExtraType { WIDE, NO_BALL, BYE, LEG_BYE, PENALTY }

/**
 * Which "shape" of match this is — chosen once at match setup time and saved on MatchEntity, so
 * a player's career Statistics tab can break their numbers down by format (the reference app's
 * T10/T20/Club/OD columns) instead of lumping every match together. This is separate from
 * `oversLimit` (the exact number of overs) — two different organizers might both run a
 * "20-over" match but call it a "T20" or a "Club" match depending on how formal it is, so we
 * ask directly rather than guessing purely from the overs count.
 */
enum class MatchFormat { T10, T20, ODI, CLUB, CUSTOM }

// ---- Not currently used anywhere — see the big comment above ----

enum class TournamentFormat { ROUND_ROBIN, KNOCKOUT, LEAGUE_PLAYOFF }

enum class MatchStatus { SCHEDULED, LIVE, COMPLETED, ABANDONED }

data class User(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val createdAt: Instant = Instant.now()
)

data class Player(
    val id: String,
    val userId: String? = null,
    val name: String,
    val role: PlayerRole
)

data class Team(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val playerIds: List<String> = emptyList()
)

data class Ball(
    val id: String,
    val overId: String,
    val batsmanId: String,
    val bowlerId: String,
    val runs: Int,
    val isWicket: Boolean = false,
    val dismissalType: DismissalType? = null,
    val extraType: ExtraType? = null,
    val extraRuns: Int = 0
)

data class Over(
    val id: String,
    val inningsId: String,
    val overNumber: Int,
    val bowlerId: String,
    val ballIds: List<String> = emptyList()
)

data class Innings(
    val id: String,
    val matchId: String,
    val battingTeamId: String,
    val bowlingTeamId: String,
    val overIds: List<String> = emptyList(),
    val totalRuns: Int = 0,
    val totalWickets: Int = 0
)

data class Match(
    val id: String,
    val tournamentId: String? = null,
    val teamAId: String,
    val teamBId: String,
    val oversLimit: Int,
    val tossWinnerId: String? = null,
    val tossDecision: String? = null,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val inningsIds: List<String> = emptyList()
)

data class Tournament(
    val id: String,
    val name: String,
    val format: TournamentFormat,
    val teamIds: List<String> = emptyList(),
    val organizerUserId: String,
    val venue: String? = null,
    val createdAt: Instant = Instant.now()
)
