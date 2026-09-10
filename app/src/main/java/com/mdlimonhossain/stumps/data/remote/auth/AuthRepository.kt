package com.mdlimonhossain.stumps.data.remote.auth

import kotlinx.coroutines.flow.Flow

/**
 * This file describes WHAT login/signup can do, without saying HOW it's done. That's called an
 * "interface" in programming — think of it like a job description: "whoever does this job must
 * be able to sign up, sign in, and log out", without saying exactly how they do those things.
 * The actual how-to-do-it code lives in FirebaseAuthRepository.kt, which "implements" this
 * interface using Firebase Authentication.
 *
 * Why bother with this extra layer? If we ever wanted to switch away from Firebase to a
 * different login service, we'd only need to write a new class that implements THIS SAME
 * interface — nothing else in the app (the screens, the view models) would need to change,
 * because they only ever talk to "an AuthRepository", not specifically to Firebase.
 */

/** A logged-in user's basic identity info, as far as the app cares. */
data class AuthUser(
    val uid: String, // a unique id Firebase gives every account
    val name: String?,
    val email: String?,
    val phone: String?,
    val photoUrl: String?,
    // True once this person has clicked the "verify your email" link Firebase emailed them.
    // Google sign-in accounts count as already verified (Google verified the address for us),
    // so this only really matters for the email+password sign-up path.
    val isEmailVerified: Boolean
)

interface AuthRepository {
    // A live stream of "who's logged in right now" — automatically updates on login/logout.
    val currentUser: Flow<AuthUser?>

    suspend fun signUpWithEmail(name: String, email: String, password: String): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>

    /**
     * Finishes a "Sign in with Google" flow. The idToken here is a proof-of-identity token that
     * Google's own sign-in screen hands back to us AFTER the user has already picked their
     * Google account and approved the sign-in there — we just forward that token to Firebase,
     * which checks it's genuinely from Google and signs the user in.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>

    /**
     * Asks Firebase for the freshest info about whoever is currently signed in. We need this
     * specifically after someone clicks the verification link in their email, because the app
     * has no other way of knowing that happened — Firebase doesn't push us an update, we have to
     * go ask "has this changed yet?".
     */
    suspend fun reloadCurrentUser(): Result<AuthUser>

    /** Sends a brand new "verify your email" link, in case the first one expired or got lost. */
    suspend fun resendEmailVerification(): Result<Unit>

    suspend fun signOut()
}
