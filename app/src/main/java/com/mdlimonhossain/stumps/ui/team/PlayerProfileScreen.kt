package com.mdlimonhossain.stumps.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.export.ShareUtils
import com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity
import com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType
import com.mdlimonhossain.stumps.ui.common.AnimatedTabChip
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.PlayerStatisticsBreakdown
import com.mdlimonhossain.stumps.ui.designsystem.RecentFormCircle
import com.mdlimonhossain.stumps.ui.designsystem.StatCard
import com.mdlimonhossain.stumps.ui.profile.ProfileViewModel
import com.mdlimonhossain.stumps.ui.theme.PitchGreen
import kotlinx.coroutines.launch

/** Which of the two tabs on a player's own profile page is currently showing. */
private enum class PlayerProfileTab { OVERVIEW, STATISTICS }

/**
 * A saved team's own player's profile page — reached by tapping "View Profile" on that team's
 * Players tab. This reuses `ProfileViewModel` (see ui/profile/ProfileViewModel.kt) unchanged: it
 * was already written generically around "a uid + a player name", not specifically the signed-in
 * user, so it works exactly as well here to compute a saved player's career batting/bowling/
 * fielding record from every match `viewerUid` has recorded.
 *
 * Every player in this app is currently just a name typed into a team roster — there's no way
 * yet for a real person to "claim" that player as their own linked Stumps account — so this
 * screen always shows the "Unregistered Player" state, with a Profile ID built from the player's
 * own database row id (same pattern TeamDetailScreen's Team Overview uses for its own "Team ID"),
 * rather than a real, searchable account identifier.
 */
@Composable
fun PlayerProfileScreen(playerId: String, viewerUid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication

    var player by remember { mutableStateOf<PlayerEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(playerId) {
        player = app.teamRepository.getPlayerOnce(playerId)
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val p = player
    if (p == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Filled.AccountBox, title = "এই খেলোয়াড় খুঁজে পাওয়া যায়নি")
        }
        return
    }

    val viewModel: ProfileViewModel = viewModel(
        key = playerId,
        factory = ProfileViewModel.Factory(viewerUid, p.name, app.statsRepository)
    )
    val uiState by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(PlayerProfileTab.OVERVIEW) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "ফিরে যাও",
                modifier = Modifier.clickable(onClick = onBack)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedTabChip("Player Overview", tab == PlayerProfileTab.OVERVIEW) { tab = PlayerProfileTab.OVERVIEW }
            AnimatedTabChip("Statistics", tab == PlayerProfileTab.STATISTICS) { tab = PlayerProfileTab.STATISTICS }
        }
        Spacer(Modifier.height(20.dp))

        when (tab) {
            PlayerProfileTab.OVERVIEW -> PlayerOverviewTab(player = p, viewerUid = viewerUid, careerStats = uiState.stats.careerStats, recentForm = uiState.stats.recentForm)
            PlayerProfileTab.STATISTICS -> PlayerStatisticsBreakdown(uiState.stats.statsByFormat, uiState.stats.recentForm)
        }
    }
}

@Composable
private fun PlayerOverviewTab(player: PlayerEntity, viewerUid: String, careerStats: com.mdlimonhossain.stumps.domain.repository.CareerStats, recentForm: List<Int?>) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val scope = rememberCoroutineScope()
    var showUnregisteredInfo by remember { mutableStateOf(false) }
    val isFollowing by app.followRepository.observeIsFollowing(viewerUid, FollowTargetType.PLAYER, player.id).collectAsState(initial = false)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // No photo-upload feature exists for saved players, so a big initials circle stands in
        // for a profile photo — same approach as the signed-in user's own ProfileScreen.
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(PitchGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = player.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(text = player.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showUnregisteredInfo = true }) {
            Text(text = "Unregistered Player", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "এর মানে কী?",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text = "Profile ID : ${player.id.take(8)}",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    app.followRepository.toggleFollow(viewerUid, FollowTargetType.PLAYER, player.id, player.name, isFollowing)
                }
            }) { Text(if (isFollowing) "Following ✓" else "Follow") }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        ShareUtils.shareText(context, "${player.name}-এর Stumps প্রোফাইল দেখো — ${careerStats.runs} রান, ${careerStats.wickets} উইকেট!")
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = "শেয়ার করো", modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Batting",
                headerColor = Color(0xFFDB8A93),
                rows = listOf(
                    "Runs" to careerStats.runs.toString(),
                    "Average" to com.mdlimonhossain.stumps.ui.designsystem.oneDecimal(careerStats.battingAverage),
                    "High Score" to careerStats.highScore.toString()
                )
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Bowling",
                headerColor = Color(0xFF7C9E82),
                rows = listOf(
                    "Wickets" to careerStats.wickets.toString(),
                    "Average" to (if (careerStats.wickets == 0) "-" else com.mdlimonhossain.stumps.ui.designsystem.oneDecimal(careerStats.bowlingAverage)),
                    "Best Bowling" to careerStats.bestBowlingFigures
                )
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Fielding",
                headerColor = Color(0xFF6F8FA6),
                rows = listOf(
                    "Catches" to careerStats.catches.toString(),
                    "Stumpings" to careerStats.stumpings.toString(),
                    "Runouts" to careerStats.runOuts.toString()
                )
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Recent Form", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val slots = (0 until 5).map { recentForm.getOrNull(it) }
            slots.forEachIndexed { index, runs ->
                RecentFormCircle(runs = runs, isNewest = index == 0)
            }
        }
    }

    if (showUnregisteredInfo) {
        AlertDialog(
            onDismissRequest = { showUnregisteredInfo = false },
            title = { Text("Unregistered Player") },
            text = {
                Text("এই খেলোয়াড়কে শুধু নাম দিয়ে টিমে যোগ করা হয়েছে — এটা কোনো real Stumps অ্যাকাউন্টের সাথে যুক্ত না, তাই এই খেলোয়াড় নিজে লগইন করে নিজের প্রোফাইল পরিচালনা করতে পারবে না। এখানে দেখানো সব পরিসংখ্যান শুধু তোমার রেকর্ড করা ম্যাচ থেকে হিসাব করা হয়েছে।")
            },
            confirmButton = { TextButton(onClick = { showUnregisteredInfo = false }) { Text("বুঝেছি") } }
        )
    }
}
