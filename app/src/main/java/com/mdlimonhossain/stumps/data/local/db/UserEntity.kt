package com.mdlimonhossain.stumps.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The local database table for a logged-in user's profile — name, photo, phone/email, role.
 * This is our OWN copy, kept alongside Firebase's login record, so the app can work offline
 * and doesn't need to ask Firebase for the same info over and over.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String, // the same unique id Firebase gives this user
    val name: String,
    val photoUrl: String?,
    val phone: String?,
    val email: String?,
    val role: String, // stored as text (a PlayerRole enum name, e.g. "BATSMAN")
    val createdAt: Long
)
