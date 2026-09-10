package com.mdlimonhossain.stumps.domain.scoring

import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val STRIKER = "batsman_A"
private const val NON_STRIKER = "batsman_B"
private const val BOWLER = "bowler_X"

class ScoringEngineTest {

    private fun state(balls: List<BallRecord>, overs: Int = 20, squad: Int = 11) =
        ScoringEngine.computeState(
            balls = balls,
            openingStrikerId = STRIKER,
            openingNonStrikerId = NON_STRIKER,
            openingBowlerId = BOWLER,
            oversLimit = overs,
            squadSize = squad
        )

    private fun normalBall(seq: Int, runs: Int, striker: String = STRIKER, nonStriker: String = NON_STRIKER, bowler: String = BOWLER) =
        BallRecord(
            sequence = seq, bowlerId = bowler, strikerId = striker, nonStrikerId = nonStriker,
            runsOffBat = runs, extraType = null, extraRuns = 0, runsRun = runs,
            isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null
        )

    @Test
    fun singleRun_swapsStrike() {
        val s = state(listOf(normalBall(1, 1)))
        assertEquals(1, s.totalRuns)
        assertEquals(NON_STRIKER, s.strikerId)
        assertEquals(STRIKER, s.nonStrikerId)
    }

    @Test
    fun boundary_doesNotSwapStrike_andCountsAsFour() {
        val s = state(listOf(normalBall(1, 4)))
        assertEquals(4, s.totalRuns)
        assertEquals(STRIKER, s.strikerId)
        assertEquals(1, s.batsmanFigures[STRIKER]?.fours)
    }

    @Test
    fun wide_addsOneRun_notLegalDelivery_batsmanNotCharged() {
        val wide = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = ExtraType.WIDE, extraRuns = 1, runsRun = 0,
            isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null
        )
        val s = state(listOf(wide))
        assertEquals(1, s.totalRuns)
        assertEquals(0, s.legalBallsBowled)
        assertEquals(0, s.batsmanFigures[STRIKER]?.runs ?: 0)
        assertEquals(1, s.bowlerFigures[BOWLER]?.runsConceded)
    }

    @Test
    fun noBall_batsmanCreditedButNotLegal() {
        val noBall = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 4, extraType = ExtraType.NO_BALL, extraRuns = 1, runsRun = 4,
            isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null
        )
        val s = state(listOf(noBall))
        assertEquals(5, s.totalRuns)
        assertEquals(0, s.legalBallsBowled)
        assertEquals(4, s.batsmanFigures[STRIKER]?.runs)
        assertEquals(5, s.bowlerFigures[BOWLER]?.runsConceded)
    }

    @Test
    fun byes_countForTeamButNotBatsmanOrBowler() {
        val bye = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = ExtraType.BYE, extraRuns = 2, runsRun = 2,
            isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null
        )
        val s = state(listOf(bye))
        assertEquals(2, s.totalRuns)
        assertEquals(1, s.legalBallsBowled)
        assertEquals(0, s.batsmanFigures[STRIKER]?.runs ?: 0)
        assertEquals(0, s.bowlerFigures[BOWLER]?.runsConceded)
        assertEquals(STRIKER, s.strikerId)
    }

    @Test
    fun wicket_creditsBowlerAndBringsInNewBatsman() {
        val wicketBall = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
            isWicket = true, dismissalType = DismissalType.BOWLED, dismissedPlayerId = STRIKER,
            newBatsmanId = "batsman_C"
        )
        val s = state(listOf(wicketBall))
        assertEquals(1, s.totalWickets)
        assertEquals(1, s.bowlerFigures[BOWLER]?.wickets)
        assertTrue(s.batsmanFigures[STRIKER]?.isOut == true)
        assertEquals("batsman_C", s.strikerId)
        assertEquals(NON_STRIKER, s.nonStrikerId)
    }

    @Test
    fun runOut_doesNotCreditBowlerWicket() {
        val runOutBall = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 1, extraType = null, extraRuns = 0, runsRun = 1,
            isWicket = true, dismissalType = DismissalType.RUN_OUT, dismissedPlayerId = NON_STRIKER,
            newBatsmanId = "batsman_C"
        )
        val s = state(listOf(runOutBall))
        assertEquals(1, s.totalWickets)
        assertEquals(0, s.bowlerFigures[BOWLER]?.wickets)
    }

    @Test
    fun sixLegalBalls_completeOverAndSwapStrike() {
        val over = (1..6).map { normalBall(it, 0) }
        val s = state(over)
        assertEquals(6, s.legalBallsBowled)
        assertTrue(s.isOverJustCompleted)
        // even total runs across the over (all dot balls) but end-of-over always swaps ends
        assertEquals(NON_STRIKER, s.strikerId)
    }

    @Test
    fun undoIsJustDroppingTheLastBall() {
        val balls = listOf(normalBall(1, 4), normalBall(2, 1))
        val full = state(balls)
        val afterUndo = state(balls.dropLast(1))
        assertEquals(5, full.totalRuns)
        assertEquals(4, afterUndo.totalRuns)
        assertEquals(STRIKER, afterUndo.strikerId)
    }

    @Test
    fun inningsCompletesWhenOversExhausted() {
        val balls = (1..30).map { normalBall(it, 1) }
        val s = state(balls, overs = 5)
        assertEquals(30, s.legalBallsBowled)
        assertTrue(s.isInningsComplete)
    }

    @Test
    fun inningsCompletesWhenAllOutExceptOne() {
        val balls = (1..9).map { seq ->
            BallRecord(
                sequence = seq, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
                runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
                isWicket = true, dismissalType = DismissalType.BOWLED, dismissedPlayerId = STRIKER,
                newBatsmanId = "batsman_$seq"
            )
        }
        val s = state(balls, squad = 11)
        assertEquals(9, s.totalWickets)
        assertFalse(s.isInningsComplete)
    }

    @Test
    fun runsPerOver_bucketsByCompletedOver() {
        // over 1: 1+4+0+0+0+0=5, over 2 (partial): 6+2=8
        val balls = listOf(
            normalBall(1, 1), normalBall(2, 4), normalBall(3, 0), normalBall(4, 0), normalBall(5, 0), normalBall(6, 0),
            normalBall(7, 6), normalBall(8, 2)
        )
        val s = state(balls)
        assertEquals(listOf(5, 8), s.runsPerOver)
    }

    @Test
    fun runsPerOver_noPhantomOverWhenInningsEndsOnBoundary() {
        val balls = (1..6).map { normalBall(it, 1) } // exactly one full over, nothing after
        val s = state(balls)
        assertEquals(listOf(6), s.runsPerOver)
    }

    @Test
    fun shotEvents_onlyRecordedWhenAngleGiven() {
        val withAngle = normalBall(1, 4).copy(shotAngleDegrees = 90)
        val withoutAngle = normalBall(2, 6)
        val s = state(listOf(withAngle, withoutAngle))
        assertEquals(1, s.shotEvents.size)
        assertEquals(90, s.shotEvents.first().angleDegrees)
        assertEquals(4, s.shotEvents.first().runs)
        assertFalse(s.shotEvents.first().isSix)
    }

    @Test
    fun shotEvents_excludedForWides() {
        val wideWithAngle = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = ExtraType.WIDE, extraRuns = 1, runsRun = 0,
            isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null,
            shotAngleDegrees = 45
        )
        val s = state(listOf(wideWithAngle))
        assertTrue(s.shotEvents.isEmpty())
    }
}
