package com.mdlimonhossain.stumps.ui.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.social.FollowEntity
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.ListItemCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import kotlinx.coroutines.launch

/**
 * The "Following" screen: everything this user currently follows (clubs, tournaments, teams,
 * players), with an unfollow button on each row. Follow BUTTONS themselves live on the actual
 * club/tournament/team/player screens — this screen is purely for reviewing/managing the list.
 */
@Composable
fun FollowingScreen(uid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val follows by app.followRepository.observeFollowsForUser(uid).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Following", style = MaterialTheme.typography.headlineLarge)
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Spacer(Modifier.height(12.dp))

        if (follows.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.FavoriteBorder,
                title = "এখনো কিছু Follow করা হয়নি",
                subtitle = "তুমি Clubs, Tournaments, Teams আর Players ফলো করতে পারো।",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn {
                itemsIndexed(follows, key = { _, it -> "${it.targetType}:${it.targetId}" }) { index, follow: FollowEntity ->
                    ListItemCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).staggeredEntrance(index),
                        title = follow.targetName,
                        subtitle = follow.targetType,
                        trailing = {
                            TextButton(onClick = {
                                scope.launch { app.followRepository.unfollow(uid, follow.targetType, follow.targetId) }
                            }) { Text("Unfollow") }
                        }
                    )
                }
            }
        }
    }
}
