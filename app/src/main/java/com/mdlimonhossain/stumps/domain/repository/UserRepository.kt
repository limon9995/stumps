package com.mdlimonhossain.stumps.domain.repository

import com.mdlimonhossain.stumps.data.local.db.AppDatabase
import com.mdlimonhossain.stumps.data.local.db.UserEntity
import com.mdlimonhossain.stumps.data.local.session.UserSessionStore
import com.mdlimonhossain.stumps.data.remote.auth.AuthRepository
import com.mdlimonhossain.stumps.data.remote.auth.AuthUser
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Bridges Firebase Auth (identity) with the local Room cache (offline-first profile data)
 * so the rest of the app can keep working, and attribute new matches/teams correctly,
 * even without network.
 *
 * In plain words: Firebase tells us WHO is logged in (their unique id, phone, email). This
 * class then makes sure we also have a matching profile row saved in our OWN local database,
 * so the rest of the app (teams, matches, tournaments) can just use that local profile without
 * needing to talk to Firebase every single time or worry about no internet connection.
 */
class UserRepository(
    private val authRepository: AuthRepository,
    private val db: AppDatabase,
    private val sessionStore: UserSessionStore
) {
    // A live stream of "whoever is currently signed in" — updates automatically on login/logout.
    val currentUser: Flow<AuthUser?> = authRepository.currentUser

    /** The cached local profile for whichever user is currently signed in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cachedProfile: Flow<UserEntity?> = sessionStore.currentUid.flatMapLatest { uid ->
        // flatMapLatest here means: "whenever the logged-in user's id changes, switch to
        // watching THAT user's profile row instead". If nobody is logged in (uid is null),
        // just emit "no profile" (null) instead of trying to look one up.
        if (uid == null) flowOf(null) else db.userDao().observe(uid)
    }

    /**
     * Called right after someone successfully logs in (via phone OTP or email/password). If
     * this is the very first time we've seen this uid, create a blank profile row for them
     * locally — they'll fill in the real details next, on the "complete your profile" screen.
     */
    suspend fun onSignedIn(authUser: AuthUser) {
        sessionStore.setCurrentUid(authUser.uid)
        val existing = db.userDao().getOnce(authUser.uid)
        if (existing == null) {
            db.userDao().upsert(
                UserEntity(
                    uid = authUser.uid,
                    name = authUser.name ?: "",
                    photoUrl = authUser.photoUrl,
                    phone = authUser.phone,
                    email = authUser.email,
                    role = PlayerRole.BATSMAN.name,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Saves the details the user filled in on the "complete your profile" screen (name, role, photo). */
    suspend fun completeProfile(uid: String, name: String, role: PlayerRole, photoUrl: String?) {
        val existing = db.userDao().getOnce(uid)
        db.userDao().upsert(
            (existing ?: UserEntity(uid, name, photoUrl, null, null, role.name, System.currentTimeMillis()))
                .copy(name = name, role = role.name, photoUrl = photoUrl ?: existing?.photoUrl)
        )
    }

    /** Logs the user out of Firebase and forgets which user was "current" locally. */
    suspend fun signOut() {
        authRepository.signOut()
        sessionStore.setCurrentUid(null)
    }

    // A small helper (currently unused elsewhere — see the big comment in Models.kt about
    // domain.model.User being an early sketch that isn't wired into the app any more) that
    // would convert a database UserEntity into the plain User data class.
    fun UserEntity.toDomain(): User = User(
        id = uid,
        name = name,
        photoUrl = photoUrl,
        phone = phone,
        email = email
    )
}
