package com.mdlimonhossain.stumps.data.local.db.social

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** What KIND of thing is being followed — matches the reference app's "Clubs, Tournaments, Teams and Players" following options. */
enum class FollowTargetType { CLUB, TOURNAMENT, TEAM, PLAYER }

/**
 * One row = "this user follows that thing". The primary key is all three of followerUid +
 * targetType + targetId together, so a user can only follow the same thing once (following it
 * again just overwrites the same row instead of creating a duplicate).
 *
 * `targetName` is a deliberate small duplication — we copy the followed thing's display name in
 * here at follow-time, so the Following screen can just list rows straight from this ONE table
 * without needing to separately look up a club/tournament/team/player's current name every time.
 */
@Entity(tableName = "follows", primaryKeys = ["followerUid", "targetType", "targetId"])
data class FollowEntity(
    val followerUid: String,
    val targetType: String, // a FollowTargetType enum name, stored as plain text (same pattern as PlayerEntity.role)
    val targetId: String,
    val targetName: String,
    val followedAt: Long
)

@Dao
interface FollowDao {
    @Upsert
    suspend fun upsert(follow: FollowEntity)

    @Query("DELETE FROM follows WHERE followerUid = :uid AND targetType = :type AND targetId = :targetId")
    suspend fun delete(uid: String, type: String, targetId: String)

    // Everything one user follows, newest-followed first — shown on the "Following" screen.
    @Query("SELECT * FROM follows WHERE followerUid = :uid ORDER BY followedAt DESC")
    fun observeFollowsForUser(uid: String): Flow<List<FollowEntity>>

    // A live true/false for one specific thing — used to draw a Follow/Unfollow button's current state.
    @Query("SELECT EXISTS(SELECT 1 FROM follows WHERE followerUid = :uid AND targetType = :type AND targetId = :targetId)")
    fun observeIsFollowing(uid: String, type: String, targetId: String): Flow<Boolean>
}
