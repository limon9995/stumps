package com.mdlimonhossain.stumps.data.remote.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mdlimonhossain.stumps.data.local.db.match.BallEntity
import com.mdlimonhossain.stumps.data.local.db.match.InningsEntity
import com.mdlimonhossain.stumps.data.local.db.match.MatchEntity
import com.mdlimonhossain.stumps.domain.scoring.BallRecord
import com.mdlimonhossain.stumps.domain.model.DismissalType
import com.mdlimonhossain.stumps.domain.model.ExtraType
import com.mdlimonhossain.stumps.domain.scoring.InningsState
import com.mdlimonhossain.stumps.domain.scoring.ScoringEngine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * This file makes the "লাইভ ব্রডকাস্ট" (live broadcast) feature work. The idea: while a
 * scorer is scoring a match on their phone (saved in the local Room database, as normal),
 * this class ALSO copies every ball up to Firestore (Google's cloud database) in real time —
 * so that OTHER people, on other phones, who don't even need to be logged in, can watch the
 * same live scoreboard by typing in a short share code.
 *
 * The local phone's database is always the "real" source of truth — Firestore is just a
 * mirror/copy for broadcasting. This is a "one writer, many readers" setup: only the scorer's
 * own phone ever writes to Firestore for this match; everyone else only reads.
 */

/** What a viewer sees after typing in a share code, before they've picked an innings to watch. */
data class PublicLiveMatch(
    val matchId: String,
    val teamAName: String,
    val teamBName: String,
    val oversLimit: Int
)

/** The live scoreboard a viewer sees, worked out the exact same way the scorer's own screen does (via ScoringEngine). */
data class PublicLiveInnings(
    val inningsNumber: Int,
    val battingTeamName: String,
    val bowlingTeamName: String,
    val targetRuns: Int?,
    val state: InningsState
)

/** Same idea as PublicLiveInnings, but ALSO keeps the raw ball-by-ball log — needed for a one-shot (non-live) scorecard/commentary view, e.g. of a finished tournament match someone else organized. */
data class PublicInningsWithBalls(
    val inningsNumber: Int,
    val battingTeamName: String,
    val bowlingTeamName: String,
    val targetRuns: Int?,
    val state: InningsState,
    val ballLog: List<BallRecord>
)

/**
 * Mirrors a match a scorer has chosen to broadcast into Firestore (one writer — the scorer's own
 * device, source of truth is still local Room — many readers). Firestore's built-in offline cache
 * means these writes queue automatically if the scorer loses network and flush once it's back,
 * so we don't need to hand-roll a retry queue.
 */
class LiveBroadcastRepository(
    firestoreOverride: FirebaseFirestore? = null
) {
    // `by lazy` defers FirebaseFirestore.getInstance() to the first time it's actually needed,
    // instead of the moment this repository is constructed. See the matching comment in
    // TournamentRepository/ClubRepository for why: constructing a LiveBroadcastRepository as
    // part of building a TournamentRepository (for cloud match mirroring) must stay safe even in
    // a Robolectric unit test, which has no real Firebase app running at all.
    private val firestore: FirebaseFirestore by lazy { firestoreOverride ?: FirebaseFirestore.getInstance() }
    // Small helpers that point at the right "folder" in Firestore. Firestore data is organised
    // like nested folders: live_matches/{one match}/innings/{one innings}/balls/{one ball}.
    private fun matchesRef() = firestore.collection("live_matches")
    private fun inningsRef(matchId: String) = matchesRef().document(matchId).collection("innings")
    private fun ballsRef(matchId: String, inningsId: String) = inningsRef(matchId).document(inningsId).collection("balls")

    /** Makes a random 6-character code like "K3P9XZ" for viewers to type in — easy to read out loud, no confusing 0/O or 1/I. */
    fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous 0/O/1/I
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    /** Turns broadcasting ON for a match — creates/updates its Firestore document so viewers can find it by share code. */
    suspend fun goLive(match: MatchEntity, teamAName: String, teamBName: String, shareCode: String) {
        matchesRef().document(match.id).set(
            mapOf(
                "shareCode" to shareCode,
                "isLive" to true,
                "teamAName" to teamAName,
                "teamBName" to teamBName,
                "oversLimit" to match.oversLimit,
                "tournamentId" to match.tournamentId
            )
        ).await() // .await() pauses this suspend function until Firestore confirms the write is done
    }

    /** Turns broadcasting OFF — viewers won't be able to find this match by share code any more. */
    suspend fun stopLive(matchId: String) {
        matchesRef().document(matchId).update("isLive", false).await()
    }

    /** Copies an innings' "setup" info (who's opening, the target) up to Firestore. */
    suspend fun pushInnings(matchId: String, innings: InningsEntity, battingTeamName: String, bowlingTeamName: String) {
        inningsRef(matchId).document(innings.id).set(
            mapOf(
                "inningsNumber" to innings.inningsNumber,
                "battingTeamName" to battingTeamName,
                "bowlingTeamName" to bowlingTeamName,
                "openingStrikerId" to innings.openingStrikerId,
                "openingNonStrikerId" to innings.openingNonStrikerId,
                "openingBowlerId" to innings.openingBowlerId,
                "targetRuns" to innings.targetRuns
            )
        ).await()
    }

    /** Copies ONE ball up to Firestore — called every time the scorer records a ball, if the match is live. */
    suspend fun pushBall(matchId: String, ball: BallEntity) {
        ballsRef(matchId, ball.inningsId).document(ball.id).set(
            mapOf(
                "sequence" to ball.sequence,
                "bowlerId" to ball.bowlerId,
                "strikerId" to ball.strikerId,
                "nonStrikerId" to ball.nonStrikerId,
                "runsOffBat" to ball.runsOffBat,
                "extraType" to ball.extraType,
                "extraRuns" to ball.extraRuns,
                "runsRun" to ball.runsRun,
                "isWicket" to ball.isWicket,
                "dismissalType" to ball.dismissalType,
                "dismissedPlayerId" to ball.dismissedPlayerId,
                "newBatsmanId" to ball.newBatsmanId,
                "shotAngleDegrees" to ball.shotAngleDegrees,
                "fielderId" to ball.fielderId
            )
        ).await()
    }

    /** Mirrors an "undo" — deletes a ball from Firestore that was just deleted locally. */
    suspend fun removeBall(matchId: String, inningsId: String, ballId: String) {
        ballsRef(matchId, inningsId).document(ballId).delete().await()
    }

    /** Resolves a viewer's share code to the live match, without requiring the viewer to sign in. */
    suspend fun findByShareCode(shareCode: String): PublicLiveMatch? {
        // Search Firestore for a match whose shareCode matches AND is currently live (isLive = true).
        val snapshot = matchesRef()
            .whereEqualTo("shareCode", shareCode.uppercase())
            .whereEqualTo("isLive", true)
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        return PublicLiveMatch(
            matchId = doc.id,
            teamAName = doc.getString("teamAName") ?: "",
            teamBName = doc.getString("teamBName") ?: "",
            oversLimit = (doc.getLong("oversLimit") ?: 0L).toInt()
        )
    }

    /**
     * One-shot (not live-listening) fetch of a match's basic info from Firestore — team names
     * and overs limit. Used when browsing a tournament match someone ELSE organized, where this
     * device has no local copy of the match at all.
     */
    suspend fun getMatchInfoOnce(matchId: String): PublicLiveMatch? {
        val doc = matchesRef().document(matchId).get().await()
        if (!doc.exists()) return null
        return PublicLiveMatch(
            matchId = matchId,
            teamAName = doc.getString("teamAName") ?: "",
            teamBName = doc.getString("teamBName") ?: "",
            oversLimit = (doc.getLong("oversLimit") ?: 0L).toInt()
        )
    }

    /**
     * One-shot fetch of EVERY innings for a match (not just the latest, and not kept
     * live-updating like watchLatestInnings) — each with its full ball log replayed through
     * ScoringEngine, exactly the same way MatchRepository.getFinalInningsStates works for a
     * LOCAL match. This is the cloud equivalent of that function, used for computing a
     * tournament's points table/leaderboards from matches this device didn't organize, and for
     * showing a read-only scorecard of one such match.
     */
    suspend fun getAllInningsOnce(matchId: String): List<PublicInningsWithBalls> {
        val oversLimit = (matchesRef().document(matchId).get().await().getLong("oversLimit") ?: 20L).toInt()
        val inningsSnapshot = inningsRef(matchId).orderBy("inningsNumber").get().await()
        return inningsSnapshot.documents.map { inningsDoc ->
            val inningsId = inningsDoc.id
            val openingStrikerId = inningsDoc.getString("openingStrikerId") ?: ""
            val openingNonStrikerId = inningsDoc.getString("openingNonStrikerId") ?: ""
            val openingBowlerId = inningsDoc.getString("openingBowlerId") ?: ""

            val ballsSnapshot = ballsRef(matchId, inningsId).orderBy("sequence").get().await()
            val balls = ballsSnapshot.documents.mapNotNull { d ->
                BallRecord(
                    sequence = (d.getLong("sequence") ?: 0L).toInt(),
                    bowlerId = d.getString("bowlerId") ?: return@mapNotNull null,
                    strikerId = d.getString("strikerId") ?: return@mapNotNull null,
                    nonStrikerId = d.getString("nonStrikerId") ?: return@mapNotNull null,
                    runsOffBat = (d.getLong("runsOffBat") ?: 0L).toInt(),
                    extraType = d.getString("extraType")?.let { ExtraType.valueOf(it) },
                    extraRuns = (d.getLong("extraRuns") ?: 0L).toInt(),
                    runsRun = (d.getLong("runsRun") ?: 0L).toInt(),
                    isWicket = d.getBoolean("isWicket") ?: false,
                    dismissalType = d.getString("dismissalType")?.let { DismissalType.valueOf(it) },
                    dismissedPlayerId = d.getString("dismissedPlayerId"),
                    newBatsmanId = d.getString("newBatsmanId"),
                    shotAngleDegrees = d.getLong("shotAngleDegrees")?.toInt(),
                    fielderId = d.getString("fielderId")
                )
            }
            val state = ScoringEngine.computeState(
                balls = balls,
                openingStrikerId = openingStrikerId,
                openingNonStrikerId = openingNonStrikerId,
                openingBowlerId = openingBowlerId,
                oversLimit = oversLimit
            )
            PublicInningsWithBalls(
                inningsNumber = (inningsDoc.getLong("inningsNumber") ?: 1L).toInt(),
                battingTeamName = inningsDoc.getString("battingTeamName") ?: "",
                bowlingTeamName = inningsDoc.getString("bowlingTeamName") ?: "",
                targetRuns = inningsDoc.getLong("targetRuns")?.toInt(),
                state = state,
                ballLog = balls
            )
        }
    }

    /**
     * Real-time innings + ball-log listener for viewers, replaying the same ScoringEngine locally.
     *
     * This is the "watching" side of live broadcast. It sets up TWO nested Firestore listeners:
     * one watching for which innings is the latest one, and (inside that) one watching for every
     * ball in that innings. Every time a new ball arrives, we run the exact same ScoringEngine
     * that the scorer's own phone uses, so viewers see an identical, correctly-calculated
     * scoreboard — not just raw numbers copied from Firestore.
     */
    fun watchLatestInnings(matchId: String): Flow<PublicLiveInnings?> = callbackFlow {
        // Overs limit is fixed for the whole match, so one read is enough (no need for a third live listener).
        val oversLimit = (matchesRef().document(matchId).get().await().getLong("oversLimit") ?: 20L).toInt()
        var ballsListener: ListenerRegistration? = null

        // Listener #1: watch for which innings is currently the "latest" one (there can be 2).
        val inningsListener = inningsRef(matchId)
            .orderBy("inningsNumber")
            .addSnapshotListener { snapshot, _ ->
                val lastInningsDoc = snapshot?.documents?.lastOrNull()
                // If the innings changed (e.g. we moved from innings 1 to innings 2), stop
                // listening to the OLD innings' balls before we start listening to the new one.
                ballsListener?.remove()
                if (lastInningsDoc == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val inningsId = lastInningsDoc.id
                val inningsNumber = (lastInningsDoc.getLong("inningsNumber") ?: 1L).toInt()
                val battingTeamName = lastInningsDoc.getString("battingTeamName") ?: ""
                val bowlingTeamName = lastInningsDoc.getString("bowlingTeamName") ?: ""
                val openingStrikerId = lastInningsDoc.getString("openingStrikerId") ?: ""
                val openingNonStrikerId = lastInningsDoc.getString("openingNonStrikerId") ?: ""
                val openingBowlerId = lastInningsDoc.getString("openingBowlerId") ?: ""
                val targetRuns = lastInningsDoc.getLong("targetRuns")?.toInt()

                // Listener #2 (nested inside #1): watch every ball in THIS innings, and re-run
                // ScoringEngine every single time a ball is added, changed, or removed.
                ballsListener = ballsRef(matchId, inningsId)
                    .orderBy("sequence")
                    .addSnapshotListener { ballsSnapshot, _ ->
                        // Convert each Firestore "document" (a bag of text/number fields) back
                        // into a proper BallRecord that ScoringEngine understands.
                        val balls = ballsSnapshot?.documents?.mapNotNull { d ->
                            BallRecord(
                                sequence = (d.getLong("sequence") ?: 0L).toInt(),
                                bowlerId = d.getString("bowlerId") ?: return@mapNotNull null,
                                strikerId = d.getString("strikerId") ?: return@mapNotNull null,
                                nonStrikerId = d.getString("nonStrikerId") ?: return@mapNotNull null,
                                runsOffBat = (d.getLong("runsOffBat") ?: 0L).toInt(),
                                extraType = d.getString("extraType")?.let { ExtraType.valueOf(it) },
                                extraRuns = (d.getLong("extraRuns") ?: 0L).toInt(),
                                runsRun = (d.getLong("runsRun") ?: 0L).toInt(),
                                isWicket = d.getBoolean("isWicket") ?: false,
                                dismissalType = d.getString("dismissalType")?.let { DismissalType.valueOf(it) },
                                dismissedPlayerId = d.getString("dismissedPlayerId"),
                                newBatsmanId = d.getString("newBatsmanId"),
                                shotAngleDegrees = d.getLong("shotAngleDegrees")?.toInt(),
                                fielderId = d.getString("fielderId")
                            )
                        } ?: emptyList()

                        // Same ScoringEngine, same math, same result as the scorer's own device —
                        // that's the whole point of storing raw balls instead of a pre-calculated score.
                        val state = ScoringEngine.computeState(
                            balls = balls,
                            openingStrikerId = openingStrikerId,
                            openingNonStrikerId = openingNonStrikerId,
                            openingBowlerId = openingBowlerId,
                            oversLimit = oversLimit
                        )
                        trySend(
                            PublicLiveInnings(
                                inningsNumber = inningsNumber,
                                battingTeamName = battingTeamName,
                                bowlingTeamName = bowlingTeamName,
                                targetRuns = targetRuns,
                                state = state
                            )
                        )
                    }
            }

        // Clean up both listeners once nobody is watching this Flow any more, so we don't keep
        // burning battery/data listening to a match the viewer has already closed.
        awaitClose {
            inningsListener.remove()
            ballsListener?.remove()
        }
    }
}
