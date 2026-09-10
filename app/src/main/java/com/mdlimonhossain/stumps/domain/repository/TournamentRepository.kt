package com.mdlimonhossain.stumps.domain.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

/** One row of a leaderboard: a player's name and their total (runs for Orange Cap, wickets for Purple Cap). */
data class LeaderboardEntry(val playerName: String, val value: Int)

/** Orange Cap = the tournament's top run-scorer. Purple Cap = the tournament's top wicket-taker. Same idea as the IPL awards. */
data class TournamentLeaderboards(val orangeCap: List<LeaderboardEntry>, val purpleCap: List<LeaderboardEntry>)

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
            createdAt = getLong("createdAt") ?: 0L
        )
    }

    /**
     * Sets up a brand new tournament: saves the tournament itself, remembers which teams are
     * taking part, and automatically builds the full match schedule (every team plays every
     * other team once — see TournamentEngine.generateRoundRobinFixtures for the actual math).
     */
    suspend fun createTournament(
        organizerUid: String,
        name: String,
        oversPerMatch: Int,
        venue: String?,
        selectedTeamIds: List<String>
    ): String {
        val tournamentId = UUID.randomUUID().toString()
        val tournament = TournamentEntity(
            id = tournamentId, name = name, format = "ROUND_ROBIN",
            oversPerMatch = oversPerMatch, venue = venue,
            organizerUid = organizerUid, createdAt = System.currentTimeMillis()
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
                    "createdAt" to tournament.createdAt
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

    private fun cloudTournamentTeamsRef(tournamentId: String) = cloudTournamentsRef().document(tournamentId).collection("teams")
    private fun cloudFixturesRef(tournamentId: String) = cloudTournamentsRef().document(tournamentId).collection("fixtures")

    private suspend fun pushFixtureToCloud(tournamentId: String, fixture: TournamentFixtureEntity) {
        cloudFixturesRef(tournamentId).document(fixture.id).set(
            mapOf(
                "round" to fixture.round,
                "teamAId" to fixture.teamAId,
                "teamBId" to fixture.teamBId,
                "matchId" to fixture.matchId
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
        // Only look at fixtures that have actually had a match started (matchId != null).
        val fixtures = fixtureDao.getForTournamentOnce(tournamentId).filter { it.matchId != null }

        val results = mutableListOf<CompletedMatchResult>()
        for (fixture in fixtures) {
            val matchId = fixture.matchId ?: continue
            val match = matchRepository.getMatchOnce(matchId) ?: continue
            val innings = matchRepository.getFinalInningsStates(matchId)
            if (innings.size < 2) continue // match not finished yet (still on the first innings)

            // Each fixture's match creates its OWN fresh team rows (see MatchRepository.createQuickMatch),
            // so we can't compare ids directly — instead we match up innings to fixture teams
            // by comparing TEAM NAMES, which are always kept the same on purpose.
            val teamAName = teams.firstOrNull { it.teamId == fixture.teamAId }?.teamName
            val teamBName = teams.firstOrNull { it.teamId == fixture.teamBId }?.teamName
            val aInnings = innings.firstOrNull { it.battingTeamName == teamAName }
            val bInnings = innings.firstOrNull { it.battingTeamName == teamBName }
            if (aInnings == null || bInnings == null) continue

            // Boil this whole match down to just the numbers TournamentEngine needs for the
            // points table and Net Run Rate math (10 wickets down = "all out").
            results.add(
                CompletedMatchResult(
                    teamAId = fixture.teamAId,
                    teamBId = fixture.teamBId,
                    oversLimit = match.oversLimit,
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

    /**
     * Adds up every player's runs and wickets across ALL finished matches in this tournament,
     * then returns the top 10 for each — that's the Orange Cap (runs) and Purple Cap (wickets).
     */
    suspend fun computeLeaderboards(tournamentId: String): TournamentLeaderboards {
        val fixtures = fixtureDao.getForTournamentOnce(tournamentId).filter { it.matchId != null }
        // Plain maps we build up by hand: player name -> their running total so far.
        val runs = mutableMapOf<String, Int>()
        val wickets = mutableMapOf<String, Int>()

        for (fixture in fixtures) {
            val matchId = fixture.matchId ?: continue
            for (summary in matchRepository.getFinalInningsStates(matchId)) {
                // Add this match's figures on top of whatever the player already had.
                summary.state.batsmanFigures.forEach { (name, fig) -> runs[name] = (runs[name] ?: 0) + fig.runs }
                summary.state.bowlerFigures.forEach { (name, fig) -> wickets[name] = (wickets[name] ?: 0) + fig.wickets }
            }
        }

        // Sort both maps highest-first and keep only the top 10 for each leaderboard.
        return TournamentLeaderboards(
            orangeCap = runs.entries.sortedByDescending { it.value }.take(10).map { LeaderboardEntry(it.key, it.value) },
            purpleCap = wickets.entries.sortedByDescending { it.value }.take(10).map { LeaderboardEntry(it.key, it.value) }
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
                matchId = doc.getString("matchId")
            )
        }

    /** Cloud equivalent of computeStandings — reads each played fixture's match from Firestore instead of local Room. */
    suspend fun computeCloudStandings(tournamentId: String): List<TeamStanding> {
        val teams = getCloudTeams(tournamentId)
        val fixtures = getCloudFixtures(tournamentId).filter { it.matchId != null }

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
