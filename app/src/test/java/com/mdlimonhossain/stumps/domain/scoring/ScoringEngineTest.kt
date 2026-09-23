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

    @Test
    fun maidenOver_creditedWhenOverConcedesNoRuns() {
        val over = (1..6).map { normalBall(it, 0) } // six dot balls
        val s = state(over)
        assertEquals(1, s.bowlerFigures[BOWLER]?.maidens)
    }

    @Test
    fun maidenOver_notCreditedWhenRunsAreScored() {
        val over = listOf(normalBall(1, 1)) + (2..6).map { normalBall(it, 0) }
        val s = state(over)
        assertEquals(0, s.bowlerFigures[BOWLER]?.maidens)
    }

    @Test
    fun maidenOver_stillCountsWithByes() {
        // A maiden is "the whole over conceded zero runs" — byes/leg-byes still break it, same as real cricket scoring.
        val bye = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = ExtraType.BYE, extraRuns = 1, runsRun = 1,
            isWicket = false, dismissalType = null, dismissedPlayerId = null, newBatsmanId = null
        )
        val over = listOf(bye) + (2..6).map { normalBall(it, 0) }
        val s = state(over)
        assertEquals(0, s.bowlerFigures[BOWLER]?.maidens)
    }

    @Test
    fun fiftyMilestone_recordsBallsFacedWhenFirstReached() {
        // 13 fours in a row = 52 runs, crossing 50 on the 13th ball — fours don't rotate strike, so the same batsman faces every ball.
        val balls = (1..13).map { normalBall(it, 4) }
        val s = state(balls)
        assertEquals(52, s.batsmanFigures[STRIKER]?.runs)
        assertEquals(13, s.batsmanFigures[STRIKER]?.ballsAtFifty)
        assertEquals(null, s.batsmanFigures[STRIKER]?.ballsAtHundred)
    }

    @Test
    fun fiftyMilestone_notSetBelowFifty() {
        val balls = (1..5).map { normalBall(it, 4) } // 20 runs, nowhere near 50
        val s = state(balls)
        assertEquals(null, s.batsmanFigures[STRIKER]?.ballsAtFifty)
    }

    @Test
    fun partnership_closesOnWicketAndStartsFreshPairForTheNewBatsman() {
        val wicketBall = BallRecord(
            sequence = 3, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
            isWicket = true, dismissalType = DismissalType.BOWLED, dismissedPlayerId = STRIKER, newBatsmanId = "batsman_C"
        )
        val s = state(listOf(normalBall(1, 4), normalBall(2, 2), wicketBall))
        assertEquals(1, s.partnerships.size)
        val partnership = s.partnerships.first()
        assertEquals(6, partnership.runs) // 4 + 2, the wicket ball itself added nothing
        assertEquals(3, partnership.legalBalls)
        assertEquals(1, partnership.wicketNumber)
        assertTrue(setOf(partnership.batterAId, partnership.batterBId) == setOf(STRIKER, NON_STRIKER))
    }

    @Test
    fun partnership_stillUnbrokenPartnershipIsRecordedAtEndOfInnings() {
        val s = state(listOf(normalBall(1, 4), normalBall(2, 2)))
        assertEquals(1, s.partnerships.size)
        assertEquals(6, s.partnerships.first().runs)
        assertEquals(2, s.partnerships.first().legalBalls)
    }

    @Test
    fun retiredHurt_isNotAWicket_andBringsInReplacement() {
        val retireBall = BallRecord(
            sequence = 1, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
            isWicket = false, dismissalType = DismissalType.RETIRED_HURT, dismissedPlayerId = STRIKER,
            newBatsmanId = "batsman_C"
        )
        val s = state(listOf(retireBall))
        // Retiring must NEVER count as a fall of wicket — that's the whole point of the rule.
        assertEquals(0, s.totalWickets)
        assertFalse(s.batsmanFigures[STRIKER]?.isOut == true)
        assertTrue(s.batsmanFigures[STRIKER]?.isRetiredHurt == true)
        // Nor does it use up a ball of the over — nobody actually bowled anything.
        assertEquals(0, s.legalBallsBowled)
        assertEquals("batsman_C", s.strikerId)
        assertEquals(NON_STRIKER, s.nonStrikerId)
    }

    @Test
    fun retiredHurt_doesNotConsumeAnOverBall() {
        val retireBall = BallRecord(
            sequence = 4, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
            isWicket = false, dismissalType = DismissalType.RETIRED_HURT, dismissedPlayerId = STRIKER,
            newBatsmanId = "batsman_C"
        )
        // 3 normal balls, then a retirement (not a real delivery), then 3 more normal balls —
        // the over should still complete after exactly 6 REAL balls, unaffected by the retirement
        // sitting in between them.
        val balls = listOf(normalBall(1, 0), normalBall(2, 0), normalBall(3, 0), retireBall) +
            (5..7).map { normalBall(it, 0, striker = "batsman_C") }
        val s = state(balls)
        assertEquals(6, s.legalBallsBowled)
        assertTrue(s.isOverJustCompleted)
    }

    @Test
    fun retiredHurt_canReturnLaterAndResumesAccumulatingRuns() {
        // STRIKER scores 6 runs, then retires hurt — batsman_C comes in as a temporary replacement.
        val retireBall = BallRecord(
            sequence = 3, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
            isWicket = false, dismissalType = DismissalType.RETIRED_HURT, dismissedPlayerId = STRIKER,
            newBatsmanId = "batsman_C"
        )
        // batsman_C takes strike, runs a single (odd — swaps ends, so NON_STRIKER is now on strike).
        val singleByReplacement = normalBall(4, 1, striker = "batsman_C", nonStriker = NON_STRIKER)
        // NON_STRIKER then gets bowled — and the fit-again STRIKER is brought BACK in, resuming
        // their innings, instead of a brand new batsman.
        val wicketBringsBackStriker = BallRecord(
            sequence = 5, bowlerId = BOWLER, strikerId = NON_STRIKER, nonStrikerId = "batsman_C",
            runsOffBat = 0, extraType = null, extraRuns = 0, runsRun = 0,
            isWicket = true, dismissalType = DismissalType.BOWLED, dismissedPlayerId = NON_STRIKER,
            newBatsmanId = STRIKER
        )
        val s = state(listOf(normalBall(1, 4), normalBall(2, 2), retireBall, singleByReplacement, wicketBringsBackStriker))

        // Only the genuine bowled dismissal counts as a wicket — the earlier retirement never did.
        assertEquals(1, s.totalWickets)
        // STRIKER's 6 runs from before retiring are still there, untouched by the time away.
        assertEquals(6, s.batsmanFigures[STRIKER]?.runs)
        // And they're marked as active again, not out, now that they've been sent back in.
        assertFalse(s.batsmanFigures[STRIKER]?.isRetiredHurt == true)
        assertFalse(s.batsmanFigures[STRIKER]?.isOut == true)
        assertEquals(STRIKER, s.strikerId)
    }
}
