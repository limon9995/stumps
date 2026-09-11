package com.mdlimonhossain.stumps.data.local.db.match

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The Dao (Data Access Object) interfaces for teams, players, matches, innings, and balls.
 * See UserDao.kt for a general explanation of what a Dao is — this file has one Dao per table,
 * each just describing the handful of database queries the rest of the app actually needs.
 */

@Dao
interface TeamDao {
    @Upsert
    suspend fun upsert(team: TeamEntity)

    // Used by Team Settings' delete button — permanently removes this team (its saved players
    // stay in the database as orphaned rows, same trade-off MatchRepository already makes
    // elsewhere in this app for simplicity, since a deleted team's players aren't shown anywhere
    // once the team itself is gone).
    @Delete
    suspend fun delete(team: TeamEntity)

    // Every team belonging to one user, alphabetically — shown on the "আমার টিম" screen.
    @Query("SELECT * FROM teams WHERE createdByUid = :uid ORDER BY name")
    fun observeTeamsForUser(uid: String): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getById(id: String): TeamEntity?

    // A simple case-insensitive "contains" search across one user's own saved teams — used by
    // the Search screen (see TournamentDao.searchByName for why this is per-user, not global).
    @Query("SELECT * FROM teams WHERE createdByUid = :uid AND name LIKE '%' || :query || '%' ORDER BY name")
    suspend fun searchByName(uid: String, query: String): List<TeamEntity>
}

@Dao
interface PlayerDao {
    // Saves a whole list of players in one go (used when creating a team with its full roster).
    @Upsert
    suspend fun upsertAll(players: List<PlayerEntity>)

    @Query("SELECT * FROM players WHERE teamId = :teamId")
    fun observePlayersForTeam(teamId: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE teamId = :teamId")
    suspend fun getPlayersForTeamOnce(teamId: String): List<PlayerEntity>

    // A case-insensitive "contains" search across every player on any of ONE user's own saved
    // teams — joins through the teams table since PlayerEntity itself has no createdByUid of its
    // own (only its parent team does). Used by the Search screen's Player category.
    @Query(
        "SELECT players.* FROM players " +
            "INNER JOIN teams ON players.teamId = teams.id " +
            "WHERE teams.createdByUid = :uid AND players.name LIKE '%' || :query || '%' " +
            "ORDER BY players.name"
    )
    suspend fun searchByNameForUser(uid: String, query: String): List<PlayerEntity>
}

@Dao
interface MatchDao {
    @Upsert
    suspend fun upsert(match: MatchEntity)

    // Newest match first — for the "আমার ম্যাচসমূহ" (my matches) history list.
    @Query("SELECT * FROM matches WHERE createdByUid = :uid ORDER BY createdAt DESC")
    fun observeMatchesForUser(uid: String): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE createdByUid = :uid ORDER BY createdAt DESC")
    suspend fun getMatchesForUserOnce(uid: String): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE id = :id")
    fun observeMatch(id: String): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getById(id: String): MatchEntity?
}

@Dao
interface InningsDao {
    @Upsert
    suspend fun upsert(innings: InningsEntity)

    // A match's innings in order (innings 1 first, then innings 2 if it exists).
    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsNumber")
    fun observeInningsForMatch(matchId: String): Flow<List<InningsEntity>>

    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsNumber")
    suspend fun getInningsForMatchOnce(matchId: String): List<InningsEntity>

    @Query("SELECT * FROM innings WHERE id = :id")
    suspend fun getById(id: String): InningsEntity?

    @Query("SELECT * FROM innings WHERE id = :id")
    fun observeById(id: String): Flow<InningsEntity?>
}

@Dao
interface BallDao {
    // Balls are only ever ADDED (or deleted for undo), never edited in place — that's why
    // this uses @Insert rather than @Upsert.
    @Insert
    suspend fun insert(ball: BallEntity)

    // Every ball for an innings, in the order they were bowled — this is exactly the list
    // ScoringEngine.computeState replays to work out the scoreboard.
    @Query("SELECT * FROM balls WHERE inningsId = :inningsId ORDER BY sequence")
    fun observeBallsForInnings(inningsId: String): Flow<List<BallEntity>>

    @Query("SELECT * FROM balls WHERE inningsId = :inningsId ORDER BY sequence")
    suspend fun getBallsForInningsOnce(inningsId: String): List<BallEntity>

    // The highest "sequence" number saved so far — used to work out the next ball's number.
    @Query("SELECT MAX(sequence) FROM balls WHERE inningsId = :inningsId")
    suspend fun maxSequence(inningsId: String): Int?

    // Deletes only the LAST ball (the one with the highest sequence number) — this is the
    // entire "undo" feature: just remove the most recent diary entry.
    @Query("DELETE FROM balls WHERE inningsId = :inningsId AND sequence = (SELECT MAX(sequence) FROM balls WHERE inningsId = :inningsId)")
    suspend fun deleteLastBall(inningsId: String)
}
