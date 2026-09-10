package com.mdlimonhossain.stumps.data.remote.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The REAL implementation of AuthRepository, using Google's Firebase Authentication service to
 * actually do the login/signup work. See the big comment at the top of AuthRepository.kt for
 * why we keep an interface + implementation split like this.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    // callbackFlow lets us turn Firebase's old-style "listener" system (a function that gets
    // called every time something changes) into a modern Flow that the rest of the app can
    // collect from like any other stream of values.
    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        // awaitClose runs when nobody is listening to this Flow any more — we use it to clean
        // up properly by removing the listener, so we don't leak memory.
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUpWithEmail(name: String, email: String, password: String): Result<AuthUser> =
        // runCatching wraps everything in a try/catch for us — if anything throws an error
        // (like "email already in use"), it gets turned into a failed Result instead of
        // crashing the app, so the screen can show a nice error message.
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            // Firebase creates the account with no name set by default, so we set it separately.
            result.user?.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name).build()
            )?.await()
            // Straight away, email Firebase's own "please verify this address" link — the
            // screen will show a "check your inbox" step until the user clicks it.
            result.user?.sendEmailVerification()?.await()
            result.user?.toAuthUser() ?: error("Sign up succeeded but no user was returned")
        }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.toAuthUser() ?: error("Sign in succeeded but no user was returned")
        }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> =
        runCatching {
            // GoogleAuthProvider.getCredential turns the raw idToken (just a proof of identity)
            // into a "credential" object Firebase understands, then signInWithCredential does
            // the actual sign-in.
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user?.toAuthUser() ?: error("Google sign-in succeeded but no user was returned")
        }

    override suspend fun reloadCurrentUser(): Result<AuthUser> =
        runCatching {
            val user = auth.currentUser ?: error("কোনো ইউজার এখন সাইন-ইন করা নেই")
            // .reload() goes and asks Firebase's servers for the latest state of this account —
            // without this, "isEmailVerified" would still show the OLD value from before the
            // user clicked the link, since the app has no other way of finding out it changed.
            user.reload().await()
            user.toAuthUser()
        }

    override suspend fun resendEmailVerification(): Result<Unit> =
        runCatching {
            val user = auth.currentUser ?: error("কোনো ইউজার এখন সাইন-ইন করা নেই")
            user.sendEmailVerification().await()
        }

    override suspend fun signOut() {
        auth.signOut()
    }

    // A small helper that converts Firebase's own FirebaseUser type into our simpler AuthUser
    // data class, so the rest of the app never has to depend directly on Firebase's classes.
    private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        name = displayName,
        email = email,
        phone = phoneNumber,
        photoUrl = photoUrl?.toString(),
        isEmailVerified = isEmailVerified
    )
}
