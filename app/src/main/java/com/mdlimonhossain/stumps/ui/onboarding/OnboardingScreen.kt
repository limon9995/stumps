package com.mdlimonhossain.stumps.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One page of the onboarding carousel: a big emoji, a title, and a short description. */
private data class OnboardingPage(val emoji: String, val title: String, val body: String)

// The 3 feature-highlight pages shown, in order, after the welcome screen — one per major feature.
private val pages = listOf(
    OnboardingPage(
        emoji = "🏏",
        title = "বল-বাই-বল স্কোর করো",
        body = "সহজে ম্যাচ সেটআপ করো আর প্রতিটা বলের রান, উইকেট, এক্সট্রা রেকর্ড করো — নেট ছাড়াই কাজ করে।"
    ),
    OnboardingPage(
        emoji = "🏆",
        title = "টুর্নামেন্ট চালাও",
        body = "একাধিক দল, ফিক্সচার, পয়েন্ট টেবিল, Orange Cap ও Purple Cap — পুরো টুর্নামেন্ট এক জায়গা থেকে ম্যানেজ করো।"
    ),
    OnboardingPage(
        emoji = "📡",
        title = "লাইভ ব্রডকাস্ট করো",
        body = "একটা কোড শেয়ার করো, বন্ধুরা লগইন ছাড়াই রিয়েল-টাইমে স্কোর দেখতে পারবে।"
    )
)

/**
 * A swipe-free "tap Next to continue" carousel showing off the app's 3 main features, shown
 * once (ever) on a fresh install, right after WelcomeScreen. See StumpsApp.kt for how
 * OnboardingStore remembers that this has already been shown, so it never appears again.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var pageIndex by remember { mutableStateOf(0) } // which page (0, 1, or 2) is currently showing
    val page = pages[pageIndex]
    val isLast = pageIndex == pages.lastIndex

    // Without this, the phone's back button/gesture would just close the whole app on ANY
    // onboarding page — surprising on page 2 or 3, where "go back one page" is what a person
    // actually expects. Only on page 0 (nothing earlier to go back to, inside this screen) does
    // back fall through to its normal behaviour instead.
    BackHandler(enabled = pageIndex > 0) { pageIndex -= 1 }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        // SpaceBetween pushes the content to the top and the buttons/dots to the bottom, with
        // empty space stretching in between — this is what the earlier bug (fixed after the
        // user reported a screenshot with no visible buttons) was missing: the content Column
        // below uses weight(1f) instead of fillMaxSize() so it only takes its FAIR SHARE of
        // space, leaving room for the buttons underneath it.
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = page.emoji, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))
            Text(text = page.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(text = page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }

        Column {
            // The row of small dots showing which page you're on — the current page's dot is
            // slightly bigger and a different colour than the others.
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                pages.indices.forEach { i ->
                    val active = i == pageIndex
                    // animateDpAsState/animateColorAsState smoothly grow + recolour a dot as the
                    // user moves between pages, instead of it instantly snapping to its new
                    // size/colour — a small touch, but it's what makes the dots feel connected
                    // to the swipe/tap instead of just being a static page counter.
                    val dotSize by animateDpAsState(targetValue = if (active) 10.dp else 8.dp, animationSpec = tween(220), label = "onboardingDotSize")
                    val dotColor by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        animationSpec = tween(220),
                        label = "onboardingDotColor"
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                // On the last page, the button finishes onboarding entirely; otherwise it just
                // moves to the next page.
                onClick = { if (isLast) onDone() else pageIndex += 1 },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLast) "শুরু করি" else "পরবর্তী")
            }
            // No point offering "skip" on the very last page — there's nothing left to skip.
            if (!isLast) {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("স্কিপ করো")
                }
            }
        }
    }
}
