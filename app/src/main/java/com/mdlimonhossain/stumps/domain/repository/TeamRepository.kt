package com.mdlimonhossain.stumps.domain.repository

import com.mdlimonhossain.stumps.data.local.db.match.PlayerDao
import com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity
import com.mdlimonhossain.stumps.data.local.db.match.TeamDao
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The middleman (repository) for SAVED teams — the ones you build once under "আমার টিম" and can
 * reuse again and again, especially for tournaments. This is different from the ad-hoc,
 * type-the-names-fresh teams that a quick standalone match creates on the fly (see
 * MatchRepository.createQuickMatch) — those aren't meant to be reused later.
 */
class TeamRepository(
    private val teamDao: TeamDao,
    private val playerDao: PlayerDao
) {
    fun observeTeamsForUser(uid: String): Flow<List<TeamEntity>> = teamDao.observeTeamsForUser(uid)
    fun observePlayersForTeam(teamId: String): Flow<List<PlayerEntity>> = playerDao.observePlayersForTeam(teamId)
    suspend fun getTeamOnce(teamId: String): TeamEntity? = teamDao.getById(teamId)
    suspend fun getPlayersOnce(teamId: String): List<PlayerEntity> = playerDao.getPlayersForTeamOnce(teamId)
    suspend fun searchByName(uid: String, query: String): List<TeamEntity> = teamDao.searchByName(uid, query)
    suspend fun searchPlayersByName(uid: String, query: String): List<PlayerEntity> = playerDao.searchByNameForUser(uid, query)

    /** Creates a brand new saved team with a starting roster of players, all in one go. */
    suspend fun createTeam(createdByUid: String, name: String, playerNames: List<Pair<String, PlayerRole>>): String {
        val teamId = UUID.randomUUID().toString()
        teamDao.upsert(TeamEntity(teamId, name, null, createdByUid))
        // Turn each (name, role) pair into a full PlayerEntity row and save them all at once.
        playerDao.upsertAll(playerNames.map { (name, role) -> PlayerEntity(UUID.randomUUID().toString(), teamId, name, role.name) })
        return teamId
    }

    /** Adds one extra player to an already-existing team. */
    suspend fun addPlayer(teamId: String, name: String, role: PlayerRole) {
        playerDao.upsertAll(listOf(PlayerEntity(UUID.randomUUID().toString(), teamId, name, role.name)))
    }

    /** Renames a team — `.copy(name = newName)` makes a new copy of the team with just the name changed. */
    suspend fun renameTeam(team: TeamEntity, newName: String) {
        teamDao.upsert(team.copy(name = newName))
    }
}
