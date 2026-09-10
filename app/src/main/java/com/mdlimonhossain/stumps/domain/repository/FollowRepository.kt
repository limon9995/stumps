package com.mdlimonhossain.stumps.domain.repository

import com.mdlimonhossain.stumps.data.local.db.social.FollowDao
import com.mdlimonhossain.stumps.data.local.db.social.FollowEntity
import com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType
import kotlinx.coroutines.flow.Flow

/**
 * The "middleman" (repository) for the Following feature — lets a user follow/unfollow clubs,
 * tournaments, teams, and players, and lists everything they currently follow. This is purely
 * local (Room) for now, same as everything else in Phase 1 — no cloud sync of who-follows-what yet.
 */
class FollowRepository(private val followDao: FollowDao) {
    fun observeFollowsForUser(uid: String): Flow<List<FollowEntity>> = followDao.observeFollowsForUser(uid)

    fun observeIsFollowing(uid: String, type: FollowTargetType, targetId: String): Flow<Boolean> =
        followDao.observeIsFollowing(uid, type.name, targetId)

    /** Flips follow-state for one thing: follows it if not already followed, unfollows it if it is. */
    suspend fun toggleFollow(uid: String, type: FollowTargetType, targetId: String, targetName: String, isCurrentlyFollowing: Boolean) {
        if (isCurrentlyFollowing) {
            followDao.delete(uid, type.name, targetId)
        } else {
            followDao.upsert(
                FollowEntity(
                    followerUid = uid,
                    targetType = type.name,
                    targetId = targetId,
                    targetName = targetName,
                    followedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun unfollow(uid: String, type: String, targetId: String) {
        followDao.delete(uid, type, targetId)
    }
}
