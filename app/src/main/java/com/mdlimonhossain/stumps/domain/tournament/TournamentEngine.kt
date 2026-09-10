package com.mdlimonhossain.stumps.domain.tournament

/**
 * This file works out the "points table" for a tournament — who's winning, and in what order —
 * plus generates the match schedule. This is the tournament equivalent of ScoringEngine.kt:
 * it's a pure calculator that takes in match results and produces a ranked table, with no
 * database or screen involved, which makes it easy to test with plain numbers.
 */

/**
 * A summary of ONE finished match, boiled down to just the numbers the points table needs.
 * We don't need to know every ball that was bowled here — just the final scores.
 */
data class CompletedMatchResult(
    val teamAId: String,
    val teamBId: String,
    val oversLimit: Int, // how many overs each side was allowed to bat
    val teamARuns: Int,
    val teamALegalBalls: Int, // how many balls Team A actually faced (used to work out their over count)
    val teamAAllOut: Boolean, // did Team A lose all their wickets before using up their overs?
    val teamBRuns: Int,
    val teamBLegalBalls: Int,
    val teamBAllOut: Boolean
)

/**
 * One row of the points table for one team: their win/loss record, points, and the raw
 * numbers needed to work out Net Run Rate (NRR).
 */
data class TeamStanding(
    val teamId: String,
    val teamName: String,
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val tied: Int = 0,
    val points: Int = 0,
    val runsFor: Int = 0, // total runs this team has SCORED across all their matches
    val oversFor: Double = 0.0, // total overs this team has USED UP while batting
    val runsAgainst: Int = 0, // total runs this team has CONCEDED (let the other team score)
    val oversAgainst: Double = 0.0 // total overs the OTHER teams used while batting against them
) {
    /**
     * Net Run Rate = "how many runs per over does this team score, compared to how many
     * runs per over they let the other team score". A positive number is good (they score
     * faster than they concede); a negative number is bad. This is the classic tie-breaker
     * used in real cricket tournaments (like the IPL or World Cup) when two teams have the
     * same number of points.
     */
    val netRunRate: Double get() {
        val forRate = if (oversFor == 0.0) 0.0 else runsFor / oversFor
        val againstRate = if (oversAgainst == 0.0) 0.0 else runsAgainst / oversAgainst
        return forRate - againstRate
    }
}

object TournamentEngine {

    /**
     * Standard ICC NRR rule: a team that is bowled out (or otherwise doesn't use its full
     * quota, e.g. rain) is credited with having faced/bowled its FULL allotted overs for the
     * rate calculation, not just the balls actually bowled — otherwise being skittled cheaply
     * would inflate a team's own run rate.
     *
     * In plain words: imagine a team gets all out for just 50 runs in only 10 overs (out of
     * a 20-over match). If we used "50 runs / 10 overs" for their rate, that would actually
     * look pretty good (5 runs an over) even though getting bowled out cheaply is bad! So the
     * rule says: if you got all out, we pretend you used the FULL 20 overs anyway, giving a
     * fairer (lower) rate of "50 runs / 20 overs".
     */
    private fun oversForRate(legalBalls: Int, allOut: Boolean, oversLimit: Int): Double =
        if (allOut) oversLimit.toDouble() else legalBalls / 6.0

    /**
     * Builds the full points table from scratch, given the list of teams and every match
     * result played so far. Teams are ranked by points first, and Net Run Rate breaks any ties.
     */
    fun computeStandings(
        teams: List<Pair<String, String>>, // teamId to teamName
        results: List<CompletedMatchResult>
    ): List<TeamStanding> {
        // Start every team on a blank row (0 played, 0 points, etc.) before we add up results.
        val standings = teams.associate { (id, name) -> id to TeamStanding(id, name) }.toMutableMap()

        // Go through every completed match, one at a time, and update both teams' rows.
        for (r in results) {
            // Work out how many "overs" to count for each team's run-rate math (see oversForRate above).
            val aOvers = oversForRate(r.teamALegalBalls, r.teamAAllOut, r.oversLimit)
            val bOvers = oversForRate(r.teamBLegalBalls, r.teamBAllOut, r.oversLimit)

            // If we don't recognise one of the team ids, just skip this match rather than crash.
            val a = standings[r.teamAId] ?: continue
            val b = standings[r.teamBId] ?: continue

            // Add this match's runs/overs onto each team's running totals.
            // Notice: Team A's "runsAgainst" is Team B's runs, and vice versa — that's just
            // what "against" means (the runs the OTHER team scored against you).
            val updatedA = a.copy(
                played = a.played + 1,
                runsFor = a.runsFor + r.teamARuns,
                oversFor = a.oversFor + aOvers,
                runsAgainst = a.runsAgainst + r.teamBRuns,
                oversAgainst = a.oversAgainst + bOvers
            )
            val updatedB = b.copy(
                played = b.played + 1,
                runsFor = b.runsFor + r.teamBRuns,
                oversFor = b.oversFor + bOvers,
                runsAgainst = b.runsAgainst + r.teamARuns,
                oversAgainst = b.oversAgainst + aOvers
            )

            // Now work out win/loss/tie and hand out points: a win is worth 2 points, a tie is
            // worth 1 point each, and a loss is worth 0 points.
            standings[r.teamAId] = when {
                r.teamARuns > r.teamBRuns -> updatedA.copy(won = updatedA.won + 1, points = updatedA.points + 2)
                r.teamARuns < r.teamBRuns -> updatedA.copy(lost = updatedA.lost + 1)
                else -> updatedA.copy(tied = updatedA.tied + 1, points = updatedA.points + 1)
            }
            standings[r.teamBId] = when {
                r.teamBRuns > r.teamARuns -> updatedB.copy(won = updatedB.won + 1, points = updatedB.points + 2)
                r.teamBRuns < r.teamARuns -> updatedB.copy(lost = updatedB.lost + 1)
                else -> updatedB.copy(tied = updatedB.tied + 1, points = updatedB.points + 1)
            }
        }

        // Sort the final table: most points first, and if two teams are tied on points,
        // whoever has the better Net Run Rate goes higher.
        return standings.values.sortedWith(
            compareByDescending<TeamStanding> { it.points }.thenByDescending { it.netRunRate }
        )
    }

    /**
     * Builds a "round robin" schedule: every team plays every OTHER team exactly once.
     * For example, with teams [A, B, C] this produces the pairs (A vs B), (A vs C), (B vs C) —
     * 3 matches total. This is a classic little bit of math: for N teams you always get
     * N × (N-1) / 2 matches.
     */
    fun generateRoundRobinFixtures(teamIds: List<String>): List<Pair<String, String>> {
        val fixtures = mutableListOf<Pair<String, String>>()
        // For every team...
        for (i in teamIds.indices) {
            // ...pair it against every team that comes AFTER it in the list. Starting from
            // "i + 1" instead of 0 is what stops us creating duplicate matches (like both
            // "A vs B" AND "B vs A") or a team playing itself.
            for (j in i + 1 until teamIds.size) {
                fixtures.add(teamIds[i] to teamIds[j])
            }
        }
        return fixtures
    }
}
