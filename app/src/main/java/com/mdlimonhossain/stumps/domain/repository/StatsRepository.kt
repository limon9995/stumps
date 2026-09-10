package com.mdlimonhossain.stumps.domain.repository

import com.mdlimonhossain.stumps.data.local.db.match.MatchDao
import com.mdlimonhossain.stumps.domain.model.MatchFormat
import com.mdlimonhossain.stumps.domain.scoring.BatsmanFigures
import com.mdlimonhossain.stumps.domain.scoring.BowlerFigures
import com.mdlimonhossain.stumps.domain.scoring.FieldingFigures

/**
 * A player's whole career record, added up across EVERY match they've played (not just one
 * match). This is the kind of stats you'd see on a player's profile page: career batting
 * average, strike rate, total wickets, etc.
 */
data class CareerStats(
    val matchesPlayed: Int = 0,
    val inningsBatted: Int = 0, // how many separate innings this player actually faced a ball in
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val timesOut: Int = 0,
    val notOuts: Int = 0, // innings where they batted but the innings ended before they got out
    val fours: Int = 0,
    val sixes: Int = 0,
    val highScore: Int = 0, // the most runs scored in any single innings
    val hundreds: Int = 0, // innings with 100+ runs
    val fifties: Int = 0, // innings with 50-99 runs
    val thirties: Int = 0, // innings with 30-49 runs
    val ducks: Int = 0, // innings where they got out for 0 runs
    val inningsBowled: Int = 0,
    val wickets: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    // The best SINGLE innings bowling figures, e.g. "3 wickets for 24 runs" — kept as separate
    // numbers rather than one string so the UI can format it however it likes.
    val bestBowlingWickets: Int = 0,
    val bestBowlingRuns: Int = 0,
    val catches: Int = 0,
    val stumpings: Int = 0,
    val runOuts: Int = 0
) {
    // Batting average = total runs divided by how many times you got out. If you've never been
    // out, cricket convention is to just show your total runs (can't divide by zero!).
    val battingAverage: Double get() = if (timesOut == 0) runs.toDouble() else runs.toDouble() / timesOut
    // Strike rate = runs scored per 100 balls faced — a measure of how fast you score.
    val strikeRate: Double get() = if (ballsFaced == 0) 0.0 else (runs * 100.0) / ballsFaced
    // Economy = average runs conceded per over bowled — a measure of how tight a bowler is.
    val economy: Double get() = if (ballsBowled == 0) 0.0 else (runsConceded * 6.0) / ballsBowled
    // Bowling average = runs conceded per wicket taken — lower is better for a bowler.
    val bowlingAverage: Double get() = if (wickets == 0) 0.0 else runsConceded.toDouble() / wickets
    // The usual cricket shorthand for best bowling figures, e.g. "3/24". "-" if they've never bowled.
    val bestBowlingFigures: String get() = if (inningsBowled == 0) "-" else "$bestBowlingWickets/$bestBowlingRuns"
}

/**
 * Everything the Player Profile screen needs: the career totals above (across every format
 * combined), the SAME numbers again but split out per MatchFormat (for the reference app's
 * T10/T20/Club/OD style columns — a match scored before format-tracking existed, or one with no
 * format chosen, only counts towards `careerStats`, not any entry in `statsByFormat`), plus a
 * short "recent form" strip — the runs scored in each of the player's last few innings, newest
 * first. A null entry in recentForm means that recent match happened but this player didn't bat in it.
 */
data class PlayerProfileStats(
    val careerStats: CareerStats,
    val statsByFormat: Map<MatchFormat, CareerStats>,
    val recentForm: List<Int?>
)

/**
 * Aggregates a player's figures across every match a user has recorded. Player identity here is
 * the display name used at scoring time (see MatchRepository) — matches Phase 1's simplified,
 * ad-hoc team model. A future phase can key this off a stable player id once teams are fully
 * account-linked.
 */
class StatsRepository(
    private val matchDao: MatchDao,
    private val matchRepository: MatchRepository
) {
    /**
     * Walks through EVERY match this user has recorded, and for each one, checks whether this
     * particular player batted and/or bowled in it — if so, adds their figures from that match
     * onto a running career total. This recalculates from scratch every time it's called (no
     * separate "career stats" table saved anywhere) — same replay-everything approach used
     * throughout this app.
     */
    suspend fun careerStatsFor(uid: String, playerName: String): CareerStats =
        profileStatsFor(uid, playerName, recentFormLimit = 0).careerStats

    /**
     * Same idea as careerStatsFor, but also returns a short "recent form" list (most recent
     * innings first) AND a per-format breakdown, for the Player Profile screen. Doing all three
     * in one pass avoids walking through every match's ball-by-ball data more than once.
     */
    suspend fun profileStatsFor(uid: String, playerName: String, recentFormLimit: Int = 5): PlayerProfileStats {
        var overall = CareerStats()
        val byFormat = mutableMapOf<MatchFormat, CareerStats>()
        val recentForm = mutableListOf<Int?>()
        // Newest matches first, so the "recent form" strip reflects the player's LATEST innings,
        // not just whichever matches happened to be saved first.
        val matches = matchDao.getMatchesForUserOnce(uid).sortedByDescending { it.createdAt }

        for (match in matches) {
            // Older matches scored before format-tracking existed (or a format that somehow
            // didn't match a known MatchFormat name) simply don't get a per-format bucket —
            // they still count fully towards `overall` below.
            val format = match.format?.let { runCatching { MatchFormat.valueOf(it) }.getOrNull() }
            var appearedInMatch = false
            var battedThisMatch = false
            // A match can have up to 2 innings (one per team) — check both for this player.
            for (summary in matchRepository.getFinalInningsStates(match.id)) {
                val state = summary.state
                state.batsmanFigures[playerName]?.let { fig ->
                    appearedInMatch = true
                    battedThisMatch = true
                    overall = overall.withBatting(fig)
                    if (format != null) byFormat[format] = byFormat.getOrDefault(format, CareerStats()).withBatting(fig)
                    if (recentForm.size < recentFormLimit) recentForm.add(fig.runs)
                }
                // Same idea for bowling figures — a player could have both a batting AND a
                // bowling entry in the same innings if they're an all-rounder.
                state.bowlerFigures[playerName]?.let { fig ->
                    appearedInMatch = true
                    overall = overall.withBowling(fig)
                    if (format != null) byFormat[format] = byFormat.getOrDefault(format, CareerStats()).withBowling(fig)
                }
                // Fielding credit is tracked separately — a player can be credited with a
                // catch/stumping/run-out in an innings they didn't bat OR bowl in at all.
                state.fieldingFigures[playerName]?.let { fig ->
                    appearedInMatch = true
                    overall = overall.withFielding(fig)
                    if (format != null) byFormat[format] = byFormat.getOrDefault(format, CareerStats()).withFielding(fig)
                }
            }
            // Only count this as a "match played" if the player actually appeared in it somewhere.
            if (appearedInMatch) {
                overall = overall.copy(matchesPlayed = overall.matchesPlayed + 1)
                if (format != null) {
                    byFormat[format] = byFormat.getOrDefault(format, CareerStats()).let { it.copy(matchesPlayed = it.matchesPlayed + 1) }
                }
            }
            // If the player didn't bat in this (already-counted-as-recent) match at all, the form
            // strip should show a gap for it rather than silently skipping to an older match.
            if (!battedThisMatch && appearedInMatch && recentForm.size < recentFormLimit) recentForm.add(null)
        }

        return PlayerProfileStats(overall, byFormat, recentForm)
    }

    /** Folds one innings' batting figures on top of an existing CareerStats — shared by both the "overall" total and each per-format bucket. */
    private fun CareerStats.withBatting(fig: BatsmanFigures): CareerStats {
        val milestone = when {
            fig.runs >= 100 -> Triple(1, 0, 0)
            fig.runs >= 50 -> Triple(0, 1, 0)
            fig.runs >= 30 -> Triple(0, 0, 1)
            else -> Triple(0, 0, 0)
        }
        return copy(
            inningsBatted = inningsBatted + 1,
            runs = runs + fig.runs,
            ballsFaced = ballsFaced + fig.ballsFaced,
            timesOut = timesOut + if (fig.isOut) 1 else 0,
            notOuts = notOuts + if (fig.isOut) 0 else 1,
            fours = fours + fig.fours,
            sixes = sixes + fig.sixes,
            highScore = maxOf(highScore, fig.runs),
            hundreds = hundreds + milestone.first,
            fifties = fifties + milestone.second,
            thirties = thirties + milestone.third,
            ducks = ducks + if (fig.isOut && fig.runs == 0) 1 else 0
        )
    }

    /** Folds one innings' bowling figures on top of an existing CareerStats — see withBatting above for why this is a shared helper. */
    private fun CareerStats.withBowling(fig: BowlerFigures): CareerStats {
        // "Best" bowling = most wickets first, then fewest runs conceded as a tie-breaker — the
        // same ordering cricket scorecards have always used. The very first bowling innings ever
        // recorded always counts as "the best so far", even a wicketless one, otherwise it would
        // be stuck showing "0/0" instead of what actually happened.
        val isNewBest = inningsBowled == 0 ||
            fig.wickets > bestBowlingWickets ||
            (fig.wickets == bestBowlingWickets && fig.runsConceded < bestBowlingRuns)
        return copy(
            inningsBowled = inningsBowled + 1,
            wickets = wickets + fig.wickets,
            ballsBowled = ballsBowled + fig.legalBalls,
            runsConceded = runsConceded + fig.runsConceded,
            bestBowlingWickets = if (isNewBest) fig.wickets else bestBowlingWickets,
            bestBowlingRuns = if (isNewBest) fig.runsConceded else bestBowlingRuns
        )
    }

    /** Folds one innings' fielding figures on top of an existing CareerStats — see withBatting above for why this is a shared helper. */
    private fun CareerStats.withFielding(fig: FieldingFigures): CareerStats =
        copy(catches = catches + fig.catches, stumpings = stumpings + fig.stumpings, runOuts = runOuts + fig.runOuts)
}
