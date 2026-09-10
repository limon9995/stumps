package com.mdlimonhossain.stumps.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A "Dao" (Data Access Object) is a Room interface where we just describe WHAT database
 * queries we want, and Room writes the actual SQL code for us behind the scenes. We only
 * write the @Query text (real SQL) and the function signature — no manual database code.
 *
 * This one handles reading and writing rows in the "users" table.
 */
@Dao
interface UserDao {
    // @Upsert = "update if this row already exists (matched by primary key), otherwise insert
    // it as a new row". Handy shortcut so we don't have to check "does this exist?" ourselves.
    @Upsert
    suspend fun upsert(user: UserEntity)

    // Returns a live Flow — Room automatically re-runs this query and sends a fresh result
    // every time the "users" table changes, so anyone watching gets kept up to date for free.
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    fun observe(uid: String): Flow<UserEntity?>

    // Same query as above, but reads ONCE and stops — no ongoing live updates. Useful when we
    // just need a quick one-time answer (like "does this profile already exist?").
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getOnce(uid: String): UserEntity?
}
