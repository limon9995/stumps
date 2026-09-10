package com.mdlimonhossain.stumps.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mdlimonhossain.stumps.data.remote.auth.AuthRepository
import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A "ViewModel" sits BETWEEN a screen and the repositories (database/network). Its job is to
 * hold the screen's current state (is it loading? is there an error?) and to call the right
 * repository functions when the user taps a button — the screen itself stays "dumb" and just
 * displays whatever the ViewModel tells it to.
 *
 * Why bother with this extra layer instead of just calling the repository straight from the
 * screen? Two big reasons: (1) a ViewModel survives things like the phone rotating the screen,
 * so we don't lose the user's progress, and (2) it keeps all the "what happens when you tap
 * this button" logic in one testable place, separate from how it LOOKS on screen.
 *
 * This file's job specifically: everything about logging in / signing up.
 */

/** Everything the login/signup screen needs to know to draw itself correctly right now. */
data class AuthUiState(
    val isLoading: Boolean = false, // true while we're waiting for Firebase to respond — shows a spinner
    val errorMessage: String? = null, // set if something went wrong, so we can show it in red text
    val infoMessage: String? = null, // set for a non-error status update, like "verification link resent"
    val isSignedIn: Boolean = false,
    val currentUid: String? = null,
    // true while we're waiting for the user to go click the verification link we emailed them —
    // the screen shows a "check your inbox" step instead of the normal form while this is true.
    val awaitingEmailVerification: Boolean = false,
    val pendingEmail: String? = null // which address the verification link was sent to
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // The "_uiState" (with the underscore) is the PRIVATE, changeable version only this class
    // can update. "uiState" (no underscore) is the PUBLIC, read-only version the screen actually
    // watches. This "underscore trick" is a very common Kotlin pattern: it stops the screen from
    // accidentally being able to change the state directly — only this ViewModel is allowed to.
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Called when the user taps "Sign Up" on the email tab. */
    fun signUpWithEmail(name: String, email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        // viewModelScope.launch starts a "coroutine" — a chunk of code that can pause (e.g.
        // while waiting for the internet) without freezing the whole app. It automatically
        // gets cancelled if the screen is closed, so we don't waste effort on work nobody needs any more.
        viewModelScope.launch {
            authRepository.signUpWithEmail(name, email, password)
                // onSuccess/onFailure run depending on whether signup worked or not — this
                // comes from Kotlin's Result type, a clean way to handle "this might fail"
                // without needing a separate try/catch block here.
                .onSuccess { user -> handleFreshlyAuthenticatedUser(user) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
                }
        }
    }

    /** Called when the user taps "Login" on the email tab. */
    fun signInWithEmail(email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            authRepository.signInWithEmail(email, password)
                .onSuccess { user -> handleFreshlyAuthenticatedUser(user) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
                }
        }
    }

    /** Called once Google's own sign-in screen hands back a successful result, with the idToken it produced. */
    fun signInWithGoogle(idToken: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            authRepository.signInWithGoogleIdToken(idToken)
                .onSuccess { user -> handleFreshlyAuthenticatedUser(user) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
                }
        }
    }

    /** Called if the Google sign-in popup itself failed or was cancelled, before we even got an idToken. */
    fun onGoogleSignInFailed(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
    }

    /**
     * Shared by all three sign-in paths above: decides whether this person is fully ready to
     * enter the app, or needs to go verify their email address first. Google accounts are
     * already verified by Google, so in practice this "waiting" step only ever shows up for the
     * email+password path.
     */
    private suspend fun handleFreshlyAuthenticatedUser(user: com.mdlimonhossain.stumps.data.remote.auth.AuthUser) {
        if (user.isEmailVerified) {
            userRepository.onSignedIn(user)
            _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true, currentUid = user.uid)
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                awaitingEmailVerification = true,
                pendingEmail = user.email
            )
        }
    }

    /** Called when the user taps "verify করেছি, continue করো" after (hopefully) clicking the emailed link. */
    fun checkEmailVerifiedAndContinue() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            authRepository.reloadCurrentUser()
                .onSuccess { user ->
                    if (user.isEmailVerified) {
                        userRepository.onSignedIn(user)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isSignedIn = true,
                            currentUid = user.uid,
                            awaitingEmailVerification = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "এখনো verify করা হয়নি — ইমেইল চেক করে link-এ click করে আসো"
                        )
                    }
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    /** Called when the user taps "আবার পাঠাও" (resend) on the "check your inbox" step. */
    fun resendVerificationEmail() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            authRepository.resendEmailVerification()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, infoMessage = "নতুন verification link পাঠানো হয়েছে")
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    /** Called when the user finishes the "complete your profile" screen after their very first login. */
    fun completeProfile(uid: String, name: String, role: PlayerRole, photoUrl: String?) {
        viewModelScope.launch {
            userRepository.completeProfile(uid, name, role, photoUrl)
        }
    }

    /** Called when the user taps "Logout" on the profile screen. */
    fun signOut() {
        viewModelScope.launch {
            userRepository.signOut()
            _uiState.value = AuthUiState() // reset back to a completely blank state
        }
    }

    /**
     * A "Factory" is Android's required way of creating a ViewModel that needs constructor
     * arguments (here, the two repositories). Android creates ViewModels for us behind the
     * scenes (so they can survive screen rotation etc.), so we can't just write
     * "AuthViewModel(authRepository, userRepository)" directly on the screen — instead we hand
     * Android this Factory, and it calls `create()` for us at the right moment.
     */
    class Factory(
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(authRepository, userRepository) as T
    }
}
