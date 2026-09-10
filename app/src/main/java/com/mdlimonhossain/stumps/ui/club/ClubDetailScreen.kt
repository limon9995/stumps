package com.mdlimonhossain.stumps.ui.club

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.club.ClubEntity
import com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType
import com.mdlimonhossain.stumps.ui.theme.PitchGreen
import kotlinx.coroutines.launch

/**
 * A read-only view of ONE club — reached by tapping a club in Search results. Unlike ClubScreen
 * (which only ever shows clubs THIS device owns, straight from local Room), this screen might be
 * showing a club some OTHER user registered, so it checks the local database first and falls
 * back to the shared cloud directory if it's not found there (see ClubRepository.getCloudClub).
 */
@Composable
fun ClubDetailScreen(clubId: String, viewerUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var club by remember { mutableStateOf<ClubEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(clubId) {
        club = app.clubRepository.getClubOnce(clubId) ?: app.clubRepository.getCloudClub(clubId)
        isLoading = false
    }

    val isFollowing by app.followRepository.observeIsFollowing(viewerUid, FollowTargetType.CLUB, clubId).collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            club == null -> Text(text = "এই ক্লাব খুঁজে পাওয়া যায়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val c = club!!
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(PitchGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = c.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text = c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${c.city} • প্রতিষ্ঠিত ${c.establishedYear}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = if (c.ballType == "LEATHER") "লেদার বল" else "টেনিস বল", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {
                        scope.launch { app.followRepository.toggleFollow(viewerUid, FollowTargetType.CLUB, c.id, c.name, isFollowing) }
                    }) { Text(if (isFollowing) "Following ✓" else "Follow") }
                }
            }
        }
    }
}
