package com.mdlimonhossain.stumps.data.local.db.match

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This file describes the actual DATABASE TABLES the app uses for teams, players, matches,
 * innings, and balls. "Room" is a library that lets us write normal Kotlin data classes like
 * these and have them automatically turned into real SQLite database tables on the phone —
 * we don't have to write any raw SQL "CREATE TABLE" statements ourselves.
 *
 * Each @Entity here becomes one table. Each @PrimaryKey is the column used to uniquely identify
 * one row (like a row's "id card number" — no two rows can share the same one).
 */

/** One saved team, e.g. "Dhaka Tigers". createdByUid remembers which user made it. */
@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val logoUrl: String? = null,
    val createdByUid: String,
    val location: String? = null // e.g. "Dhaka" — the city/area this team is based in, shown on its detail page
)

/**
 * One player belonging to one team. `role` is stored as plain text (matching a PlayerRole enum
 * name). `isCaptain`/`isViceCaptain` are simple on/off flags — at most one player per team should
 * have each flag set to true at a time (TeamRepository.toggleCaptain/toggleViceCaptain enforce
 * that by clearing the flag on every other player of the same team before setting it).
 */
@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val teamId: String, // which team this player belongs to — links back to a TeamEntity's id
    val name: String,
    val role: String,
    val isCaptain: Boolean = false,
    val isViceCaptain: Boolean = false
)

/**
 * One match between two teams. Notice this table doesn't store the SCORE anywhere — that's on
 * purpose! The score is always worked out by replaying the balls (see ScoringEngine.kt). This
 * table only stores the "setup" facts about the match: who's playing, how many overs, the toss.
 */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val tournamentId: String?, // set if this match is part of a tournament, null for a standalone quick match
    val teamAId: String,
    val teamBId: String,
    val oversLimit: Int,
    val tossWinnerTeamId: String?,
    val tossDecision: String?, // "BAT" or "BOWL" — what the toss winner chose to do
    val status: String,
    val createdByUid: String,
    val createdAt: Long, // when this match was created, as a plain timestamp number
    val isLive: Boolean = false, // true while this match is being broadcast live for others to watch
    val shareCode: String? = null, // the short code viewers type in to watch this match live
    val format: String? = null // a MatchFormat enum name (e.g. "T10", "T20", "ODI") chosen at setup time, or null for old matches scored before this existed
)

/**
 * One innings of a match (a T20 match has 2 innings — one per team batting). Just like
 * MatchEntity, this only stores the SETUP facts (who opened, what's the target) — not the score.
 */
@Entity(tableName = "innings")
data class InningsEntity(
    @PrimaryKey val id: String,
    val matchId: String, // which match this innings belongs to
    val inningsNumber: Int, // 1 = first team batting, 2 = second team batting (chasing)
    val battingTeamId: String,
    val bowlingTeamId: String,
    val openingStrikerId: String, // who was on strike for the very first ball of this innings
    val openingNonStrikerId: String,
    val openingBowlerId: String,
    val targetRuns: Int? = null // only set for the 2nd innings — how many runs needed to WIN
)

/**
 * One single ball bowled. THIS is the important table — the whole scoreboard is built by
 * reading every BallEntity row for an innings, in order, and feeding them into ScoringEngine.
 * See the big comment at the top of ScoringEngine.kt for why we store data this way.
 */
@Entity(tableName = "balls")
data class BallEntity(
    @PrimaryKey val id: String,
    val inningsId: String, // which innings this ball belongs to
    val sequence: Int, // the order this ball happened in (1st ball ever bowled in this innings, 2nd, ...)
    val bowlerId: String,
    val strikerId: String,
    val nonStrikerId: String,
    val runsOffBat: Int,
    val extraType: String?, // stored as plain text here (an ExtraType enum name, or null for a normal ball) because that's what SQLite understands
    val extraRuns: Int,
    val runsRun: Int,
    val isWicket: Boolean,
    val dismissalType: String?, // same idea — a DismissalType enum name stored as text, or null
    val dismissedPlayerId: String?,
    val newBatsmanId: String?,
    val shotAngleDegrees: Int? = null, // where the shot went, for the wagon wheel chart — only set for some balls
    // Which fielder gets credit for this dismissal — only meaningful for CAUGHT (the catcher),
    // STUMPED (the keeper), and RUN_OUT (whoever threw it in). Left null for every other
    // dismissal type (BOWLED, LBW, etc.) since nobody "fields" a bowled dismissal, and for
    // every non-wicket ball. This is what makes catches/stumpings/run-outs countable at all —
    // without it we'd only ever know HOW a batsman got out, never WHO did it.
    val fielderId: String? = null
)
