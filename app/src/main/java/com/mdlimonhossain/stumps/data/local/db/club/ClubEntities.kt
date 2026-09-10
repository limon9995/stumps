package com.mdlimonhossain.stumps.data.local.db.club

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A "club" here is a cricket club or organisation someone registers on the app — think of it
 * like a team's parent organisation, the kind of thing that runs its OWN tournaments (e.g. "Sital
 * Felicity Youngstars Sports Club" hosting "The Sital Cup 2026" in the reference app). It's a
 * separate concept from a TeamEntity (one side that plays in a match) — a club is more like a
 * whole cricketing organisation/brand.
 */
@Entity(tableName = "clubs")
data class ClubEntity(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val establishedYear: Int,
    val ballType: String, // "LEATHER" or "TENNIS" — kept as plain text, same pattern as PlayerEntity.role
    val createdByUid: String, // which user registered this club
    val createdAt: Long
)

@Dao
interface ClubDao {
    @Upsert
    suspend fun upsert(club: ClubEntity)

    // Every club a given user has registered, newest first — shown on the "আমার ক্লাব" (my clubs) screen.
    @Query("SELECT * FROM clubs WHERE createdByUid = :uid ORDER BY createdAt DESC")
    fun observeClubsForUser(uid: String): Flow<List<ClubEntity>>

    @Query("SELECT * FROM clubs WHERE id = :id")
    suspend fun getById(id: String): ClubEntity?

    // A simple case-insensitive "contains" search across one user's own registered clubs — used
    // by the Search screen (see TournamentDao.searchByName for why this is per-user, not global).
    @Query("SELECT * FROM clubs WHERE createdByUid = :uid AND name LIKE '%' || :query || '%' ORDER BY name")
    suspend fun searchByName(uid: String, query: String): List<ClubEntity>
}
