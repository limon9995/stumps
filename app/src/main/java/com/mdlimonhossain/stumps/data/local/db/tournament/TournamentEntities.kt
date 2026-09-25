package com.mdlimonhossain.stumps.data.local.db.tournament

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database tables for the tournament feature. Same idea as MatchEntities.kt: each @Entity
 * class here is one real database table, automatically created for us by Room.
 */

/**
 * One tournament, e.g. "Test Premier League" with 20-over matches at Mirpur.
 * `clubName`/`city`/`ballType` mirror ClubEntity's own fields — when the organizer has a
 * registered club, the create-tournament form pre-fills these from it (just like the reference
 * app), but they're kept as their own plain text/String columns here rather than a foreign key,
 * since a tournament should keep showing the same club/city/ball-type info even if the
 * organizer's club details change later. `season`/`startDate`/`endDate` are all optional —
 * older tournaments created before this pass simply won't have them set.
 */
@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val format: String, // ROUND_ROBIN, KNOCKOUT
    val oversPerMatch: Int,
    val venue: String?,
    val organizerUid: String, // the user who created and manages this tournament
    val createdAt: Long,
    val clubName: String? = null,
    val city: String? = null,
    val season: String? = null, // e.g. "2025-26" or "2026" — a free-form label, not a real date
    val startDate: Long? = null,
    val endDate: Long? = null,
    val ballType: String? = null // "LEATHER" or "TENNIS", same convention as ClubEntity.ballType
)

/**
 * A team's entry into a specific tournament. We deliberately copy the team's name into
 * `teamName` at the moment they join, rather than always looking it up fresh — this keeps the
 * tournament's records stable even if someone renames the saved team later, and it's also how
 * TournamentRepository matches up match results back to the right tournament team (see the
 * comment in TournamentRepository.computeStandings for why that matters).
 */
@Entity(tableName = "tournament_teams")
data class TournamentTeamEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val teamId: String, // links back to the real saved TeamEntity
    val teamName: String
)

/**
 * One scheduled match within a tournament — "Team A vs Team B" in a given round.
 *
 * `stage` says WHICH PART of the tournament this match belongs to (see [FixtureStage]):
 * the league (everyone plays everyone), a semi-final, or the final. Every fixture made before
 * knockouts existed is simply a LEAGUE match, which is why that's the default.
 *
 * For knockout matches (semi-final / final), Team A is always the HIGHER-ranked team from the
 * league table. That matters if a knockout match is tied: there's no super over in this app,
 * so the higher-ranked team (Team A) goes through — see TournamentEngine.knockoutWinner.
 */
@Entity(tableName = "tournament_fixtures")
data class TournamentFixtureEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val round: Int,
    val teamAId: String,
    val teamBId: String,
    val matchId: String? = null, // starts out null (not played yet); set once this fixture's match has been created/scored
    val stage: String = FixtureStage.LEAGUE
)

/**
 * The three parts ("stages") a tournament goes through, in order:
 *  1. LEAGUE — every team plays every other team once; results make the points table.
 *  2. SEMI_FINAL — the top 4 of the table play: 1st vs 4th, and 2nd vs 3rd.
 *  3. FINAL — the two semi-final winners play; whoever wins is the champion.
 * (With only 2 or 3 teams there are no semi-finals — the top 2 go straight to the final.)
 *
 * These are plain Strings (not a Kotlin enum) so Room can store them in the database directly,
 * the same way ballType is stored as "LEATHER"/"TENNIS" elsewhere.
 */
object FixtureStage {
    const val LEAGUE = "LEAGUE"
    const val SEMI_FINAL = "SEMI_FINAL"
    const val FINAL = "FINAL"

    /** The Bangla name shown on screen for each stage. */
    fun label(stage: String): String = when (stage) {
        SEMI_FINAL -> "সেমিফাইনাল"
        FINAL -> "ফাইনাল"
        else -> "লিগ পর্ব"
    }
}
