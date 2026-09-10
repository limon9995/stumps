package com.mdlimonhossain.stumps.data.local.db.tournament

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Dao interfaces for tournaments, their participating teams, and their fixture schedule. Same pattern as MatchDao.kt. */

@Dao
interface TournamentDao {
    @Upsert
    suspend fun upsert(tournament: TournamentEntity)

    @Query("SELECT * FROM tournaments WHERE organizerUid = :uid ORDER BY createdAt DESC")
    fun observeForUser(uid: String): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    fun observeById(id: String): Flow<TournamentEntity?>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getById(id: String): TournamentEntity?

    // A simple case-insensitive "contains" search, scoped to one user's own tournaments only —
    // used by the Search screen. There's no cross-user/public tournament directory yet (every
    // table in this app is local-only, per-device — see AppDatabase.kt), so this can only ever
    // find tournaments the CURRENT user themselves created.
    @Query("SELECT * FROM tournaments WHERE organizerUid = :uid AND name LIKE '%' || :query || '%' ORDER BY name")
    suspend fun searchByName(uid: String, query: String): List<TournamentEntity>
}

@Dao
interface TournamentTeamDao {
    @Upsert
    suspend fun upsertAll(teams: List<TournamentTeamEntity>)

    @Query("SELECT * FROM tournament_teams WHERE tournamentId = :tournamentId")
    fun observeForTournament(tournamentId: String): Flow<List<TournamentTeamEntity>>

    @Query("SELECT * FROM tournament_teams WHERE tournamentId = :tournamentId")
    suspend fun getForTournamentOnce(tournamentId: String): List<TournamentTeamEntity>
}

@Dao
interface TournamentFixtureDao {
    // Saves a whole batch of fixtures at once — used right after the round-robin schedule is generated.
    @Upsert
    suspend fun upsertAll(fixtures: List<TournamentFixtureEntity>)

    // Saves just ONE fixture — used when a fixture's matchId gets filled in as its match starts.
    @Upsert
    suspend fun upsert(fixture: TournamentFixtureEntity)

    @Query("SELECT * FROM tournament_fixtures WHERE tournamentId = :tournamentId ORDER BY round")
    fun observeForTournament(tournamentId: String): Flow<List<TournamentFixtureEntity>>

    @Query("SELECT * FROM tournament_fixtures WHERE tournamentId = :tournamentId ORDER BY round")
    suspend fun getForTournamentOnce(tournamentId: String): List<TournamentFixtureEntity>
}
