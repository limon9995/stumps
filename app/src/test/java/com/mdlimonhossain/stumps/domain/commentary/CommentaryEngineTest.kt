package com.mdlimonhossain.stumps.domain.commentary

import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType
import com.mdlimonhossain.stumps.domain.scoring.BallRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val STRIKER = "Limon"
private const val NON_STRIKER = "Rahim"
private const val BOWLER = "Karim"

class CommentaryEngineTest {

    private fun ball(seq: Int, runs: Int, extraType: ExtraType? = null, extraRuns: Int = 0, isWicket: Boolean = false, dismissal: DismissalType? = null) =
        BallRecord(
            sequence = seq, bowlerId = BOWLER, strikerId = STRIKER, nonStrikerId = NON_STRIKER,
            runsOffBat = runs, extraType = extraType, extraRuns = extraRuns, runsRun = runs,
            isWicket = isWicket, dismissalType = dismissal, dismissedPlayerId = if (isWicket) STRIKER else null, newBatsmanId = null
        )

    @Test
    fun mostRecentBallComesFirst() {
        val lines = CommentaryEngine.generate(listOf(ball(1, 0), ball(2, 4)))
        assertTrue(lines.first().text.contains("চার"))
        assertTrue(lines.last().text.contains("রান নেই"))
    }

    @Test
    fun sixAndFourGetDistinctText() {
        val lines = CommentaryEngine.generate(listOf(ball(1, 6), ball(2, 4)))
        assertTrue(lines[1].text.contains("ছক্কা"))
        assertTrue(lines[0].text.contains("চার"))
    }

    @Test
    fun wicketMentionsDismissalType() {
        val lines = CommentaryEngine.generate(listOf(ball(1, 0, isWicket = true, dismissal = DismissalType.BOWLED)))
        assertTrue(lines.first().text.contains("BOWLED"))
        assertTrue(lines.first().text.contains("উইকেট"))
    }

    @Test
    fun scoreAfterAccumulatesAcrossBalls() {
        val lines = CommentaryEngine.generate(listOf(ball(1, 4), ball(2, 6)))
        assertEquals("4/0", lines.last().scoreAfter)
        assertEquals("10/0", lines.first().scoreAfter)
    }

    @Test
    fun playerNamesAreSubstitutedWhenProvided() {
        val lines = CommentaryEngine.generate(listOf(ball(1, 4)), mapOf(STRIKER to "Md Limon Hossain"))
        assertTrue(lines.first().text.contains("Md Limon Hossain"))
    }

    @Test
    fun overBallLabelAdvancesEverySixLegalBalls() {
        val balls = (1..7).map { ball(it, 0) }
        val lines = CommentaryEngine.generate(balls)
        // reversed order — last generated (7th ball) is first in the list, should be over "1.1"
        assertEquals("1.1", lines.first().overBallLabel)
        assertEquals("0.1", lines.last().overBallLabel)
    }
}
