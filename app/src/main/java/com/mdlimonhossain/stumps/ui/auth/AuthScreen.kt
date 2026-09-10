package com.mdlimonhossain.stumps.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.mdlimonhossain.stumps.R

/**
 * This is a "Jetpack Compose" screen — the UI toolkit this whole app is built with. A few
 * quick things that are worth knowing since this pattern repeats in EVERY screen file:
 *
 * - A function marked @Composable describes a piece of UI (like a screen, or one button) by
 *   just calling other @Composable functions (Column, Text, Button...) — you're describing
 *   WHAT should be on screen, and Compose figures out how to actually draw it.
 * - `remember { mutableStateOf(...) }` creates a little piece of memory that survives while
 *   the screen is visible — e.g. `var email by remember { mutableStateOf("") }` remembers
 *   whatever the user has typed into a text field so far. Whenever this value changes,
 *   Compose automatically redraws whatever part of the screen uses it — we never manually
 *   say "now update the text on screen", it just happens.
 * - Column/Row/Spacer arrange things vertically/horizontally/with gaps — think of them like
 *   simple building blocks for laying widgets out, similar to a very simplified web page layout.
 *
 * This particular screen has a "hero header + white sheet" layout — a coloured gradient block
 * at the top with the app's branding, and a rounded-corner card below it holding the actual
 * login form. This is a very common modern-app pattern, and it's the same gradient colours
 * used on WelcomeScreen.kt, so the two screens feel like one connected experience.
 *
 * There are only two ways in: email + password, or Google. There used to be a phone number +
 * SMS OTP option too, but that was removed — Google sign-in covers the "quick, no password to
 * remember" use case just as well, and it means one less thing that needs SMS/region setup in
 * Firebase to work.
 */

/** The very first screen a signed-out user sees: log in / sign up with email, or with Google. */
@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onSignUpWithEmail: (name: String, email: String, password: String) -> Unit,
    onSignInWithEmail: (email: String, password: String) -> Unit,
    onSignInWithGoogleIdToken: (String) -> Unit,
    onGoogleSignInFailed: (String) -> Unit,
    onCheckEmailVerifiedAndContinue: () -> Unit,
    onResendVerificationEmail: () -> Unit,
    onWatchLive: () -> Unit = {}
) {
    val context = LocalContext.current

    // Sets up the Google Sign-In popup, telling it which Firebase project to hand the result
    // back to via the Web client ID (see strings.xml for where that value comes from).
    val googleSignInClient = remember {
        // R.string.default_web_client_id isn't something we wrote ourselves — the Google
        // Services Gradle plugin auto-generates it from the "client_type: 3" entry inside
        // google-services.json at build time, so it always matches whatever's in Firebase.
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    // rememberLauncherForActivityResult wires up Android's "launch another screen and wait for
    // it to hand something back" pattern — here, that "other screen" is Google's own account
    // picker, which isn't part of our app at all.
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                onSignInWithGoogleIdToken(idToken)
            } else {
                onGoogleSignInFailed("Google থেকে সঠিক টোকেন পাওয়া যায়নি")
            }
        } catch (e: ApiException) {
            // The user closing the Google popup without picking an account also lands here —
            // that's expected/normal, not really an "error" worth alarming anyone about.
            onGoogleSignInFailed(e.message ?: "Google সাইন-ইন বাতিল হয়েছে")
        }
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1B6E43), Color(0xFF0F4A2C))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // ---- Hero header: gradient background, app badge, title, tagline ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundGradient, shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .padding(top = 48.dp, bottom = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(text = "Stumps-এ স্বাগতম", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "টুর্নামেন্ট আর ম্যাচ score করতে সাইন-ইন করো",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ---- The form "sheet": pulled up slightly over the header for a layered look ----
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-24).dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // uiState.awaitingEmailVerification means: an account exists and a verification
                // link has been emailed, but nobody's clicked it yet — show that step instead of
                // the normal login form until they have.
                if (uiState.awaitingEmailVerification) {
                    EmailVerificationSection(
                        pendingEmail = uiState.pendingEmail,
                        onCheckEmailVerifiedAndContinue = onCheckEmailVerifiedAndContinue,
                        onResendVerificationEmail = onResendVerificationEmail
                    )
                } else {
                    EmailAuthSection(onSignUpWithEmail, onSignInWithEmail)
                }

                // Only show the error text if there actually IS an error message.
                uiState.errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start, fontSize = 13.sp)
                }
                // A softer, non-error status update, like "verification link resent".
                uiState.infoMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Start, fontSize = 13.sp)
                }
                if (uiState.isLoading) {
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // The Google button doesn't make sense while we're in the middle of the
                // "check your email" step — hide it there to keep that step focused.
                if (!uiState.awaitingEmailVerification) {
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(text = "  অথবা  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        GoogleBadge()
                        Spacer(Modifier.width(10.dp))
                        Text(text = "Google দিয়ে চালিয়ে যাও", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onWatchLive) { Text("শুধু লাইভ ম্যাচ দেখতে চাও? (লগইন ছাড়া)") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Shown right after signing up (or signing in to an account that was never finished verifying):
 * tells the user we've emailed them a link, and gives them a way to say "done, let me in" or to
 * get a fresh link sent if the old one expired or never arrived.
 */
@Composable
private fun EmailVerificationSection(
    pendingEmail: String?,
    onCheckEmailVerifiedAndContinue: () -> Unit,
    onResendVerificationEmail: () -> Unit
) {
    Text(text = "ইমেইল verify করো", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        text = "${pendingEmail ?: "তোমার ইমেইল"} ঠিকানায় একটা verification link পাঠানো হয়েছে। ইমেইল খুলে link-এ click করো, তারপর নিচের বাটনে চাপো।",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(18.dp))
    Button(
        onClick = onCheckEmailVerifiedAndContinue,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = roundedFieldShape
    ) {
        Text("verify করেছি, continue করো")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onResendVerificationEmail, modifier = Modifier.fillMaxWidth()) {
        Text("লিংক পাইনি, আবার পাঠাও")
    }
}

/** A small circular badge with a bold "G" — a simplified stand-in for the Google logo, no external image asset needed. */
@Composable
private fun GoogleBadge() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFF4285F4)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "G", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private val roundedFieldShape = RoundedCornerShape(14.dp)

/** The email/password login+signup form — the main way into the app besides Google. */
@Composable
private fun EmailAuthSection(
    onSignUpWithEmail: (name: String, email: String, password: String) -> Unit,
    onSignInWithEmail: (email: String, password: String) -> Unit
) {
    // One form does double duty for both signup AND login — isSignUp just decides whether we
    // show the extra "name" field and which button text/action to use.
    var isSignUp by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (isSignUp) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("নাম") }, shape = roundedFieldShape, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
    }
    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("ইমেইল") }, shape = roundedFieldShape, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("পাসওয়ার্ড") },
        // Hides the typed characters as dots, like every password field should.
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        shape = roundedFieldShape,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            if (isSignUp) onSignUpWithEmail(name, email, password) else onSignInWithEmail(email, password)
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = roundedFieldShape
    ) {
        Text(if (isSignUp) "Sign Up" else "Login")
    }
    // Lets the user flip between "I'm new" and "I already have an account" without leaving the screen.
    TextButton(onClick = { isSignUp = !isSignUp }, modifier = Modifier.fillMaxWidth()) {
        Text(if (isSignUp) "আগে থেকে অ্যাকাউন্ট আছে? Login করুন" else "নতুন? Sign Up করুন")
    }
}
