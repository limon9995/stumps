package com.mdlimonhossain.stumps.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Test

class TournamentEngineTest {

    @Test
    fun win_awardsTwoPoints_lossAwardsZero() {
        val result = CompletedMatchResult(
            teamAId = "A", teamBId = "B", oversLimit = 20,
            teamARuns = 150, teamALegalBalls = 120, teamAAllOut = false,
            teamBRuns = 120, teamBLegalBalls = 120, teamBAllOut = true
        )
        val standings = TournamentEngine.computeStandings(listOf("A" to "Team A", "B" to "Team B"), listOf(result))
        val a = standings.first { it.teamId == "A" }
        val b = standings.first { it.teamId == "B" }
        assertEquals(2, a.points)
        assertEquals(0, b.points)
        assertEquals(1, a.won)
        assertEquals(1, b.lost)
    }

    @Test
    fun tie_awardsOnePointEach() {
        val result = CompletedMatchResult(
            teamAId = "A", teamBId = "B", oversLimit = 20,
            teamARuns = 150, teamALegalBalls = 120, teamAAllOut = false,
            teamBRuns = 150, teamBLegalBalls = 120, teamBAllOut = false
        )
        val standings = TournamentEngine.computeStandings(listOf("A" to "Team A", "B" to "Team B"), listOf(result))
        assertEquals(1, standings.first { it.teamId == "A" }.points)
        assertEquals(1, standings.first { it.teamId == "B" }.points)
    }

    @Test
    fun allOutTeam_isCreditedFullOversForOwnRate() {
        // Team A scores 100 all out in just 10 overs (bowled out cheaply) — NRR must charge
        // them the full 20-over quota, not 10, or being skittled would inflate their rate.
        val result = CompletedMatchResult(
            teamAId = "A", teamBId = "B", oversLimit = 20,
            teamARuns = 100, teamALegalBalls = 60, teamAAllOut = true,
            teamBRuns = 101, teamBLegalBalls = 60, teamBAllOut = false
        )
        val standings = TournamentEngine.computeStandings(listOf("A" to "Team A", "B" to "Team B"), listOf(result))
        val a = standings.first { it.teamId == "A" }
        // 100 runs / 20 overs = 5.0 for-rate, not 100/10 = 10.0
        assertEquals(20.0, a.oversFor, 0.0001)
        assertEquals(5.0, 100.0 / a.oversFor, 0.0001)
    }

    @Test
    fun notAllOutTeam_usesActualOversForRate() {
        val result = CompletedMatchResult(
            teamAId = "A", teamBId = "B", oversLimit = 20,
            teamARuns = 150, teamALegalBalls = 120, teamAAllOut = false,
            teamBRuns = 100, teamBLegalBalls = 60, teamBAllOut = true
        )
        val standings = TournamentEngine.computeStandings(listOf("A" to "Team A", "B" to "Team B"), listOf(result))
        val a = standings.first { it.teamId == "A" }
        assertEquals(20.0, a.oversFor, 0.0001) // 120 legal balls / 6 = 20.0 overs
    }

    @Test
    fun netRunRate_combinesForAndAgainstAcrossMatches() {
        val r1 = CompletedMatchResult("A", "B", 20, 160, 120, false, 140, 120, false)
        val r2 = CompletedMatchResult("A", "C", 20, 180, 120, false, 100, 120, true)
        val standings = TournamentEngine.computeStandings(
            listOf("A" to "Team A", "B" to "Team B", "C" to "Team C"), listOf(r1, r2)
        )
        val a = standings.first { it.teamId == "A" }
        // for: (160+180)/(20+20)=8.5, against: (140+100)/(20+20)=6.0 -> nrr 2.5
        assertEquals(2.5, a.netRunRate, 0.0001)
    }

    @Test
    fun standingsSortedByPointsThenNrr() {
        val r1 = CompletedMatchResult("A", "B", 20, 200, 120, false, 100, 120, true)
        val r2 = CompletedMatchResult("C", "B", 20, 150, 120, false, 149, 120, false)
        val standings = TournamentEngine.computeStandings(
            listOf("A" to "Team A", "B" to "Team B", "C" to "Team C"), listOf(r1, r2)
        )
        assertEquals(listOf("A", "C", "B"), standings.map { it.teamId })
    }

    @Test
    fun roundRobin_generatesEveryPairOnce() {
        val fixtures = TournamentEngine.generateRoundRobinFixtures(listOf("A", "B", "C", "D"))
        assertEquals(6, fixtures.size) // 4 choose 2
        assertEquals(setOf("A" to "B", "A" to "C", "A" to "D", "B" to "C", "B" to "D", "C" to "D"), fixtures.toSet())
    }

    // 4+ teams: semi-finals are 1st v 4th and 2nd v 3rd (5th place and below are knocked out).
    @Test
    fun knockoutPairings_fourOrMoreTeams_makesSemiFinals() {
        val result = TournamentEngine.knockoutPairings(listOf("A", "B", "C", "D", "E"))
        assertEquals("SEMI_FINAL" to listOf("A" to "D", "B" to "C"), result)
    }

    // 2-3 teams: no semi-finals, the top two go straight to the final.
    @Test
    fun knockoutPairings_twoOrThreeTeams_goesStraightToFinal() {
        assertEquals("FINAL" to listOf("A" to "B"), TournamentEngine.knockoutPairings(listOf("A", "B", "C")))
        assertEquals(null, TournamentEngine.knockoutPairings(listOf("A")))
    }

    // A knockout tie can't be left without a winner — the higher-ranked Team A goes through.
    @Test
    fun knockoutWinner_tieGoesToHigherRankedTeamA() {
        assertEquals("A", TournamentEngine.knockoutWinner("A", 100, "B", 100))
        assertEquals("B", TournamentEngine.knockoutWinner("A", 100, "B", 101))
        assertEquals("A", TournamentEngine.knockoutWinner("A", 150, "B", 90))
    }
}
