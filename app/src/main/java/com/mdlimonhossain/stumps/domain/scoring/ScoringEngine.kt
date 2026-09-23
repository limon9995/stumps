package com.mdlimonhossain.stumps.domain.scoring

import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType

/**
 * This file is the "brain" of the whole app — it's the code that actually understands the
 * rules of cricket scoring. Everything else (buttons, screens, database) just calls into this
 * file to figure out "what is the score right now?".
 *
 * The big idea used here is called "event sourcing". Instead of saving "the score is 45/2" and
 * trying to update that number every time something happens, we save EVERY SINGLE BALL that was
 * bowled, in order, like a diary. Then, whenever we need to know the current score, we "replay"
 * the whole diary from ball 1 to the last ball and add everything up fresh.
 *
 * Why do it this way instead of just keeping a running total? Because it makes two hard things
 * very easy:
 *  1. "Undo" — if the scorer taps the wrong button, we just delete the last diary entry and
 *     replay everything again. No need to carefully "subtract" what we added before.
 *  2. Testing — we can feed in a list of balls and check the answer is correct, without needing
 *     a database, a phone, or a screen. That's exactly what ScoringEngineTest.kt does.
 */

/**
 * BallRecord = one single delivery (one ball bowled), like one line in the diary described above.
 * This is the ONLY thing we ever save to represent what happened in a match — the rest of the
 * scorecard is calculated from a list of these.
 */
data class BallRecord(
    val sequence: Int, // the order this ball happened in (1st ball, 2nd ball, 3rd ball...)
    val bowlerId: String, // who was bowling this ball
    val strikerId: String, // who was facing the ball (the batsman on strike)
    val nonStrikerId: String, // the other batsman, standing at the bowler's end
    val runsOffBat: Int, // runs the batsman actually hit (0, 1, 2, 3, 4, or 6)
    val extraType: ExtraType?, // was this a wide / no-ball / bye / leg-bye? null means a normal ball
    val extraRuns: Int, // extra runs added because of a wide/no-ball/bye (separate from runsOffBat)
    val runsRun: Int, // how many runs the batsmen physically ran between the wickets (used to decide who's on strike next)
    val isWicket: Boolean, // did a batsman get out on this ball?
    val dismissalType: DismissalType?, // how they got out (bowled, caught, run out, etc.) — null if not a wicket
    val dismissedPlayerId: String?, // which player got out (usually the striker, but could be the non-striker on a run out)
    val newBatsmanId: String?, // the new batsman who comes in to replace the one who got out
    /** Where the shot went, 0-359 degrees clockwise from straight down the ground. Optional — only recorded for boundaries. */
    val shotAngleDegrees: Int? = null,
    /** Who gets credit for this dismissal — the catcher (CAUGHT), keeper (STUMPED), or thrower (RUN_OUT). Null for every other dismissal type and every non-wicket ball. */
    val fielderId: String? = null
) {
    // A "legal delivery" is a ball that counts towards the 6-balls-per-over limit.
    // Wides and no-balls do NOT count, so the bowler has to bowl another ball instead.
    val isLegalDelivery: Boolean get() = extraType != ExtraType.WIDE && extraType != ExtraType.NO_BALL

    // Total runs added to the team's score from this one ball (bat runs + any extras).
    val runsThisBall: Int get() = runsOffBat + extraRuns
}

/** One shot placed on the wagon wheel — only recorded for balls where the scorer tapped a direction. */
data class ShotEvent(val batsmanId: String, val angleDegrees: Int, val runs: Int, val isSix: Boolean)

/**
 * A running scoreboard-style summary for ONE batsman: how many runs they've scored so far,
 * how many balls they've faced, how many boundaries they've hit, and whether they're out yet.
 * This gets rebuilt fresh every time we replay the ball-by-ball diary.
 *
 * `ballsAtFifty`/`ballsAtHundred` remember exactly how many balls this batsman had faced AT THE
 * MOMENT their running total first reached 50/100 — that's what "fastest fifty" style stats
 * actually measure (fewer balls = faster). They stay null until (if ever) that milestone is
 * reached, and are never overwritten once set, since a milestone only happens once per innings.
 */
data class BatsmanFigures(
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    val dismissalType: DismissalType? = null,
    val ballsAtFifty: Int? = null,
    val ballsAtHundred: Int? = null,
    // True while this batsman has retired hurt and hasn't come back in yet — NOT the same as
    // isOut (retiring isn't a dismissal, so it never counts towards the team's fall of wickets,
    // and the real cricket rule lets them return later to resume their innings). This flips
    // back to false automatically the moment they're sent back in as someone's replacement —
    // see ScoringEngine's handling of BallRecord.newBatsmanId for exactly where that happens.
    val isRetiredHurt: Boolean = false
)

/**
 * Same idea as BatsmanFigures, but for a bowler: how many (legal) balls they've bowled, how
 * many runs they've conceded, how many wickets they've taken, and how many maiden overs (an
 * over where the WHOLE over conceded zero runs — byes/leg-byes included, same ICC scoring rule
 * used everywhere else in real cricket) they've bowled.
 */
data class BowlerFigures(
    val legalBalls: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val maidens: Int = 0
) {
    // Cricket overs are written like "4.2" meaning 4 full overs plus 2 balls — not a decimal number!
    // So we build that string ourselves instead of just dividing legalBalls by 6.
    val overs: String get() = "${legalBalls / 6}.${legalBalls % 6}"

    // Economy rate = average runs conceded per over. If no balls bowled yet, avoid dividing by zero.
    val economy: Double get() = if (legalBalls == 0) 0.0 else (runsConceded * 6.0) / legalBalls
}

/** A running tally of ONE fielder's dismissal credits — how many catches, stumpings, and run-outs they're responsible for so far. */
data class FieldingFigures(
    val catches: Int = 0,
    val stumpings: Int = 0,
    val runOuts: Int = 0
)

/**
 * One completed (or still-unbroken, if this is the LAST entry for an innings) batting
 * partnership — two batsmen who were at the crease together, and how many runs/legal balls the
 * TEAM added while both of them were in. `wicketNumber` is which fall-of-wicket this partnership
 * ended at (the 1st-wicket partnership, 2nd-wicket, and so on) — or, for the final still-unbroken
 * entry, which wicket it WOULD be if it ended right now. Matches the standard cricket-scorecard
 * idea of a "partnership", used for the reference app's "Best Partnership" statistic.
 */
data class PartnershipRecord(
    val batterAId: String,
    val batterBId: String,
    val runs: Int,
    val legalBalls: Int,
    val wicketNumber: Int
)

/**
 * InningsState is the FINAL ANSWER — everything you'd want to show on a live scoreboard,
 * all bundled together. This is what gets rebuilt every time [ScoringEngine.computeState] runs.
 */
data class InningsState(
    val totalRuns: Int = 0,
    val totalWickets: Int = 0,
    val legalBallsBowled: Int = 0, // used to work out how many overs have been bowled
    val oversLimit: Int = 0, // how many overs this match/innings is allowed (e.g. 20 for T20)
    val strikerId: String? = null, // who is on strike RIGHT NOW
    val nonStrikerId: String? = null, // who is at the other end RIGHT NOW
    val currentBowlerId: String? = null, // who is bowling RIGHT NOW
    val previousOverBowlerId: String? = null, // who bowled the over before this one (a bowler can't bowl two overs in a row)
    val isOverJustCompleted: Boolean = false, // true only right after the 6th ball of an over
    val isInningsComplete: Boolean = false, // true when the innings should stop (all out or overs finished)
    val batsmanFigures: Map<String, BatsmanFigures> = emptyMap(), // stats for every batsman who has batted, keyed by player id
    val bowlerFigures: Map<String, BowlerFigures> = emptyMap(), // stats for every bowler who has bowled, keyed by player id
    val fieldingFigures: Map<String, FieldingFigures> = emptyMap(), // catches/stumpings/run-outs credited so far, keyed by fielder id
    val shotEvents: List<ShotEvent> = emptyList(), // every shot direction recorded, used to draw the wagon wheel chart
    /** Runs scored per over, index 0 = over 1. The last entry may be a partial over still in progress. */
    val runsPerOver: List<Int> = emptyList(),
    /** Every batting partnership so far, in order — the last entry is the current, still-unbroken one (see PartnershipRecord). */
    val partnerships: List<PartnershipRecord> = emptyList()
) {
    // Same "4.2 overs" formatting trick as BowlerFigures.overs, but for the whole innings.
    val oversDisplay: String get() = "${legalBallsBowled / 6}.${legalBallsBowled % 6}"

    // Run rate = average runs scored per over so far.
    val runRate: Double get() = if (legalBallsBowled == 0) 0.0 else (totalRuns * 6.0) / legalBallsBowled
}

/**
 * ScoringEngine is the actual calculator. It has one important function: [computeState].
 * You give it the full list of balls bowled so far (in any order — it sorts them itself),
 * and it "plays through" the innings ball by ball, like a very fast replay, to work out
 * exactly what the scoreboard should look like right now.
 */
object ScoringEngine {

    /**
     * Replays a list of balls from the very start of an innings and works out the current
     * scoreboard. Think of this like reading a play-by-play commentary from ball 1 and keeping
     * a tally on a piece of paper as you go — that's literally what this function does.
     *
     * @param balls every ball bowled so far in this innings (order doesn't matter, we sort by sequence)
     * @param openingStrikerId who started the innings on strike
     * @param openingNonStrikerId who started the innings at the other end
     * @param openingBowlerId who bowled the very first ball
     * @param oversLimit how many overs this innings is allowed
     * @param squadSize how many players are on the batting team (normally 11) — used to know when the team is "all out"
     */
    fun computeState(
        balls: List<BallRecord>,
        openingStrikerId: String,
        openingNonStrikerId: String,
        openingBowlerId: String,
        oversLimit: Int,
        squadSize: Int = 11
    ): InningsState {
        // These are our "running tally" variables — they start at zero/opening values and get
        // updated as we walk through each ball, one at a time, from first to last.
        var totalRuns = 0
        var totalWickets = 0
        var legalBalls = 0
        var ballsInCurrentOver = 0
        var striker = openingStrikerId
        var nonStriker = openingNonStrikerId
        var bowler = openingBowlerId
        var previousOverBowler: String? = null
        var overJustCompleted = false
        // Remembers whether the VERY LAST ball processed closed off a partnership (either a
        // real wicket, OR a batsman retiring hurt — both change who's at the crease) — used
        // after the loop to avoid appending a bogus, empty "phantom" partnership when the
        // innings happened to end right on one of those balls (that partnership was already
        // closed off inside the loop).
        var lastBallSplitPartnership = false

        // Maps that collect stats per-player as we go. Key = player id, Value = their figures so far.
        val batsmen = mutableMapOf<String, BatsmanFigures>()
        val bowlers = mutableMapOf<String, BowlerFigures>()
        val fielders = mutableMapOf<String, FieldingFigures>()
        val shotEvents = mutableListOf<ShotEvent>()
        // runsPerOver tracks runs scored in each over, one "bucket" per over. We start with one
        // empty bucket (over 1) and add a new empty bucket every time an over finishes.
        val runsPerOver = mutableListOf(0)

        // ---- Partnership tracking ----
        // A partnership is "how many runs/balls the team added while these exact two batsmen
        // were both at the crease together". We track the CURRENT pair and their running total,
        // and every time one of them gets out, we close that partnership off and start a fresh
        // one for the survivor + whoever comes in next.
        val partnerships = mutableListOf<PartnershipRecord>()
        var partnerA = openingStrikerId
        var partnerB = openingNonStrikerId
        var partnershipRuns = 0
        var partnershipBalls = 0
        var wicketNumberForPartnership = 1

        // The main loop: go through every ball, oldest first, and update our tally.
        // sortedBy { it.sequence } makes sure we process them in the order they actually happened,
        // even if the list we were given wasn't already in order.
        for (ball in balls.sortedBy { it.sequence }) {
            overJustCompleted = false
            // A "retirement" ball is a special marker (see BallRecord.dismissalType's own
            // comment) — a batsman leaving hurt isn't a real delivery bowled, so it must NOT
            // count towards the over, NOT add a "ball faced" to the batsman, and NOT touch the
            // bowler's figures at all. It only changes who's standing at the crease.
            val isRetirement = !ball.isWicket && ball.dismissalType == DismissalType.RETIRED_HURT
            lastBallSplitPartnership = ball.isWicket || isRetirement
            totalRuns += ball.runsThisBall
            // Add this ball's runs into whichever over-bucket is currently open (the last one in the list).
            runsPerOver[runsPerOver.lastIndex] = runsPerOver.last() + ball.runsThisBall
            // The current partnership grows by every run the TEAM scores (including extras — same
            // as a real scorecard) and every legal ball bowled, for as long as this exact pair
            // stays unbroken. (runsThisBall is always 0 for a retirement marker, so the += here
            // is harmless even though we don't specifically guard it.)
            partnershipRuns += ball.runsThisBall
            if (ball.isLegalDelivery && !isRetirement) partnershipBalls += 1

            // If the scorer recorded WHERE the shot went (only done for some balls), remember it
            // for the wagon wheel chart. We skip wides here because you can't really "place" a wide.
            if (ball.shotAngleDegrees != null && (ball.extraType == null || ball.extraType == ExtraType.NO_BALL)) {
                shotEvents.add(ShotEvent(ball.strikerId, ball.shotAngleDegrees, ball.runsOffBat, ball.runsOffBat == 6))
            }

            val batsAtCreaseId = ball.strikerId

            // A retirement marker skips ALL of the normal "a ball was actually bowled" bookkeeping
            // below (bowler figures, batsman runs/balls-faced, wicket tally) — the only thing it
            // does is recorded further down: swap the retiring batsman out for their replacement.
            if (!isRetirement) {
                // ---- Work out the bowler's figures for this ball ----
                val bowlerStats = bowlers.getOrDefault(ball.bowlerId, BowlerFigures())
                // In real cricket scoring, a bowler is charged for the batsman's runs PLUS any wide/no-ball
                // penalty runs, but NOT for byes or leg-byes (those aren't the bowler's fault).
                val chargedToBowler = ball.runsOffBat + if (ball.extraType == ExtraType.WIDE || ball.extraType == ExtraType.NO_BALL) ball.extraRuns else 0

                // ---- Work out the batsman's figures for this ball ----
                val battingStats = batsmen.getOrDefault(batsAtCreaseId, BatsmanFigures())
                // A batsman doesn't "face" a wide (it's not really their ball to play), so we don't
                // count it towards their ballsFaced total. Everything else counts.
                val countsAsFaced = ball.extraType != ExtraType.WIDE

                // The batsman only gets personal credit for runs on a normal ball or a no-ball
                // (byes/leg-byes go to the team total but not to the batsman's own score).
                val batRunsThisBall = if (ball.extraType == null || ball.extraType == ExtraType.NO_BALL) ball.runsOffBat else 0
                val newBatRuns = battingStats.runs + batRunsThisBall
                val newBallsFaced = battingStats.ballsFaced + if (countsAsFaced) 1 else 0
                batsmen[batsAtCreaseId] = battingStats.copy(
                    runs = newBatRuns,
                    ballsFaced = newBallsFaced,
                    fours = battingStats.fours + if ((ball.extraType == null || ball.extraType == ExtraType.NO_BALL) && ball.runsOffBat == 4) 1 else 0,
                    sixes = battingStats.sixes + if ((ball.extraType == null || ball.extraType == ExtraType.NO_BALL) && ball.runsOffBat == 6) 1 else 0,
                    // A milestone is only ever recorded the FIRST time the total crosses the line —
                    // once ballsAtFifty/ballsAtHundred is set, it's never overwritten.
                    ballsAtFifty = battingStats.ballsAtFifty ?: (if (newBatRuns >= 50) newBallsFaced else null),
                    ballsAtHundred = battingStats.ballsAtHundred ?: (if (newBatRuns >= 100) newBallsFaced else null)
                )

                var updatedBowlerStats = bowlerStats.copy(
                    runsConceded = bowlerStats.runsConceded + chargedToBowler,
                    legalBalls = bowlerStats.legalBalls + if (ball.isLegalDelivery) 1 else 0
                )

                // ---- If someone got out on this ball ----
                if (ball.isWicket) {
                    totalWickets += 1
                    // Usually the striker is the one who got out, but on a run-out it could be the
                    // non-striker instead — dismissedPlayerId tells us exactly who, if it was set.
                    val dismissedId = ball.dismissedPlayerId ?: batsAtCreaseId
                    val dismissedStats = batsmen.getOrDefault(dismissedId, BatsmanFigures())
                    batsmen[dismissedId] = dismissedStats.copy(isOut = true, dismissalType = ball.dismissalType)

                    // This wicket breaks the current partnership — close it off, then start a fresh
                    // one for whoever's left + the new batsman coming in (if the innings isn't over).
                    partnerships.add(PartnershipRecord(partnerA, partnerB, partnershipRuns, partnershipBalls, wicketNumberForPartnership))
                    if (ball.newBatsmanId != null) {
                        if (dismissedId == partnerA) partnerA = ball.newBatsmanId else partnerB = ball.newBatsmanId
                    }
                    partnershipRuns = 0
                    partnershipBalls = 0
                    wicketNumberForPartnership += 1
                    // A bowler only gets "credit" for a wicket if it wasn't a run out — a run out is
                    // usually the fielders' doing, not really the bowler's achievement.
                    if (ball.dismissalType != DismissalType.RUN_OUT) {
                        updatedBowlerStats = updatedBowlerStats.copy(wickets = updatedBowlerStats.wickets + 1)
                    }
                    // Give the credited fielder a tally mark, if the scorer recorded one — only ever
                    // set for CAUGHT/STUMPED/RUN_OUT (see BallRecord.fielderId's own comment).
                    if (ball.fielderId != null) {
                        val fielderStats = fielders.getOrDefault(ball.fielderId, FieldingFigures())
                        fielders[ball.fielderId] = when (ball.dismissalType) {
                            DismissalType.CAUGHT -> fielderStats.copy(catches = fielderStats.catches + 1)
                            DismissalType.STUMPED -> fielderStats.copy(stumpings = fielderStats.stumpings + 1)
                            DismissalType.RUN_OUT -> fielderStats.copy(runOuts = fielderStats.runOuts + 1)
                            else -> fielderStats // shouldn't happen — fielderId is only ever set for the three types above
                        }
                    }
                }
                bowlers[ball.bowlerId] = updatedBowlerStats
            }

            // ---- A batsman retiring hurt (see the top of this loop iteration) ----
            // Not a dismissal (no wicket added), but it DOES change who's at the crease and
            // splits the partnership just like a wicket would — we just don't advance the
            // partnership's wicket-number label, since retiring isn't a fall of wicket.
            if (isRetirement) {
                val retiredId = ball.dismissedPlayerId ?: batsAtCreaseId
                val retiredStats = batsmen.getOrDefault(retiredId, BatsmanFigures())
                batsmen[retiredId] = retiredStats.copy(isRetiredHurt = true)
                partnerships.add(PartnershipRecord(partnerA, partnerB, partnershipRuns, partnershipBalls, wicketNumberForPartnership))
                if (ball.newBatsmanId != null) {
                    if (retiredId == partnerA) partnerA = ball.newBatsmanId else partnerB = ball.newBatsmanId
                }
                partnershipRuns = 0
                partnershipBalls = 0
            }

            // ---- Work out who's on strike for the NEXT ball ----
            // In cricket, if the batsmen run an ODD number of runs (1, 3, 5...), they end up
            // swapping ends, so the striker and non-striker switch places. An even number of
            // runs (0, 2, 4, 6) means they stay where they are. (Always 0 runs run on a
            // retirement marker, so this never fires for one.)
            if (ball.runsRun % 2 == 1) {
                val tmp = striker
                striker = nonStriker
                nonStriker = tmp
            }

            // If a wicket fell OR a batsman retired, and someone is coming in to replace them,
            // put that person in the correct spot (whichever end the outgoing batsman was at).
            // newBatsmanId is only ever set for these two cases — never for an ordinary ball —
            // so checking it alone (instead of also checking ball.isWicket) covers both.
            if (ball.newBatsmanId != null) {
                if ((ball.dismissedPlayerId ?: batsAtCreaseId) == striker) striker = ball.newBatsmanId
                else nonStriker = ball.newBatsmanId
                // Whoever's coming in — a brand new batsman, or someone who had earlier retired
                // hurt and is now well enough to resume — is clearly not "sitting out" any more.
                val incomingStats = batsmen.getOrDefault(ball.newBatsmanId, BatsmanFigures())
                batsmen[ball.newBatsmanId] = incomingStats.copy(isRetiredHurt = false)
            }

            bowler = ball.bowlerId

            // ---- Handle the end of an over ----
            // Only LEGAL deliveries count towards the 6-ball over (wides/no-balls don't, and
            // neither does a retirement marker — nobody actually bowled a ball for that).
            if (ball.isLegalDelivery && !isRetirement) {
                legalBalls += 1
                ballsInCurrentOver += 1
                if (ballsInCurrentOver == 6) {
                    // The over just finished! A "maiden" is an over where the team scored
                    // NOTHING at all off it — checked here, before the fresh bucket below opens,
                    // while runsPerOver's last bucket still holds this just-finished over's total.
                    if (runsPerOver.last() == 0) {
                        val overBowlerStats = bowlers.getOrDefault(bowler, BowlerFigures())
                        bowlers[bowler] = overBowlerStats.copy(maidens = overBowlerStats.maidens + 1)
                    }
                    // Reset the ball counter, remember who bowled it (so we don't let them bowl
                    // the very next over too), and open a fresh "bucket" in runsPerOver for the
                    // next over's runs.
                    ballsInCurrentOver = 0
                    previousOverBowler = bowler
                    overJustCompleted = true
                    runsPerOver.add(0)
                    // At the end of every over, the batsmen automatically swap ends
                    // (this happens regardless of how many runs were run on the last ball).
                    val tmp = striker
                    striker = nonStriker
                    nonStriker = tmp
                }
            }
        }

        // Every over-completion pre-opens the next over's bucket; if the innings ended exactly on
        // an over boundary, that trailing bucket never received a ball — drop it so the chart
        // doesn't show a phantom extra over.
        if (ballsInCurrentOver == 0 && runsPerOver.size > 1) {
            runsPerOver.removeAt(runsPerOver.lastIndex)
        }

        // The innings is over if either: the batting team has run out of players (all out),
        // or they've used up every ball they're allowed to face. Note: this doesn't account for
        // the rare real-cricket edge case where a retired-hurt batsman never returns AND there's
        // nobody left to send in as a fresh batsman either — that would need knowing the whole
        // squad list here (not just its size), so it's left as a known limitation for now.
        val isComplete = totalWickets >= (squadSize - 1) || legalBalls >= oversLimit * 6

        // The LAST partnership never got closed off (either it's still unbroken, or the innings
        // ended because overs ran out) — add it now so it's not lost. Skipped when no ball has
        // been bowled yet (nothing to show), or when the very last ball already closed it off
        // itself (a wicket or a retirement — see lastBallSplitPartnership above; adding it again
        // here would just create a bogus empty "0 runs" duplicate).
        if (balls.isNotEmpty() && !lastBallSplitPartnership) {
            partnerships.add(PartnershipRecord(partnerA, partnerB, partnershipRuns, partnershipBalls, wicketNumberForPartnership))
        }

        // Package everything we worked out into one neat object and hand it back.
        return InningsState(
            totalRuns = totalRuns,
            totalWickets = totalWickets,
            legalBallsBowled = legalBalls,
            oversLimit = oversLimit,
            strikerId = striker,
            nonStrikerId = nonStriker,
            currentBowlerId = bowler,
            previousOverBowlerId = previousOverBowler,
            isOverJustCompleted = overJustCompleted,
            isInningsComplete = isComplete,
            batsmanFigures = batsmen,
            bowlerFigures = bowlers,
            fieldingFigures = fielders,
            shotEvents = shotEvents,
            runsPerOver = runsPerOver,
            partnerships = partnerships
        )
    }
}
