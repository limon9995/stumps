package com.mdlimonhossain.stumps.domain.repository

import com.mdlimonhossain.stumps.data.local.db.match.BallDao
import com.mdlimonhossain.stumps.data.local.db.match.BallEntity
import com.mdlimonhossain.stumps.data.local.db.match.InningsDao
import com.mdlimonhossain.stumps.data.local.db.match.InningsEntity
import com.mdlimonhossain.stumps.data.local.db.match.MatchDao
import com.mdlimonhossain.stumps.data.local.db.match.MatchEntity
import com.mdlimonhossain.stumps.data.local.db.match.PlayerDao
import com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity
import com.mdlimonhossain.stumps.data.local.db.match.TeamDao
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType
import com.mdlimonhossain.stumps.domain.scoring.BallRecord
import com.mdlimonhossain.stumps.domain.scoring.InningsState
import com.mdlimonhossain.stumps.domain.scoring.ScoringEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.util.UUID

/**
 * This file is the "middleman" between the app's screens and the actual database (Room/SQLite)
 * for everything to do with matches: creating a match, saving each ball as it's scored, and
 * reading back the current state so the screen can show it. Screens never talk to the database
 * directly — they always go through a "repository" like this one. That way, if we ever changed
 * HOW we store data, only this file would need to change, not every screen.
 *
 * Remember from ScoringEngine.kt: we don't store "the score" directly, we store every ball ever
 * bowled (see BallEntity), and re-calculate the score by replaying them with ScoringEngine
 * whenever we need it. This file is where that replaying actually gets triggered.
 */

/** Bundles an innings row with the match it belongs to, so the scoring screen has both in one emission. */
data class LiveInnings(val innings: InningsEntity, val match: MatchEntity, val state: InningsState)

/** How two teams have fared against each other across every match this user has recorded between them (not counting the match currently being looked at). */
data class HeadToHeadRecord(val teamAWins: Int, val teamBWins: Int, val played: Int)

/** A finished innings plus the display name of the batting team — for history/summary screens. */
data class InningsSummary(
    val innings: InningsEntity,
    val state: InningsState,
    val battingTeamName: String,
    val ballLog: List<BallRecord> = emptyList()
)

/**
 * MatchRepository ties together 5 different database tables (teams, players, matches, innings,
 * balls — each one is a "Dao", short for Data Access Object, which is just a class Room gives us
 * for reading/writing one table) and exposes simple, easy-to-use functions for the rest of the
 * app to call, like "createQuickMatch" or "recordBall".
 */
class MatchRepository(
    private val teamDao: TeamDao,
    private val playerDao: PlayerDao,
    private val matchDao: MatchDao,
    private val inningsDao: InningsDao,
    private val ballDao: BallDao
) {
    // "Flow" here means "a stream of values over time" — instead of asking the database once,
    // we subscribe to it, and every time the underlying data changes, we automatically get sent
    // the new value. This is how the UI stays live-updating without us manually refreshing it.
    fun observeMatchesForUser(uid: String): Flow<List<MatchEntity>> = matchDao.observeMatchesForUser(uid)
    // Used by a saved team's own Matches tab (see TeamDetailScreen.kt) — every match this ONE
    // team has played, whichever side of the match it was on.
    fun observeMatchesForTeam(teamId: String): Flow<List<MatchEntity>> = matchDao.observeMatchesForTeam(teamId)
    fun observeMatch(matchId: String): Flow<MatchEntity?> = matchDao.observeMatch(matchId)
    fun observeInningsForMatch(matchId: String): Flow<List<InningsEntity>> = inningsDao.observeInningsForMatch(matchId)
    fun observePlayersForTeam(teamId: String): Flow<List<PlayerEntity>> = playerDao.observePlayersForTeam(teamId)

    /**
     * Finds this user's own matches where EITHER team's name contains the search text — matches
     * don't have a name of their own to search by (they're just "Team A vs Team B"), so this
     * searches by team name instead, via the two saved teams a match created (see
     * createQuickMatch: every match makes its own fresh TeamEntity rows, even for a standalone
     * quick match, so a text search on teams is always meaningful here).
     */
    suspend fun searchByTeamName(uid: String, query: String): List<MatchEntity> {
        if (query.isBlank()) return emptyList()
        val matchingTeamIds = teamDao.searchByName(uid, query).map { it.id }.toSet()
        if (matchingTeamIds.isEmpty()) return emptyList()
        return matchDao.getMatchesForUserOnce(uid).filter { it.teamAId in matchingTeamIds || it.teamBId in matchingTeamIds }
    }

    /** Creates ad-hoc teams + players for a quick match (Phase 3 adds reusable saved teams). */
    suspend fun createQuickMatch(
        createdByUid: String,
        teamAName: String,
        teamAPlayerNames: List<String>,
        teamBName: String,
        teamBPlayerNames: List<String>,
        oversLimit: Int,
        tossWinnerIsTeamA: Boolean,
        tossDecisionIsBat: Boolean,
        openingStrikerId: String,
        openingNonStrikerId: String,
        openingBowlerId: String,
        tournamentId: String? = null,
        format: com.mdlimonhossain.stumps.domain.model.MatchFormat? = null
    ): String {
        // Give each team a random, unique id (a "UUID" — Universally Unique Identifier — is
        // just a long random string that's practically guaranteed never to clash with another one).
        val teamAId = UUID.randomUUID().toString()
        val teamBId = UUID.randomUUID().toString()
        teamDao.upsert(TeamEntity(teamAId, teamAName, null, createdByUid))
        teamDao.upsert(TeamEntity(teamBId, teamBName, null, createdByUid))
        // "upsert" means "update if it already exists, otherwise insert a new row" — a handy
        // combo word for "update or insert".
        playerDao.upsertAll(teamAPlayerNames.map { PlayerEntity(UUID.randomUUID().toString(), teamAId, it, "BATSMAN") })
        playerDao.upsertAll(teamBPlayerNames.map { PlayerEntity(UUID.randomUUID().toString(), teamBId, it, "BATSMAN") })

        val matchId = UUID.randomUUID().toString()
        val tossWinnerTeamId = if (tossWinnerIsTeamA) teamAId else teamBId
        matchDao.upsert(
            MatchEntity(
                id = matchId,
                tournamentId = tournamentId,
                teamAId = teamAId,
                teamBId = teamBId,
                oversLimit = oversLimit,
                tossWinnerTeamId = tossWinnerTeamId,
                tossDecision = if (tossDecisionIsBat) "BAT" else "BOWL",
                status = "LIVE",
                createdByUid = createdByUid,
                createdAt = System.currentTimeMillis(),
                format = format?.name
            )
        )

        // Work out which team is actually batting first: if the toss winner chose to bat, they
        // bat; if they chose to bowl, the OTHER team bats first.
        // battingIsTeamA mirrors ui/match/OpeningLineupScreen.battingIsTeamA — keep both in sync.
        val battingIsTeamA = (tossWinnerIsTeamA && tossDecisionIsBat) || (!tossWinnerIsTeamA && !tossDecisionIsBat)
        val battingTeamId = if (battingIsTeamA) teamAId else teamBId
        val bowlingTeamId = if (battingIsTeamA) teamBId else teamAId

        // Create the first innings row — this is just the "who's opening" info. No balls have
        // been bowled yet, so there's nothing else to save here; the score starts at 0/0.
        val inningsId = UUID.randomUUID().toString()
        inningsDao.upsert(
            InningsEntity(
                id = inningsId,
                matchId = matchId,
                inningsNumber = 1,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                openingStrikerId = openingStrikerId,
                openingNonStrikerId = openingNonStrikerId,
                openingBowlerId = openingBowlerId
            )
        )
        return matchId
    }

    /**
     * Reactive, replay-derived live state for one innings: recomputed from the ball log on every
     * change, and also re-emits when the match row itself changes (e.g. live-broadcast toggled),
     * not just when a ball is recorded.
     *
     * In plain words: this function gives the screen a live, always-up-to-date scoreboard. Every
     * time a new ball is saved to the database, this automatically re-runs ScoringEngine and
     * sends the fresh result out, so the screen updates itself with no extra work needed.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeLiveInnings(inningsId: String): Flow<LiveInnings?> =
        // flatMapLatest: "whenever the innings row changes, switch to watching a NEW combined
        // stream built from that innings' match + balls". This makes sure we're always watching
        // the right match, even if the innings itself gets swapped out.
        inningsDao.observeById(inningsId).flatMapLatest { innings ->
            if (innings == null) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                // combine: "whenever EITHER the match info OR the ball list changes, run this
                // block again with the latest of both". That's what makes the screen react to
                // both new balls being scored AND things like the live-broadcast toggle changing.
                combine(
                    matchDao.observeMatch(innings.matchId),
                    ballDao.observeBallsForInnings(inningsId)
                ) { match, balls ->
                    if (match == null) null
                    else LiveInnings(
                        innings, match,
                        // This is the important bit: every time anything changes, we replay
                        // the WHOLE ball list from scratch through ScoringEngine to get the
                        // current scoreboard. See ScoringEngine.kt for why we do it this way.
                        ScoringEngine.computeState(
                            balls = balls.map { it.toRecord() },
                            openingStrikerId = innings.openingStrikerId,
                            openingNonStrikerId = innings.openingNonStrikerId,
                            openingBowlerId = innings.openingBowlerId,
                            oversLimit = match.oversLimit
                        )
                    )
                }
            }
        }

    /**
     * Saves one new ball to the database — this is called every time the scorer taps a run
     * button, records an extra, or records a wicket. It doesn't calculate anything itself; it
     * just writes down what happened, and the next time observeLiveInnings runs, it will pick
     * this new ball up automatically.
     */
    suspend fun recordBall(
        innings: InningsEntity,
        state: InningsState,
        runsOffBat: Int,
        extraType: ExtraType?,
        extraRuns: Int,
        runsRun: Int,
        isWicket: Boolean,
        dismissalType: DismissalType?,
        dismissedPlayerId: String?,
        newBatsmanId: String?,
        selectedBowlerId: String?,
        shotAngleDegrees: Int? = null,
        fielderId: String? = null
    ): BallEntity {
        // Work out the next ball number in this innings (1st ball, 2nd ball, ...) by looking
        // at the highest sequence number saved so far and adding 1.
        val nextSequence = (ballDao.maxSequence(innings.id) ?: 0) + 1
        val ball = BallEntity(
            id = UUID.randomUUID().toString(),
            inningsId = innings.id,
            sequence = nextSequence,
            // We already know who's bowling/on strike from the CURRENT state (the scoreboard
            // as of the previous ball) — no need for the caller to re-specify it every time.
            bowlerId = selectedBowlerId ?: state.currentBowlerId ?: innings.openingBowlerId,
            strikerId = state.strikerId ?: innings.openingStrikerId,
            nonStrikerId = state.nonStrikerId ?: innings.openingNonStrikerId,
            runsOffBat = runsOffBat,
            extraType = extraType?.name,
            extraRuns = extraRuns,
            runsRun = runsRun,
            isWicket = isWicket,
            dismissalType = dismissalType?.name,
            dismissedPlayerId = dismissedPlayerId,
            newBatsmanId = newBatsmanId,
            shotAngleDegrees = shotAngleDegrees,
            fielderId = fielderId
        )
        ballDao.insert(ball)
        return ball
    }

    /** Returns the ball that was removed (for mirroring the deletion into a live broadcast), or null if the innings had none. */
    suspend fun undoLastBall(inningsId: String): BallEntity? {
        // "Undo" here is beautifully simple thanks to the event-sourcing design (see
        // ScoringEngine.kt's big comment at the top): we just delete the very last ball. The
        // next time the scoreboard is recalculated, it'll be as if that ball never happened.
        val removed = ballDao.getBallsForInningsOnce(inningsId).maxByOrNull { it.sequence }
        ballDao.deleteLastBall(inningsId)
        return removed
    }

    /** Turns live-broadcast on/off for a match, and saves the share code viewers type in to watch. */
    suspend fun setLive(matchId: String, isLive: Boolean, shareCode: String?) {
        val match = matchDao.getById(matchId) ?: return
        matchDao.upsert(match.copy(isLive = isLive, shareCode = shareCode ?: match.shareCode))
    }

    /** One-shot (non-reactive) final state for every innings of a match — used for match history and stats. */
    suspend fun getFinalInningsStates(matchId: String): List<InningsSummary> {
        // Unlike observeLiveInnings, this doesn't keep watching for changes — it just reads
        // the data ONCE and hands back the final answer. Good for a finished match's scorecard,
        // where nothing is going to change any more.
        val match = matchDao.getById(matchId) ?: return emptyList()
        return inningsDao.getInningsForMatchOnce(matchId).map { innings ->
            val balls = ballDao.getBallsForInningsOnce(innings.id).map { it.toRecord() }
            val state = ScoringEngine.computeState(
                balls = balls,
                openingStrikerId = innings.openingStrikerId,
                openingNonStrikerId = innings.openingNonStrikerId,
                openingBowlerId = innings.openingBowlerId,
                oversLimit = match.oversLimit
            )
            val teamName = teamDao.getById(innings.battingTeamId)?.name ?: "Innings ${innings.inningsNumber}"
            InningsSummary(innings, state, teamName, balls)
        }
    }

    suspend fun getMatchOnce(matchId: String): MatchEntity? = matchDao.getById(matchId)

    /**
     * Looks back through every OTHER match this user has recorded between these exact two team
     * names (either order — "A vs B" and "B vs A" both count) and tallies who won each one, for
     * Match Centre's Info tab "Head to Head" section. A match only counts if it actually finished
     * (both innings complete) — an abandoned/incomplete match has no winner to count.
     */
    suspend fun headToHead(uid: String, teamAName: String, teamBName: String, excludeMatchId: String): HeadToHeadRecord {
        var teamAWins = 0
        var teamBWins = 0
        var played = 0
        for (match in matchDao.getMatchesForUserOnce(uid)) {
            if (match.id == excludeMatchId) continue
            val names = getTeamNames(match.id) ?: continue
            val isSamePairing = (names.first == teamAName && names.second == teamBName) ||
                (names.first == teamBName && names.second == teamAName)
            if (!isSamePairing) continue

            val innings = getFinalInningsStates(match.id)
            if (innings.size < 2) continue // never finished — no winner to count
            val aInnings = innings.firstOrNull { it.battingTeamName == teamAName } ?: continue
            val bInnings = innings.firstOrNull { it.battingTeamName == teamBName } ?: continue

            played += 1
            when {
                aInnings.state.totalRuns > bInnings.state.totalRuns -> teamAWins += 1
                bInnings.state.totalRuns > aInnings.state.totalRuns -> teamBWins += 1
                // Equal scores = a tie — doesn't add to either team's win count, but still counts as "played".
            }
        }
        return HeadToHeadRecord(teamAWins, teamBWins, played)
    }

    /** Looks up both teams' real names for a match — handy for building share text or PDF titles. */
    suspend fun getTeamNames(matchId: String): Pair<String, String>? {
        val match = matchDao.getById(matchId) ?: return null
        val teamA = teamDao.getById(match.teamAId)?.name ?: return null
        val teamB = teamDao.getById(match.teamBId)?.name ?: return null
        return teamA to teamB
    }

    /** Starts the SECOND innings of a match (the team batting first has finished, now the other team bats). */
    suspend fun startNextInnings(
        matchId: String,
        battingIsTeamA: Boolean,
        openingStrikerId: String,
        openingNonStrikerId: String,
        openingBowlerId: String,
        targetRuns: Int
    ): String {
        val match = matchDao.getById(matchId) ?: error("Match $matchId not found")
        val battingTeamId = if (battingIsTeamA) match.teamAId else match.teamBId
        val bowlingTeamId = if (battingIsTeamA) match.teamBId else match.teamAId
        val inningsId = UUID.randomUUID().toString()
        inningsDao.upsert(
            InningsEntity(
                id = inningsId,
                matchId = matchId,
                inningsNumber = 2,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                openingStrikerId = openingStrikerId,
                openingNonStrikerId = openingNonStrikerId,
                openingBowlerId = openingBowlerId,
                targetRuns = targetRuns // how many runs the second team needs to WIN (first innings total + 1)
            )
        )
        return inningsId
    }

    // A small helper that converts the database's version of a ball (BallEntity, which stores
    // things like extraType as a plain text string because that's what SQLite understands) into
    // ScoringEngine's version (BallRecord, which uses a proper ExtraType enum). Keeping the
    // database format and the "business logic" format separate like this is good practice —
    // it means ScoringEngine doesn't need to know anything about how we save data to disk.
    private fun BallEntity.toRecord() = BallRecord(
        sequence = sequence,
        bowlerId = bowlerId,
        strikerId = strikerId,
        nonStrikerId = nonStrikerId,
        runsOffBat = runsOffBat,
        extraType = extraType?.let { ExtraType.valueOf(it) },
        extraRuns = extraRuns,
        runsRun = runsRun,
        isWicket = isWicket,
        dismissalType = dismissalType?.let { DismissalType.valueOf(it) },
        dismissedPlayerId = dismissedPlayerId,
        newBatsmanId = newBatsmanId,
        shotAngleDegrees = shotAngleDegrees,
        fielderId = fielderId
    )
}
