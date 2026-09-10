package com.mdlimonhossain.stumps.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdlimonhossain.stumps.R
import com.mdlimonhossain.stumps.ui.designsystem.ScreenFadeThrough

/**
 * The very first screen a brand new user ever sees — a big branded "hero" screen with the app
 * logo, name, and a "শুরু করি" (Get Started) button leading into OnboardingScreen. Purely
 * visual/marketing — it doesn't check or save anything itself, StumpsApp.kt handles moving on
 * once onGetStarted fires.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    // A vertical gradient from bright pitch-green at the top to near-black at the bottom, for
    // a bit of visual depth instead of one flat colour.
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1B6E43), Color(0xFF0F4A2C), Color(0xFF0D1712))
    )

    // The very first thing a brand new user ever sees fades + grows gently into view, rather
    // than just being there instantly the moment the app finishes launching — a small but
    // deliberate "first impression" touch.
    ScreenFadeThrough {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The logo + title + tagline block takes up all the LEFTOVER space (weight(1f)) above
        // the button, and centres its contents within that space.
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // A soft, semi-transparent white circle behind the logo, like a spotlight badge.
            Column(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Reuse the exact same app-icon artwork used for the launcher icon, so the
                // in-app branding matches the icon on the phone's home screen.
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = "STUMPS",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "স্কোর করো. টুর্নামেন্ট চালাও. লাইভ শেয়ার করো.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // A custom-coloured button (white background, dark green text) instead of the default
        // Material colours, so it stands out clearly against the dark green gradient behind it.
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF0F4A2C)
            )
        ) {
            Text(text = "শুরু করি", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    }
}
