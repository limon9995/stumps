package com.mdlimonhossain.stumps.integration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mdlimonhossain.stumps.data.local.db.AppDatabase
import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.domain.repository.MatchRepository
import com.mdlimonhossain.stumps.domain.repository.TeamRepository
import com.mdlimonhossain.stumps.domain.repository.TournamentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the ENTIRE tournament feature end-to-end against a real (in-memory) Room database —
 * team creation, tournament + round-robin fixture generation, starting a fixture from saved
 * rosters, ball-by-ball scoring through two innings, live-state reactivity, and finally points
 * table / NRR / leaderboard computation. This is the closest verification possible without a
 * physical device or emulator: every repository and DAO runs for real, only the UI layer is absent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TournamentFlowIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var teamRepository: TeamRepository
    private lateinit var matchRepository: MatchRepository
    private lateinit var tournamentRepository: TournamentRepository

    private val organizerUid = "organizer-uid-1"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamRepository = TeamRepository(db.teamDao(), db.playerDao())
        matchRepository = MatchRepository(db.teamDao(), db.playerDao(), db.matchDao(), db.inningsDao(), db.ballDao())
        tournamentRepository = TournamentRepository(
            db.tournamentDao(), db.tournamentTeamDao(), db.tournamentFixtureDao(), teamRepository, matchRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun squad(prefix: String) = (1..11).map { "$prefix$it" }

    @Test
    fun fullTournamentFlow_teamsToStandingsAndLeaderboards() = runBlocking {
        // ---- 1. Saved teams (prerequisite for a tournament) ----
        val dhakaPlayers = squad("Dhaka_P")
        val ctgPlayers = squad("Ctg_P")
        val sylhetPlayers = squad("Sylhet_P")

        val dhakaId = teamRepository.createTeam(organizerUid, "Dhaka Tigers", dhakaPlayers.map { it to PlayerRole.BATSMAN })
        val ctgId = teamRepository.createTeam(organizerUid, "Chittagong Kings", ctgPlayers.map { it to PlayerRole.BATSMAN })
        val sylhetId = teamRepository.createTeam(organizerUid, "Sylhet Strikers", sylhetPlayers.map { it to PlayerRole.BATSMAN })

        assertEquals(11, teamRepository.getPlayersOnce(dhakaId).size)

        // ---- 2. Tournament + round-robin fixtures ----
        val tournamentId = tournamentRepository.createTournament(
            organizerUid, "Test Premier League", oversPerMatch = 2, venue = "Mirpur",
            selectedTeamIds = listOf(dhakaId, ctgId, sylhetId)
        )
        val fixtures = tournamentRepository.observeFixtures(tournamentId).first()
        assertEquals("round robin of 3 teams must produce 3 fixtures", 3, fixtures.size)

        val dhakaVsCtg = fixtures.first { it.teamAId == dhakaId && it.teamBId == ctgId }
        val dhakaVsSylhet = fixtures.first { it.teamAId == dhakaId && it.teamBId == sylhetId }
        val ctgVsSylhet = fixtures.first { it.teamAId == ctgId && it.teamBId == sylhetId }

        // ---- 3. Fixture 1: Dhaka 12/0 (2 overs) vs Chittagong chase 13 in 3 balls ----
        playMatch(
            fixture = dhakaVsCtg, tournamentId = tournamentId,
            teamAPlayers = dhakaPlayers, teamBPlayers = ctgPlayers,
            firstInningsBalls = List(12) { BallPlan(runs = 1) },
            secondInningsBalls = listOf(BallPlan(runs = 6), BallPlan(runs = 6), BallPlan(runs = 1))
        )

        // ---- 4. Fixture 2: Dhaka 24/0 (2 overs) vs Sylhet 10/2 (2 overs, doesn't chase down) ----
        playMatch(
            fixture = dhakaVsSylhet, tournamentId = tournamentId,
            teamAPlayers = dhakaPlayers, teamBPlayers = sylhetPlayers,
            firstInningsBalls = List(12) { BallPlan(runs = 2) },
            secondInningsBalls = List(10) { BallPlan(runs = 1) } +
                listOf(BallPlan(runs = 0, isWicket = true), BallPlan(runs = 0, isWicket = true)),
            fixedBowlerForSecondInnings = "Dhaka_P1"
        )

        // ---- 5. Fixture 3: Chittagong 12/0 (2 overs) vs Sylhet chase 13 in 3 balls ----
        playMatch(
            fixture = ctgVsSylhet, tournamentId = tournamentId,
            teamAPlayers = ctgPlayers, teamBPlayers = sylhetPlayers,
            firstInningsBalls = List(12) { BallPlan(runs = 1) },
            secondInningsBalls = listOf(BallPlan(runs = 6), BallPlan(runs = 6), BallPlan(runs = 1))
        )

        // ---- 6. Points table: all three teams finish 1-1, so ranking is decided entirely by NRR ----
        val standings = tournamentRepository.computeStandings(tournamentId)
        assertEquals(3, standings.size)
        standings.forEach { assertEquals("every team played exactly 2 matches", 2, it.played) }
        standings.forEach { assertEquals("every team has exactly 1 win and 1 loss -> 2 points", 2, it.points) }

        assertEquals(
            "NRR tie-break must order Sylhet > Chittagong > Dhaka",
            listOf("Sylhet Strikers", "Chittagong Kings", "Dhaka Tigers"),
            standings.map { it.teamName }
        )
        val chittagong = standings.first { it.teamName == "Chittagong Kings" }
        assertEquals(0.0, chittagong.netRunRate, 0.0001)
        val sylhet = standings.first { it.teamName == "Sylhet Strikers" }
        assertEquals(0.2, sylhet.netRunRate, 0.0001)
        val dhaka = standings.first { it.teamName == "Dhaka Tigers" }
        assertEquals(-0.2, dhaka.netRunRate, 0.0001)

        // ---- 7. Leaderboards: Purple Cap must credit the one bowler we forced onto every ball ----
        val leaderboards = tournamentRepository.computeLeaderboards(tournamentId)
        val purpleCapLeader = leaderboards.purpleCap.first()
        assertEquals("Dhaka_P1", purpleCapLeader.playerName)
        assertEquals(2, purpleCapLeader.value)

        val totalRunsAcrossOrangeCap = leaderboards.orangeCap.sumOf { it.value }
        val totalRunsAcrossAllInnings = 12 + 13 + 24 + 10 + 12 + 13
        assertEquals(totalRunsAcrossAllInnings, totalRunsAcrossOrangeCap)
        assertTrue(
            "orange cap must be sorted highest-first",
            leaderboards.orangeCap.zipWithNext().all { (a, b) -> a.value >= b.value }
        )
    }

    /**
     * Plays a WHOLE 4-team tournament, start to finish: 6 league matches -> 2 semi-finals ->
     * the final -> a champion. In every match here the team batting first scores 12 and the
     * chasing team scores 0, so "Team A" of each fixture always wins — which makes the final
     * league table easy to predict: T1 (3 wins), T2 (2), T3 (1), T4 (0).
     */
    @Test
    fun fullTournament_leagueThenSemiFinalsThenFinal_crownsChampion() = runBlocking {
        val squads = (1..4).map { squad("T${it}_P") }
        val teamIds = squads.mapIndexed { i, players ->
            teamRepository.createTeam(organizerUid, "Team ${i + 1}", players.map { it to PlayerRole.BATSMAN })
        }
        val tournamentId = tournamentRepository.createTournament(
            organizerUid, "Knockout Cup", oversPerMatch = 2, venue = null, selectedTeamIds = teamIds
        )
        fun playersOf(teamId: String) = squads[teamIds.indexOf(teamId)]
        suspend fun playAll(stage: String) {
            val toPlay = tournamentRepository.observeFixtures(tournamentId).first().filter { it.stage == stage && it.matchId == null }
            for (f in toPlay) {
                playMatch(
                    fixture = f, tournamentId = tournamentId,
                    teamAPlayers = playersOf(f.teamAId), teamBPlayers = playersOf(f.teamBId),
                    firstInningsBalls = List(12) { BallPlan(runs = 1) },
                    secondInningsBalls = List(12) { BallPlan(runs = 0) }
                )
            }
        }

        // ---- League: can't advance until every league match is done ----
        var progress = tournamentRepository.computeProgress(tournamentId)
        assertEquals(com.mdlimonhossain.stumps.domain.repository.TournamentPhase.LEAGUE, progress.phase)
        assertEquals(false, progress.canAdvance)
        playAll("LEAGUE")
        progress = tournamentRepository.computeProgress(tournamentId)
        assertEquals(6, progress.stagePlayed)
        assertTrue("league finished -> semi-finals can start", progress.canAdvance)

        // ---- Semi-finals: 1st v 4th, 2nd v 3rd ----
        tournamentRepository.advanceToNextStage(tournamentId)
        tournamentRepository.advanceToNextStage(tournamentId) // a double-tap must NOT create a second set
        val semis = tournamentRepository.observeFixtures(tournamentId).first().filter { it.stage == "SEMI_FINAL" }
        assertEquals(2, semis.size)
        assertEquals(setOf(teamIds[0] to teamIds[3], teamIds[1] to teamIds[2]), semis.map { it.teamAId to it.teamBId }.toSet())
        assertEquals(com.mdlimonhossain.stumps.domain.repository.TournamentPhase.SEMI_FINALS, tournamentRepository.computeProgress(tournamentId).phase)

        // Semi-final results must NOT change the league points table.
        playAll("SEMI_FINAL")
        assertEquals(listOf(6, 4, 2, 0), tournamentRepository.computeStandings(tournamentId).map { it.points })

        // ---- Final: the two semi-final winners (T1 and T2) ----
        tournamentRepository.advanceToNextStage(tournamentId)
        val finals = tournamentRepository.observeFixtures(tournamentId).first().filter { it.stage == "FINAL" }
        assertEquals(1, finals.size)
        assertEquals(teamIds[0] to teamIds[1], finals.first().teamAId to finals.first().teamBId)
        playAll("FINAL")

        // ---- Champion ----
        progress = tournamentRepository.computeProgress(tournamentId)
        assertEquals(com.mdlimonhossain.stumps.domain.repository.TournamentPhase.FINISHED, progress.phase)
        assertEquals("Team 1", progress.championName)
        assertEquals("Team 2", progress.runnerUpName)

        // Every fixture's mini scoreboard is filled in, and the final's winner is Team 1.
        val scores = tournamentRepository.computeFixtureScores(tournamentId)
        assertEquals(9, scores.size)
        assertTrue(scores.values.all { it.isComplete })
        assertEquals(teamIds[0], scores[finals.first().id]?.winnerTeamId)
        assertEquals("12/0 (2.0)", scores[finals.first().id]?.teamAScore)
    }

    @Test
    fun liveInningsReactsToLiveBroadcastToggle_notJustToNewBalls() = runBlocking {
        val teamAId = teamRepository.createTeam(organizerUid, "Team A", squad("A_P").map { it to PlayerRole.BATSMAN })
        val teamBId = teamRepository.createTeam(organizerUid, "Team B", squad("B_P").map { it to PlayerRole.BATSMAN })
        val teamA = teamRepository.getTeamOnce(teamAId)!!
        val teamB = teamRepository.getTeamOnce(teamBId)!!

        val matchId = matchRepository.createQuickMatch(
            createdByUid = organizerUid,
            teamAName = teamA.name, teamAPlayerNames = teamRepository.getPlayersOnce(teamAId).map { it.name },
            teamBName = teamB.name, teamBPlayerNames = teamRepository.getPlayersOnce(teamBId).map { it.name },
            oversLimit = 2, tossWinnerIsTeamA = true, tossDecisionIsBat = true,
            openingStrikerId = "A_P1", openingNonStrikerId = "A_P2", openingBowlerId = "B_P1"
        )
        val inningsId = matchRepository.observeInningsForMatch(matchId).first().first().id

        assertEquals(false, matchRepository.observeLiveInnings(inningsId).first()!!.match.isLive)

        matchRepository.setLive(matchId, true, "ABC123")

        val afterGoingLive = matchRepository.observeLiveInnings(inningsId).first()!!
        assertTrue(afterGoingLive.match.isLive)
        assertEquals("ABC123", afterGoingLive.match.shareCode)
    }

    private data class BallPlan(val runs: Int, val isWicket: Boolean = false)

    private suspend fun playMatch(
        fixture: com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity,
        tournamentId: String,
        teamAPlayers: List<String>,
        teamBPlayers: List<String>,
        firstInningsBalls: List<BallPlan>,
        secondInningsBalls: List<BallPlan>,
        fixedBowlerForSecondInnings: String? = null
    ) {
        val tournament = tournamentRepository.observeTournament(tournamentId).first()!!

        val (matchId, firstInningsId) = tournamentRepository.startFixtureMatch(
            fixture = fixture, tournament = tournament, organizerUid = organizerUid,
            tossWinnerIsTeamA = true, tossDecisionIsBat = true,
            openingStrikerId = teamAPlayers[0], openingNonStrikerId = teamAPlayers[1], openingBowlerId = teamBPlayers[0]
        )
        assertNotNull(matchId)

        var battingRoster = teamAPlayers
        var wicketCounter = 0

        for (plan in firstInningsBalls) {
            val live = matchRepository.observeLiveInnings(firstInningsId).first()!!
            if (plan.isWicket) {
                wicketCounter++
                matchRepository.recordBall(
                    innings = live.innings, state = live.state,
                    runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
                    isWicket = true, dismissalType = DismissalType.BOWLED,
                    dismissedPlayerId = live.state.strikerId, newBatsmanId = "${battingRoster[0].dropLast(1)}${10 + wicketCounter}",
                    selectedBowlerId = null
                )
            } else {
                matchRepository.recordBall(
                    innings = live.innings, state = live.state,
                    runsOffBat = plan.runs, extraType = null, extraRuns = 0, runsRun = plan.runs,
                    isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null,
                    selectedBowlerId = null
                )
            }
        }

        val firstInningsFinal = matchRepository.observeLiveInnings(firstInningsId).first()!!
        val target = firstInningsFinal.state.totalRuns + 1

        val secondInningsId = matchRepository.startNextInnings(
            matchId = matchId, battingIsTeamA = false,
            openingStrikerId = teamBPlayers[0], openingNonStrikerId = teamBPlayers[1], openingBowlerId = teamAPlayers[0],
            targetRuns = target
        )

        battingRoster = teamBPlayers
        wicketCounter = 0
        for (plan in secondInningsBalls) {
            val live = matchRepository.observeLiveInnings(secondInningsId).first()!!
            if (live.state.totalRuns >= target) break // mirrors ScoringScreen's own "target reached, stop" gate

            if (plan.isWicket) {
                wicketCounter++
                matchRepository.recordBall(
                    innings = live.innings, state = live.state,
                    runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
                    isWicket = true, dismissalType = DismissalType.BOWLED,
                    dismissedPlayerId = live.state.strikerId, newBatsmanId = "${battingRoster[0].dropLast(1)}${10 + wicketCounter}",
                    selectedBowlerId = fixedBowlerForSecondInnings
                )
            } else {
                matchRepository.recordBall(
                    innings = live.innings, state = live.state,
                    runsOffBat = plan.runs, extraType = null, extraRuns = 0, runsRun = plan.runs,
                    isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null,
                    selectedBowlerId = fixedBowlerForSecondInnings
                )
            }
        }
    }
}
