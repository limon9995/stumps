package com.mdlimonhossain.stumps.domain.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mdlimonhossain.stumps.data.local.db.tournament.FixtureStage
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentDao
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureDao
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamDao
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity
import com.mdlimonhossain.stumps.data.remote.sync.LiveBroadcastRepository
import com.mdlimonhossain.stumps.domain.tournament.CompletedMatchResult
import com.mdlimonhossain.stumps.domain.tournament.TeamStanding
import com.mdlimonhossain.stumps.domain.tournament.TournamentEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * This file is the "middleman" (repository) for everything to do with TOURNAMENTS — the app's
 * main/flagship feature. It's the bridge between the tournament screens and the database, and
 * it also stitches together saved teams (TeamRepository) and matches (MatchRepository) to make
 * a whole tournament work: create it, generate the fixture list, start each match from a saved
 * team's roster, and finally work out the points table and leaderboards.
 */

/** One row of a leaderboard: a player's name and their total (runs for Orange Cap, wickets for Purple Cap, etc). */
data class LeaderboardEntry(val playerName: String, val value: Int)

/** A single best-innings bowling figure, e.g. "Rahim — 4/18". */
data class BestBowlingEntry(val playerName: String, val wickets: Int, val runsConceded: Int)

/** The tournament's best batting partnership — two players' names and how many runs they put on together. */
data class PartnershipEntry(val playerAName: String, val playerBName: String, val runs: Int)

/** A bowler with the tournament's best (lowest) economy rate, among those who've bowled at least one over. */
data class EconomyEntry(val playerName: String, val economy: Double)

/**
 * Orange Cap = the tournament's top run-scorer. Purple Cap = the tournament's top wicket-taker.
 * Same idea as the IPL awards. `highestScore`/`bestBowling` are the best SINGLE-INNINGS figures
 * (not summed across the whole tournament, unlike orangeCap/purpleCap) — the same "biggest one
 * innings" idea as CareerStats.highScore/bestBowlingFigures elsewhere in this app.
 *
 * The fields from `bestPartnership` onward back the reference app's "More Statistics" section —
 * made possible by ScoringEngine now tracking ball-by-ball milestones (fastest fifty/hundred),
 * over-level maidens, and batting partnerships, not just simple running totals.
 */
data class TournamentLeaderboards(
    val orangeCap: List<LeaderboardEntry>,
    val purpleCap: List<LeaderboardEntry>,
    val highestScore: LeaderboardEntry? = null,
    val bestBowling: BestBowlingEntry? = null,
    val mostSixes: List<LeaderboardEntry> = emptyList(),
    val mostFours: List<LeaderboardEntry> = emptyList(),
    val mostCatches: List<LeaderboardEntry> = emptyList(),
    val mostStumpings: List<LeaderboardEntry> = emptyList(),
    val bestPartnership: PartnershipEntry? = null,
    val mostBallsFaced: List<LeaderboardEntry> = emptyList(),
    val fastestFifty: LeaderboardEntry? = null, // value = balls faced to reach it — LOWER is better
    val fastestHundred: LeaderboardEntry? = null,
    val mostMaidens: List<LeaderboardEntry> = emptyList(),
    val bestEconomy: EconomyEntry? = null,
    // Most sixes/fours hit in a SINGLE innings (like highestScore) — different from
    // mostSixes/mostFours above, which are summed across the whole tournament.
    val inningsMostSixes: LeaderboardEntry? = null,
    val inningsMostFours: LeaderboardEntry? = null
)

/**
 * A tiny scoreboard for ONE fixture, for the Matches tab's cards — each team's score so far,
 * whether the match has started / finished, and who won.
 */
data class FixtureScore(
    val teamAScore: String? = null, // e.g. "120/5 (18.2)" — null if Team A hasn't batted yet
    val teamBScore: String? = null,
    val isStarted: Boolean = false,
    val isComplete: Boolean = false,
    val resultText: String? = null, // e.g. "Dhaka Tigers ৪৫ রানে জয়ী"
    // Who won. For a LEAGUE match this is null on a tie. For a knockout match there is ALWAYS
    // a winner once it's complete (see TournamentEngine.knockoutWinner for the tie rule).
    val winnerTeamId: String? = null
)

/** Which part of its life a tournament is in right now — drives the "journey" bar on the Home tab. */
enum class TournamentPhase { SETUP, LEAGUE, SEMI_FINALS, FINAL, FINISHED }

/**
 * A summary of "where is this tournament up to?" — worked out fresh from the fixtures and their
 * matches, never stored (same "replay everything" idea as the points table).
 */
data class TournamentProgress(
    val phase: TournamentPhase = TournamentPhase.SETUP,
    val stagePlayed: Int = 0, // how many matches of the CURRENT stage are finished
    val stageTotal: Int = 0, // how many matches the current stage has in total
    // True when every match of the current stage is done, so the NEXT stage can be created.
    val canAdvance: Boolean = false,
    val nextStageLabel: String? = null, // "সেমিফাইনাল" or "ফাইনাল" — what the advance button makes
    val qualifyCount: Int = 0, // how many teams from the league table go into the knockouts (4 or 2)
    val championTeamId: String? = null,
    val championName: String? = null,
    val runnerUpName: String? = null
)

class TournamentRepository(
    private val tournamentDao: TournamentDao,
    private val tournamentTeamDao: TournamentTeamDao,
    private val fixtureDao: TournamentFixtureDao,
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val liveBroadcastRepository: LiveBroadcastRepository = LiveBroadcastRepository(),
    firestoreOverride: FirebaseFirestore? = null
) {
    // `by lazy` means FirebaseFirestore.getInstance() is only actually called the FIRST time
    // something tries to reach the cloud directory — not immediately when this repository is
    // constructed. That matters because Robolectric unit tests build a TournamentRepository
    // without a real Firebase app running at all; if we called .getInstance() eagerly as a plain
    // constructor default, just CREATING the repository in a test would crash immediately.
    // Deferring it like this means tests that never touch the cloud-search methods (and the
    // best-effort `runCatching` push in createTournament) are unaffected.
    private val firestore: FirebaseFirestore by lazy { firestoreOverride ?: FirebaseFirestore.getInstance() }
    // Same "local truth + shared cloud mirror" split as ClubRepository — see its doc comment.
    private fun cloudTournamentsRef() = firestore.collection("tournaments")

    fun observeTournamentsForUser(uid: String): Flow<List<TournamentEntity>> = tournamentDao.observeForUser(uid)
    fun observeTournament(id: String): Flow<TournamentEntity?> = tournamentDao.observeById(id)
    suspend fun getTournamentOnce(id: String): TournamentEntity? = tournamentDao.getById(id)
    fun observeTournamentTeams(tournamentId: String): Flow<List<TournamentTeamEntity>> = tournamentTeamDao.observeForTournament(tournamentId)
    // Used by a saved team's own Tournaments tab (see TeamDetailScreen.kt) — every tournament
    // this ONE team has been entered into, newest first.
    fun observeTournamentsForTeam(teamId: String): Flow<List<TournamentEntity>> = tournamentDao.observeTournamentsForTeam(teamId)
    fun observeFixtures(tournamentId: String): Flow<List<TournamentFixtureEntity>> = fixtureDao.observeForTournament(tournamentId)
    suspend fun searchByName(uid: String, query: String): List<TournamentEntity> = tournamentDao.searchByName(uid, query)

    /** Searches the SHARED cloud directory of every user's tournaments — see ClubRepository.searchCloudByName for how/why this works the way it does. */
    suspend fun searchCloudByName(query: String): List<TournamentEntity> {
        if (query.isBlank()) return emptyList()
        val snapshot = cloudTournamentsRef().orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await()
        return snapshot.documents.mapNotNull { doc -> doc.toTournamentEntityOrNull()?.takeIf { it.name.contains(query, ignoreCase = true) } }
    }

    /** One-shot lookup of a single tournament from the shared cloud directory, by id — used when opening a tournament found via Search that this device doesn't organize (and so has no local copy of). */
    suspend fun getCloudTournament(id: String): TournamentEntity? = cloudTournamentsRef().document(id).get().await().toTournamentEntityOrNull()

    private fun com.google.firebase.firestore.DocumentSnapshot.toTournamentEntityOrNull(): TournamentEntity? {
        if (!exists()) return null
        val name = getString("name") ?: return null
        return TournamentEntity(
            id = id,
            name = name,
            format = getString("format") ?: "ROUND_ROBIN",
            oversPerMatch = (getLong("oversPerMatch") ?: 0L).toInt(),
            venue = getString("venue"),
            organizerUid = getString("organizerUid") ?: "",
            createdAt = getLong("createdAt") ?: 0L,
            clubName = getString("clubName"),
            city = getString("city"),
            season = getString("season"),
            startDate = getLong("startDate"),
            endDate = getLong("endDate"),
            ballType = getString("ballType")
        )
    }

    /**
     * Sets up a brand new tournament: saves the tournament itself, remembers which teams are
     * taking part, and automatically builds the full match schedule (every team plays every
     * other team once — see TournamentEngine.generateRoundRobinFixtures for the actual math).
     * `selectedTeamIds` defaults to empty — the newer "create tournament" form only asks for the
     * tournament's own details (name/club/city/season/dates/ball type), and lets teams be added
     * afterwards, one at a time, from the tournament's own Teams tab (see addTeamToTournament
     * below) instead of all at once during creation.
     */
    suspend fun createTournament(
        organizerUid: String,
        name: String,
        oversPerMatch: Int,
        venue: String?,
        selectedTeamIds: List<String> = emptyList(),
        clubName: String? = null,
        city: String? = null,
        season: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        ballType: String? = null
    ): String {
        val tournamentId = UUID.randomUUID().toString()
        val tournament = TournamentEntity(
            id = tournamentId, name = name, format = "ROUND_ROBIN",
            oversPerMatch = oversPerMatch, venue = venue,
            organizerUid = organizerUid, createdAt = System.currentTimeMillis(),
            clubName = clubName, city = city, season = season,
            startDate = startDate, endDate = endDate, ballType = ballType
        )
        tournamentDao.upsert(tournament)
        // Best-effort push to the shared cloud directory (see the class doc comment) — a failed
        // push here (offline, rules not set up yet) never blocks the tournament from working
        // perfectly for its own organizer locally.
        runCatching {
            cloudTournamentsRef().document(tournamentId).set(
                mapOf(
                    "name" to tournament.name,
                    "format" to tournament.format,
                    "oversPerMatch" to tournament.oversPerMatch,
                    "venue" to tournament.venue,
                    "organizerUid" to tournament.organizerUid,
                    "createdAt" to tournament.createdAt,
                    "clubName" to tournament.clubName,
                    "city" to tournament.city,
                    "season" to tournament.season,
                    "startDate" to tournament.startDate,
                    "endDate" to tournament.endDate,
                    "ballType" to tournament.ballType
                )
            ).await()
        }

        // Save a "snapshot" row for each participating team, remembering its name at the time
        // the tournament was created (teamRepository.getTeamOnce looks up the real saved team).
        val tournamentTeams = selectedTeamIds.map { teamId ->
            val teamName = teamRepository.getTeamOnce(teamId)?.name ?: teamId
            TournamentTeamEntity(
                id = UUID.randomUUID().toString(),
                tournamentId = tournamentId,
                teamId = teamId,
                teamName = teamName
            )
        }
        tournamentTeamDao.upsertAll(tournamentTeams)
        // Best-effort: mirror each participating team's name up to the cloud too, so someone
        // OTHER than the organizer browsing this tournament (found via Search) can see who's
        // playing without needing anything synced locally on their own device.
        runCatching {
            tournamentTeams.forEach { team ->
                cloudTournamentTeamsRef(tournamentId).document(team.id).set(
                    mapOf("teamId" to team.teamId, "teamName" to team.teamName)
                ).await()
            }
        }

        // Ask TournamentEngine for the list of "who plays who" pairs, then save each pair as a
        // fixture row. At this point no fixture has a matchId yet — that only gets filled in
        // once the organizer actually starts scoring that particular match.
        val pairs = TournamentEngine.generateRoundRobinFixtures(selectedTeamIds)
        val fixtures = pairs.mapIndexed { index, (a, b) ->
            TournamentFixtureEntity(
                id = UUID.randomUUID().toString(),
                tournamentId = tournamentId,
                round = index + 1,
                teamAId = a,
                teamBId = b
            )
        }
        fixtureDao.upsertAll(fixtures)
        runCatching { fixtures.forEach { pushFixtureToCloud(tournamentId, it) } }
        return tournamentId
    }

    /**
     * Adds ONE more saved team to an already-existing tournament — used by the Teams tab's "ADD
     * TEAM" flow. Rather than regenerating the WHOLE fixture list from scratch (which could
     * disturb fixtures that already have a matchId, i.e. already-played or in-progress matches),
     * this only creates the new fixtures this addition actually needs: the new team playing every
     * team that was ALREADY in the tournament, exactly once each. Every existing fixture between
     * two OLD teams is left completely untouched.
     */
    suspend fun addTeamToTournament(tournamentId: String, teamId: String) {
        // Once the knockouts (semi-final / final) have started, the league is closed — a new
        // team joining now would have league matches that can never count for anything.
        if (fixtureDao.getForTournamentOnce(tournamentId).any { it.stage != FixtureStage.LEAGUE }) return
        val existingTeams = tournamentTeamDao.getForTournamentOnce(tournamentId)
        if (existingTeams.any { it.teamId == teamId }) return // already in this tournament — nothing to do
        val teamName = teamRepository.getTeamOnce(teamId)?.name ?: return

        val newTeamRow = TournamentTeamEntity(UUID.randomUUID().toString(), tournamentId, teamId, teamName)
        tournamentTeamDao.upsertAll(listOf(newTeamRow))
        runCatching {
            cloudTournamentTeamsRef(tournamentId).document(newTeamRow.id).set(
                mapOf("teamId" to teamId, "teamName" to teamName)
            ).await()
        }

        val existingFixtures = fixtureDao.getForTournamentOnce(tournamentId)
        val nextRound = (existingFixtures.maxOfOrNull { it.round } ?: 0) + 1
        val newFixtures = existingTeams.map { other ->
            TournamentFixtureEntity(id = UUID.randomUUID().toString(), tournamentId = tournamentId, round = nextRound, teamAId = teamId, teamBId = other.teamId)
        }
        if (newFixtures.isNotEmpty()) {
            fixtureDao.upsertAll(newFixtures)
            runCatching { newFixtures.forEach { pushFixtureToCloud(tournamentId, it) } }
        }
    }

    /** Renames a tournament — used by the Home tab's "More" menu. */
    suspend fun renameTournament(tournament: TournamentEntity, newName: String) {
        tournamentDao.upsert(tournament.copy(name = newName))
    }

    /** Permanently deletes a tournament — used by the Home tab's "More" menu. */
    suspend fun deleteTournament(tournament: TournamentEntity) {
        tournamentDao.delete(tournament)
    }

    /**
     * Total sixes and fours hit across EVERY finished match in this tournament — the Home tab's
     * "Tournament Boundaries" numbers. Walks each finished fixture's ball-by-ball log once; same
     * "replay everything, don't store a running total" approach used throughout this app.
     */
    suspend fun computeBoundaryCounts(tournamentId: String): Pair<Int, Int> {
        var sixes = 0
        var fours = 0
        for (fixture in fixtureDao.getForTournamentOnce(tournamentId)) {
            val matchId = fixture.matchId ?: continue
            for (summary in matchRepository.getFinalInningsStates(matchId)) {
                for (ball in summary.ballLog) {
                    if (ball.runsOffBat == 6) sixes++
                    if (ball.runsOffBat == 4) fours++
                }
            }
        }
        return sixes to fours
    }

    private fun cloudTournamentTeamsRef(tournamentId: String) = cloudTournamentsRef().document(tournamentId).collection("teams")
    private fun cloudFixturesRef(tournamentId: String) = cloudTournamentsRef().document(tournamentId).collection("fixtures")

    private suspend fun pushFixtureToCloud(tournamentId: String, fixture: TournamentFixtureEntity) {
        cloudFixturesRef(tournamentId).document(fixture.id).set(
            mapOf(
                "round" to fixture.round,
                "teamAId" to fixture.teamAId,
                "teamBId" to fixture.teamBId,
                "matchId" to fixture.matchId,
                "stage" to fixture.stage
            )
        ).await()
    }

    /** Starts scoring one fixture: pulls the saved team's roster and creates a match linked to this tournament. */
    suspend fun startFixtureMatch(
        fixture: TournamentFixtureEntity,
        tournament: TournamentEntity,
        organizerUid: String,
        tossWinnerIsTeamA: Boolean,
        tossDecisionIsBat: Boolean,
        openingStrikerId: String,
        openingNonStrikerId: String,
        openingBowlerId: String
    ): Pair<String, String> {
        // Look up the two teams' real rosters (their saved player lists) so the match is
        // scored with the actual squad, not a freshly-typed one.
        val teamA = teamRepository.getTeamOnce(fixture.teamAId) ?: error("Team ${fixture.teamAId} not found")
        val teamB = teamRepository.getTeamOnce(fixture.teamBId) ?: error("Team ${fixture.teamBId} not found")
        val teamAPlayers = teamRepository.getPlayersOnce(fixture.teamAId).map { it.name }
        val teamBPlayers = teamRepository.getPlayersOnce(fixture.teamBId).map { it.name }

        // Behind the scenes this is just a normal match, created the same way as a standalone
        // quick match — the only difference is we pass tournamentId so it's linked back here.
        val matchId = matchRepository.createQuickMatch(
            createdByUid = organizerUid,
            teamAName = teamA.name,
            teamAPlayerNames = teamAPlayers,
            teamBName = teamB.name,
            teamBPlayerNames = teamBPlayers,
            oversLimit = tournament.oversPerMatch,
            tossWinnerIsTeamA = tossWinnerIsTeamA,
            tossDecisionIsBat = tossDecisionIsBat,
            openingStrikerId = openingStrikerId,
            openingNonStrikerId = openingNonStrikerId,
            openingBowlerId = openingBowlerId,
            tournamentId = tournament.id,
            // No separate "pick a format" step exists for tournament fixtures (every match in a
            // tournament already shares the same overs-per-match) — so this infers a reasonable
            // MatchFormat label straight from that overs count, purely for Profile Statistics'
            // format-wise breakdown to have SOMETHING sensible to group by.
            format = when {
                tournament.oversPerMatch <= 10 -> com.mdlimonhossain.stumps.domain.model.MatchFormat.T10
                tournament.oversPerMatch <= 20 -> com.mdlimonhossain.stumps.domain.model.MatchFormat.T20
                else -> com.mdlimonhossain.stumps.domain.model.MatchFormat.ODI
            }
        )
        // Remember which match belongs to this fixture, so the fixture list can show
        // "in progress" / "finished" instead of "not started".
        val updatedFixture = fixture.copy(matchId = matchId)
        fixtureDao.upsert(updatedFixture)
        runCatching { pushFixtureToCloud(tournament.id, updatedFixture) }

        // Grab the id of the first innings that was just created, so the caller can jump
        // straight into the scoring screen for it.
        val firstInningsId = matchRepository.observeInningsForMatch(matchId).first().first().id

        // Tournament matches are always meant to be publicly visible (that's the whole point of
        // a tournament someone can follow) — so unlike a standalone quick match, this ALWAYS
        // turns on the same live-broadcast mirroring a scorer would otherwise have to tap "go
        // live" for. Every ball recorded from here on gets pushed to Firestore automatically
        // (see MatchViewModel — it already pushes balls whenever match.isLive is true), with no
        // other code needing to change. Best-effort: if this fails (offline right now), the
        // match still scores perfectly locally; it just won't be live-visible until it's retried.
        runCatching {
            val shareCode = liveBroadcastRepository.generateShareCode()
            matchRepository.setLive(matchId, true, shareCode)
            val match = matchRepository.getMatchOnce(matchId) ?: error("just-created match vanished")
            liveBroadcastRepository.goLive(match, teamA.name, teamB.name, shareCode)
            // Same "who won the toss AND chose to bat, or lost it but the winner chose to
            // bowl" logic as ui/match/OpeningLineupScreen.battingIsTeamA — kept as a standalone
            // calculation here since this repository doesn't depend on that UI-layer file.
            val battingIsTeamA = (tossWinnerIsTeamA && tossDecisionIsBat) || (!tossWinnerIsTeamA && !tossDecisionIsBat)
            val battingName = if (battingIsTeamA) teamA.name else teamB.name
            val bowlingName = if (battingIsTeamA) teamB.name else teamA.name
            val firstInnings = matchRepository.observeInningsForMatch(matchId).first().first { it.id == firstInningsId }
            liveBroadcastRepository.pushInnings(matchId, firstInnings, battingName, bowlingName)
        }

        return matchId to firstInningsId
    }

    /**
     * Builds the tournament's points table right now, by looking at every fixture that has
     * actually been played to completion. This is recalculated fresh every time it's called
     * (not stored anywhere) — same "replay everything" philosophy as the scoring engine.
     */
    suspend fun computeStandings(tournamentId: String): List<TeamStanding> {
        val teams = tournamentTeamDao.getForTournamentOnce(tournamentId)
        // Only LEAGUE fixtures count towards the points table (semi-finals and the final are
        // knockouts — they decide the champion, not points), and only ones that have been started.
        val fixtures = fixtureDao.getForTournamentOnce(tournamentId)
            .filter { it.matchId != null && it.stage == FixtureStage.LEAGUE }

        val results = mutableListOf<CompletedMatchResult>()
        for (fixture in fixtures) {
            // readFixture (below) does the fiddly work of finding each team's innings.
            val reading = readFixture(fixture, teams) ?: continue
            if (!reading.isComplete) continue // still being played — doesn't count yet
            val aInnings = reading.teamAInnings ?: continue
            val bInnings = reading.teamBInnings ?: continue

            // Boil this whole match down to just the numbers TournamentEngine needs for the
            // points table and Net Run Rate math (10 wickets down = "all out").
            results.add(
                CompletedMatchResult(
                    teamAId = fixture.teamAId,
                    teamBId = fixture.teamBId,
                    oversLimit = reading.oversLimit,
                    teamARuns = aInnings.state.totalRuns,
                    teamALegalBalls = aInnings.state.legalBallsBowled,
                    teamAAllOut = aInnings.state.totalWickets >= 10,
                    teamBRuns = bInnings.state.totalRuns,
                    teamBLegalBalls = bInnings.state.legalBallsBowled,
                    teamBAllOut = bInnings.state.totalWickets >= 10
                )
            )
        }

        // Hand everything to TournamentEngine, which does the actual points/NRR maths.
        return TournamentEngine.computeStandings(teams.map { it.teamId to it.teamName }, results)
    }

    /** Everything readFixture found out about one fixture's match. */
    private class FixtureReading(
        val allInnings: List<InningsSummary>,
        val teamAInnings: InningsSummary?, // the innings where the fixture's Team A batted (null if not yet)
        val teamBInnings: InningsSummary?,
        val oversLimit: Int,
        val resultText: String?,
        val isComplete: Boolean
    )

    /**
     * Reads one fixture's match from the database: each team's innings, and whether the match
     * is actually FINISHED. Returns null if the fixture hasn't been started yet.
     *
     * "Finished" means the second innings is over — either all out / overs used up, or the
     * chasing team has already reached its target (the scoring screen stops there too).
     */
    private suspend fun readFixture(fixture: TournamentFixtureEntity, teams: List<TournamentTeamEntity>): FixtureReading? {
        val matchId = fixture.matchId ?: return null
        val match = matchRepository.getMatchOnce(matchId) ?: return null
        val innings = matchRepository.getFinalInningsStates(matchId)

        // Each fixture's match creates its OWN fresh team rows (see MatchRepository.createQuickMatch),
        // so we can't compare ids directly — instead we match up innings to fixture teams
        // by comparing TEAM NAMES, which are always kept the same on purpose.
        val teamAName = teams.firstOrNull { it.teamId == fixture.teamAId }?.teamName
        val teamBName = teams.firstOrNull { it.teamId == fixture.teamBId }?.teamName
        val second = innings.firstOrNull { it.innings.inningsNumber == 2 }
        val targetReached = second != null && second.innings.targetRuns?.let { second.state.totalRuns >= it } == true
        val isComplete = match.status == "COMPLETED" || (second != null && (second.state.isInningsComplete || targetReached))
        return FixtureReading(
            allInnings = innings,
            teamAInnings = innings.firstOrNull { it.battingTeamName == teamAName },
            teamBInnings = innings.firstOrNull { it.battingTeamName == teamBName },
            oversLimit = match.oversLimit,
            resultText = match.resultText,
            isComplete = isComplete
        )
    }

    /** "120/5 (18.2)" — the classic way a cricket score is written: runs/wickets (overs). */
    private fun InningsSummary.scoreLine(): String = "${state.totalRuns}/${state.totalWickets} (${state.oversDisplay})"

    /**
     * A mini scoreboard for EVERY fixture in the tournament (fixture id -> its score), for the
     * Matches tab's cards.
     */
    suspend fun computeFixtureScores(tournamentId: String): Map<String, FixtureScore> {
        val teams = tournamentTeamDao.getForTournamentOnce(tournamentId)
        return fixtureDao.getForTournamentOnce(tournamentId).associate { fixture ->
            fixture.id to fixtureScore(fixture, teams)
        }
    }

    /** Builds the mini scoreboard (see FixtureScore) for ONE fixture. */
    private suspend fun fixtureScore(fixture: TournamentFixtureEntity, teams: List<TournamentTeamEntity>): FixtureScore {
        val reading = readFixture(fixture, teams) ?: return FixtureScore() // not started yet
        val a = reading.teamAInnings
        val b = reading.teamBInnings
        var winner: String? = null
        var result = reading.resultText ?: if (reading.isComplete) matchResultText(reading.allInnings) else null
        if (reading.isComplete && a != null && b != null) {
            val aRuns = a.state.totalRuns
            val bRuns = b.state.totalRuns
            if (fixture.stage == FixtureStage.LEAGUE) {
                winner = when {
                    aRuns > bRuns -> fixture.teamAId
                    bRuns > aRuns -> fixture.teamBId
                    else -> null // a league tie — 1 point each, no winner
                }
            } else {
                winner = TournamentEngine.knockoutWinner(fixture.teamAId, aRuns, fixture.teamBId, bRuns)
                if (aRuns == bRuns) {
                    // Explain the tie rule right on the card, so nobody wonders why a tie has a winner.
                    val name = teams.firstOrNull { it.teamId == winner }?.teamName ?: ""
                    result = "ম্যাচ টাই — লিগ টেবিলে উপরে থাকায় $name এগিয়ে গেল"
                }
            }
        }
        return FixtureScore(
            teamAScore = a?.scoreLine(),
            teamBScore = b?.scoreLine(),
            isStarted = true,
            isComplete = reading.isComplete,
            resultText = result,
            winnerTeamId = winner
        )
    }

    /**
     * Works out where the tournament is up to right now: still in the league, in the
     * semi-finals, in the final, or FINISHED (with a champion). Also says whether the organizer
     * can press "start the next stage" yet — only once every match of the current stage is done.
     */
    suspend fun computeProgress(tournamentId: String): TournamentProgress {
        val teams = tournamentTeamDao.getForTournamentOnce(tournamentId)
        val fixtures = fixtureDao.getForTournamentOnce(tournamentId)
        val scores = fixtures.associate { it.id to fixtureScore(it, teams) }
        fun nameOf(teamId: String?) = teams.firstOrNull { it.teamId == teamId }?.teamName

        val league = fixtures.filter { it.stage == FixtureStage.LEAGUE }
        val semis = fixtures.filter { it.stage == FixtureStage.SEMI_FINAL }
        val finals = fixtures.filter { it.stage == FixtureStage.FINAL }
        // 4+ teams -> top 4 reach the semi-finals; 2-3 teams -> top 2 go straight to the final.
        val qualifyCount = if (teams.size >= 4) 4 else 2
        fun doneCount(list: List<TournamentFixtureEntity>) = list.count { scores[it.id]?.isComplete == true }

        return when {
            // The final exists: either it's still to be played, or it's done and we have a champion.
            finals.isNotEmpty() -> {
                val final = finals.first()
                val score = scores[final.id]
                val championId = score?.winnerTeamId
                if (score?.isComplete == true && championId != null) {
                    val runnerUpId = if (championId == final.teamAId) final.teamBId else final.teamAId
                    TournamentProgress(
                        phase = TournamentPhase.FINISHED, stagePlayed = 1, stageTotal = 1,
                        qualifyCount = qualifyCount,
                        championTeamId = championId, championName = nameOf(championId), runnerUpName = nameOf(runnerUpId)
                    )
                } else {
                    TournamentProgress(phase = TournamentPhase.FINAL, stagePlayed = 0, stageTotal = 1, qualifyCount = qualifyCount)
                }
            }
            semis.isNotEmpty() -> TournamentProgress(
                phase = TournamentPhase.SEMI_FINALS,
                stagePlayed = doneCount(semis), stageTotal = semis.size,
                canAdvance = doneCount(semis) == semis.size,
                nextStageLabel = FixtureStage.label(FixtureStage.FINAL),
                qualifyCount = qualifyCount
            )
            teams.size < 2 -> TournamentProgress(phase = TournamentPhase.SETUP, qualifyCount = qualifyCount)
            else -> TournamentProgress(
                phase = TournamentPhase.LEAGUE,
                stagePlayed = doneCount(league), stageTotal = league.size,
                canAdvance = league.isNotEmpty() && doneCount(league) == league.size,
                nextStageLabel = FixtureStage.label(if (teams.size >= 4) FixtureStage.SEMI_FINAL else FixtureStage.FINAL),
                qualifyCount = qualifyCount
            )
        }
    }

    /**
     * Creates the NEXT stage's matches, once every match of the current stage is finished:
     *  - league done -> semi-finals (1st v 4th, 2nd v 3rd), or straight to the final with 2-3 teams
     *  - semi-finals done -> the final, between the two semi-final winners
     * Does nothing if the current stage isn't finished yet (so a double-tap can't make it twice).
     */
    suspend fun advanceToNextStage(tournamentId: String) {
        val progress = computeProgress(tournamentId)
        if (!progress.canAdvance) return
        val teams = tournamentTeamDao.getForTournamentOnce(tournamentId)
        val fixtures = fixtureDao.getForTournamentOnce(tournamentId)
        var nextRound = (fixtures.maxOfOrNull { it.round } ?: 0) + 1
        // The final league table — used both to seed the knockouts and to break ties.
        val rankedIds = computeStandings(tournamentId).map { it.teamId }

        val newPairs: List<Pair<String, String>>
        val newStage: String
        if (progress.phase == TournamentPhase.LEAGUE) {
            val (stage, pairs) = TournamentEngine.knockoutPairings(rankedIds) ?: return
            newStage = stage
            newPairs = pairs
        } else {
            // Semi-finals are done: find each one's winner, in the order the semis were made.
            val winners = fixtures.filter { it.stage == FixtureStage.SEMI_FINAL }
                .sortedBy { it.round }
                .mapNotNull { fixtureScore(it, teams).winnerTeamId }
            if (winners.size != 2) return
            // Put whichever finalist finished higher in the league first (as "Team A"), so the
            // knockout tie rule works the same way in the final too.
            val ordered = winners.sortedBy { id -> rankedIds.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
            newStage = FixtureStage.FINAL
            newPairs = listOf(ordered[0] to ordered[1])
        }

        // Each knockout match gets its own round number, so "Semi-final 1" and "Semi-final 2"
        // always show in the same order.
        val newFixtures = newPairs.map { (a, b) ->
            TournamentFixtureEntity(
                id = UUID.randomUUID().toString(), tournamentId = tournamentId,
                round = nextRound++, teamAId = a, teamBId = b, stage = newStage
            )
        }
        fixtureDao.upsertAll(newFixtures)
        runCatching { newFixtures.forEach { pushFixtureToCloud(tournamentId, it) } }
    }

    /**
     * Adds up every player's runs/wickets/sixes/fours/catches/stumpings across ALL finished
     * matches in this tournament (Orange Cap, Purple Cap, and the rest of the Statistics tab's
     * card grid), plus tracks the single best batting innings (Highest Score) and single best
     * bowling innings (Best Bowling) seen anywhere in the tournament.
     */
    suspend fun computeLeaderboards(tournamentId: String): TournamentLeaderboards {
        val fixtures = fixtureDao.getForTournamentOnce(tournamentId).filter { it.matchId != null }
        // Plain maps we build up by hand: player name -> their running total so far.
        val runs = mutableMapOf<String, Int>()
        val wickets = mutableMapOf<String, Int>()
        val sixes = mutableMapOf<String, Int>()
        val fours = mutableMapOf<String, Int>()
        val catches = mutableMapOf<String, Int>()
        val stumpings = mutableMapOf<String, Int>()
        val ballsFaced = mutableMapOf<String, Int>()
        val maidens = mutableMapOf<String, Int>()
        // Per-bowler running totals kept SEPARATELY from the summed `wickets` map above, since
        // economy needs both a runs-conceded total AND a legal-balls total together to work out
        // a rate at the very end, not just a single running number.
        val economyRunsConceded = mutableMapOf<String, Int>()
        val economyLegalBalls = mutableMapOf<String, Int>()
        var highestScore: LeaderboardEntry? = null
        var bestBowling: BestBowlingEntry? = null
        var bestPartnership: PartnershipEntry? = null
        var fastestFifty: LeaderboardEntry? = null
        var fastestHundred: LeaderboardEntry? = null
        var inningsMostSixes: LeaderboardEntry? = null
        var inningsMostFours: LeaderboardEntry? = null

        for (fixture in fixtures) {
            val matchId = fixture.matchId ?: continue
            for (summary in matchRepository.getFinalInningsStates(matchId)) {
                // Add this match's figures on top of whatever the player already had.
                summary.state.batsmanFigures.forEach { (name, fig) ->
                    runs[name] = (runs[name] ?: 0) + fig.runs
                    sixes[name] = (sixes[name] ?: 0) + fig.sixes
                    fours[name] = (fours[name] ?: 0) + fig.fours
                    ballsFaced[name] = (ballsFaced[name] ?: 0) + fig.ballsFaced
                    if (highestScore == null || fig.runs > highestScore!!.value) highestScore = LeaderboardEntry(name, fig.runs)
                    if (inningsMostSixes == null || fig.sixes > inningsMostSixes!!.value) inningsMostSixes = LeaderboardEntry(name, fig.sixes)
                    if (inningsMostFours == null || fig.fours > inningsMostFours!!.value) inningsMostFours = LeaderboardEntry(name, fig.fours)
                    fig.ballsAtFifty?.let { balls ->
                        if (fastestFifty == null || balls < fastestFifty!!.value) fastestFifty = LeaderboardEntry(name, balls)
                    }
                    fig.ballsAtHundred?.let { balls ->
                        if (fastestHundred == null || balls < fastestHundred!!.value) fastestHundred = LeaderboardEntry(name, balls)
                    }
                }
                summary.state.bowlerFigures.forEach { (name, fig) ->
                    wickets[name] = (wickets[name] ?: 0) + fig.wickets
                    maidens[name] = (maidens[name] ?: 0) + fig.maidens
                    economyRunsConceded[name] = (economyRunsConceded[name] ?: 0) + fig.runsConceded
                    economyLegalBalls[name] = (economyLegalBalls[name] ?: 0) + fig.legalBalls
                    // "Best" = most wickets first, then fewest runs conceded as a tie-breaker —
                    // same rule CareerStats.withBowling uses for a player's own career-best.
                    val current = bestBowling
                    val isNewBest = current == null || fig.wickets > current.wickets ||
                        (fig.wickets == current.wickets && fig.runsConceded < current.runsConceded)
                    if (isNewBest) bestBowling = BestBowlingEntry(name, fig.wickets, fig.runsConceded)
                }
                summary.state.fieldingFigures.forEach { (name, fig) ->
                    catches[name] = (catches[name] ?: 0) + fig.catches
                    stumpings[name] = (stumpings[name] ?: 0) + fig.stumpings
                }
                // The names here are the SAME player-name convention used everywhere else in this
                // app (see the class doc comment on StatsRepository) — no extra lookup needed.
                summary.state.partnerships.forEach { p ->
                    if (bestPartnership == null || p.runs > bestPartnership!!.runs) {
                        bestPartnership = PartnershipEntry(p.batterAId, p.batterBId, p.runs)
                    }
                }
            }
        }

        // Sort every map highest-first and keep only the top 10 for each leaderboard.
        fun top10(map: Map<String, Int>) = map.entries.sortedByDescending { it.value }.take(10).map { LeaderboardEntry(it.key, it.value) }
        // Best (lowest) economy, only among bowlers who've bowled at least one full over — a
        // single wicketless ball would otherwise show a misleading "0.0" economy as "best".
        val bestEconomy = economyLegalBalls.entries
            .filter { it.value >= 6 }
            .minByOrNull { (name, legalBalls) -> (economyRunsConceded[name] ?: 0) * 6.0 / legalBalls }
            ?.let { (name, legalBalls) -> EconomyEntry(name, (economyRunsConceded[name] ?: 0) * 6.0 / legalBalls) }

        return TournamentLeaderboards(
            orangeCap = top10(runs),
            purpleCap = top10(wickets),
            highestScore = highestScore,
            bestBowling = bestBowling,
            mostSixes = top10(sixes),
            mostFours = top10(fours),
            mostCatches = top10(catches),
            mostStumpings = top10(stumpings),
            bestPartnership = bestPartnership,
            mostBallsFaced = top10(ballsFaced),
            fastestFifty = fastestFifty,
            fastestHundred = fastestHundred,
            mostMaidens = top10(maidens),
            bestEconomy = bestEconomy,
            inningsMostSixes = inningsMostSixes,
            inningsMostFours = inningsMostFours
        )
    }

    // ---- Cloud-sourced equivalents below, for browsing a tournament this device does NOT
    // organize (found via Search) — same shapes/math as the local versions above, just reading
    // from Firestore's shared "tournaments" + "live_matches" collections instead of local Room. ----

    /** Cloud equivalent of observeTournamentTeams — a one-shot read, since Search-found tournaments aren't kept live-synced locally. */
    suspend fun getCloudTeams(tournamentId: String): List<TournamentTeamEntity> =
        cloudTournamentTeamsRef(tournamentId).get().await().documents.map { doc ->
            TournamentTeamEntity(
                id = doc.id,
                tournamentId = tournamentId,
                teamId = doc.getString("teamId") ?: "",
                teamName = doc.getString("teamName") ?: ""
            )
        }

    /** Cloud equivalent of observeFixtures — see getCloudTeams. */
    suspend fun getCloudFixtures(tournamentId: String): List<TournamentFixtureEntity> =
        cloudFixturesRef(tournamentId).get().await().documents.map { doc ->
            TournamentFixtureEntity(
                id = doc.id,
                tournamentId = tournamentId,
                round = (doc.getLong("round") ?: 0L).toInt(),
                teamAId = doc.getString("teamAId") ?: "",
                teamBId = doc.getString("teamBId") ?: "",
                matchId = doc.getString("matchId"),
                stage = doc.getString("stage") ?: FixtureStage.LEAGUE
            )
        }

    /** Cloud equivalent of computeStandings — reads each played fixture's match from Firestore instead of local Room. */
    suspend fun computeCloudStandings(tournamentId: String): List<TeamStanding> {
        val teams = getCloudTeams(tournamentId)
        // Only LEAGUE matches count towards the points table — semi-finals and the final don't.
        val fixtures = getCloudFixtures(tournamentId).filter { it.matchId != null && it.stage == FixtureStage.LEAGUE }

        val results = mutableListOf<CompletedMatchResult>()
        for (fixture in fixtures) {
            val matchId = fixture.matchId ?: continue
            val matchInfo = liveBroadcastRepository.getMatchInfoOnce(matchId) ?: continue
            val innings = liveBroadcastRepository.getAllInningsOnce(matchId)
            if (innings.size < 2) continue // match not finished yet

            val teamAName = teams.firstOrNull { it.teamId == fixture.teamAId }?.teamName
            val teamBName = teams.firstOrNull { it.teamId == fixture.teamBId }?.teamName
            val aInnings = innings.firstOrNull { it.battingTeamName == teamAName }
            val bInnings = innings.firstOrNull { it.battingTeamName == teamBName }
            if (aInnings == null || bInnings == null) continue

            results.add(
                CompletedMatchResult(
                    teamAId = fixture.teamAId,
                    teamBId = fixture.teamBId,
                    oversLimit = matchInfo.oversLimit,
                    teamARuns = aInnings.state.totalRuns,
                    teamALegalBalls = aInnings.state.legalBallsBowled,
                    teamAAllOut = aInnings.state.totalWickets >= 10,
                    teamBRuns = bInnings.state.totalRuns,
                    teamBLegalBalls = bInnings.state.legalBallsBowled,
                    teamBAllOut = bInnings.state.totalWickets >= 10
                )
            )
        }
        return TournamentEngine.computeStandings(teams.map { it.teamId to it.teamName }, results)
    }

    /** Cloud equivalent of computeLeaderboards — see computeCloudStandings. */
    suspend fun computeCloudLeaderboards(tournamentId: String): TournamentLeaderboards {
        val fixtures = getCloudFixtures(tournamentId).filter { it.matchId != null }
        val runs = mutableMapOf<String, Int>()
        val wickets = mutableMapOf<String, Int>()

        for (fixture in fixtures) {
            val matchId = fixture.matchId ?: continue
            for (summary in liveBroadcastRepository.getAllInningsOnce(matchId)) {
                summary.state.batsmanFigures.forEach { (name, fig) -> runs[name] = (runs[name] ?: 0) + fig.runs }
                summary.state.bowlerFigures.forEach { (name, fig) -> wickets[name] = (wickets[name] ?: 0) + fig.wickets }
            }
        }
        return TournamentLeaderboards(
            orangeCap = runs.entries.sortedByDescending { it.value }.take(10).map { LeaderboardEntry(it.key, it.value) },
            purpleCap = wickets.entries.sortedByDescending { it.value }.take(10).map { LeaderboardEntry(it.key, it.value) }
        )
    }
}
