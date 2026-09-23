package com.mdlimonhossain.stumps.domain.commentary

import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType
import com.mdlimonhossain.stumps.domain.scoring.BallRecord

/**
 * One line of commentary text, ready to show on screen — like one sentence a TV commentator
 * would say after a ball. overBallLabel is the cricket "3.4" style over-and-ball number,
 * text is the actual sentence ("চার! দারুণ শট..."), and scoreAfter is what the score became
 * right after this ball.
 */
data class CommentaryLine(val overBallLabel: String, val text: String, val scoreAfter: String)

/**
 * This file turns the same ball-by-ball diary that ScoringEngine.kt uses into human-readable
 * commentary sentences — the kind you'd see scrolling on a live cricket score app. It doesn't
 * calculate the score itself (that's ScoringEngine's job); it just describes, in words, what
 * happened on each ball.
 *
 * Just like ScoringEngine, this is a "pure" function with no dependencies on the database or
 * the screen — you give it a list of balls, it gives you back a list of sentences. That makes
 * it very easy to test.
 */
object CommentaryEngine {

    /**
     * Turns a list of balls into a list of commentary lines, newest ball first (so it reads
     * like a live feed where the latest thing that happened is at the top).
     *
     * @param balls the ball-by-ball diary for this innings
     * @param playerNames optional lookup so we can show a nice display name instead of a raw id
     */
    fun generate(balls: List<BallRecord>, playerNames: Map<String, String> = emptyMap()): List<CommentaryLine> {
        // These track the running score as we walk through the balls in order, so we can say
        // "the score became 45/2" after each one.
        var runningRuns = 0
        var runningWickets = 0
        var legalBallsInOver = 0 // how many legal balls bowled in the CURRENT over so far (0 to 5)
        var overNumber = 0 // which over we're currently in (0 = first over)
        val lines = mutableListOf<CommentaryLine>()

        // Walk through every ball in the order it actually happened.
        for (ball in balls.sortedBy { it.sequence }) {
            // A "retirement" marker (see ScoringEngine's own comment on BallRecord.dismissalType)
            // isn't a real ball bowled at all — it must NOT get an over/ball number of its own
            // or count towards the 6-ball over, same as ScoringEngine treats it.
            val isRetirement = !ball.isWicket && ball.dismissalType == DismissalType.RETIRED_HURT
            runningRuns += ball.runsThisBall
            if (ball.isWicket) runningWickets += 1

            // Cricket balls are labelled like "3.4" (over 3, 4th ball). A "*" at the end marks
            // an extra ball (wide/no-ball) that doesn't count towards the 6-ball over. A
            // retirement shows the CURRENT (not-yet-incremented) position, since it happened
            // "between" balls rather than being one itself.
            val label = when {
                isRetirement -> "${overNumber}.${legalBallsInOver}"
                ball.isLegalDelivery -> "${overNumber}.${legalBallsInOver + 1}"
                else -> "${overNumber}.${legalBallsInOver + 1}*"
            }
            // Try to show a real player name if we have one, otherwise just fall back to their id.
            val batsman = playerNames[ball.strikerId] ?: ball.strikerId
            val bowler = playerNames[ball.bowlerId] ?: ball.bowlerId

            // Pick the right sentence template depending on what actually happened on this ball.
            // Kotlin's "when" here works like a big if/else-if chain — the FIRST matching line wins.
            val text = when {
                isRetirement -> {
                    val retiredName = playerNames[ball.dismissedPlayerId] ?: ball.dismissedPlayerId ?: batsman
                    val incomingName = playerNames[ball.newBatsmanId] ?: ball.newBatsmanId ?: "?"
                    "$retiredName অসুস্থ/আহত হয়ে বিশ্রামে গেলো — $incomingName ব্যাটিংয়ে আসলো"
                }
                ball.isWicket -> "উইকেট! $batsman আউট (${ball.dismissalType?.name ?: "?"}) — $bowler-এর শিকার"
                ball.extraType == ExtraType.WIDE -> "ওয়াইড — $bowler"
                ball.extraType == ExtraType.NO_BALL -> "নো বল! " + (if (ball.runsOffBat > 0) "$batsman ${ball.runsOffBat} রান নিলো" else "$bowler ওভারস্টেপ করেছে")
                ball.extraType == ExtraType.BYE -> "${ball.extraRuns} বাই রান"
                ball.extraType == ExtraType.LEG_BYE -> "${ball.extraRuns} লেগ-বাই রান"
                ball.runsOffBat == 6 -> "ছক্কা! $batsman উড়িয়ে মারলো"
                ball.runsOffBat == 4 -> "চার! দারুণ শট $batsman-এর"
                ball.runsOffBat == 0 -> "রান নেই — $bowler-এর টাইট বোলিং"
                else -> "$batsman ${ball.runsOffBat} রান নিলো"
            }

            lines.add(CommentaryLine(label, text, "$runningRuns/$runningWickets"))

            // Same "6 legal balls = one over finished" counting as ScoringEngine uses — a
            // retirement marker skips this entirely, exactly like it skips it there.
            if (ball.isLegalDelivery && !isRetirement) {
                legalBallsInOver += 1
                if (legalBallsInOver == 6) {
                    legalBallsInOver = 0
                    overNumber += 1
                }
            }
        }

        // Reverse the list so the newest ball shows up FIRST — that's how live commentary
        // feeds normally read (most recent event at the top).
        return lines.asReversed()
    }
}
