package com.mdlimonhossain.stumps.ui.team

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.ListItemCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import kotlinx.coroutines.launch

/**
 * The "আমার টিম" (my teams) screen: a list of saved teams, plus a form to create a new one.
 * `onOpenTeam` is called with a team's id when its card is tapped, so the caller (StumpsApp's
 * NavHost) can navigate into that team's own detail page (see TeamDetailScreen.kt).
 */
@Composable
fun TeamManagementScreen(uid: String, onBack: () -> Unit, onOpenTeam: (teamId: String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: TeamManagementViewModel = viewModel(factory = TeamManagementViewModel.Factory(app.teamRepository, uid))
    val teams by viewModel.teams.collectAsState()
    var showCreate by remember { mutableStateOf(false) } // true = show the "new team" form instead of the list

    if (showCreate) {
        CreateTeamForm(
            onCancel = { showCreate = false },
            // Once the team is saved, jump straight into its (still-empty) detail page so the
            // user can start adding players right away, instead of landing back on the list.
            onCreate = { name, location -> viewModel.createTeam(uid, name, location) { teamId -> showCreate = false; onOpenTeam(teamId) } }
        )
        return // stop here — don't also draw the list below while the form is showing
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "আমার টিম", style = MaterialTheme.typography.headlineLarge)
        TextButton(onClick = onBack) { Text("হোমে ফিরে যাও") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
            Text("নতুন টিম তৈরি করো")
        }
        Spacer(Modifier.height(16.dp))

        if (teams.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AccountBox,
                title = "এখনো কোনো সেভ করা টিম নেই",
                subtitle = "উপরের বাটনে চেপে তোমার প্রথম টিম বানাও।",
                modifier = Modifier.weight(1f)
            )
        } else {
            // LazyColumn only actually draws the rows currently visible on screen (plus a small
            // buffer), instead of every row at once — important for lists that could get long.
            LazyColumn {
                // `key = { it.id }` helps Compose tell rows apart efficiently when the list
                // changes, instead of just relying on their position in the list.
                itemsIndexed(teams, key = { _, t -> t.id }) { index, team ->
                    TeamRow(uid = uid, team = team, modifier = Modifier.staggeredEntrance(index), onClick = { onOpenTeam(team.id) })
                }
            }
        }
    }
}

/**
 * One team card. Tapping the row itself opens that team's detail page (`onClick`); the trailing
 * Follow/Unfollow button is its own separate tap target so following a team doesn't accidentally
 * also navigate away — same pattern as ClubScreen's ClubRow.
 */
@Composable
private fun TeamRow(uid: String, team: TeamEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val isFollowing by app.followRepository.observeIsFollowing(uid, FollowTargetType.TEAM, team.id).collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    ListItemCard(
        modifier = modifier.padding(vertical = 6.dp),
        title = team.name,
        subtitle = team.location,
        onClick = onClick,
        trailing = {
            TextButton(onClick = {
                scope.launch { app.followRepository.toggleFollow(uid, FollowTargetType.TEAM, team.id, team.name, isFollowing) }
            }) { Text(if (isFollowing) "Following ✓" else "Follow") }
        }
    )
}

/**
 * The "create a new team" form — just a name and an optional location. Players used to be typed
 * in here all at once as a big block of text; now they're added one at a time afterwards from
 * the new team's own Players tab (see TeamDetailScreen.kt), which matches how the reference app
 * (and real cricket clubs) actually build up a squad over time rather than all in one go.
 */
@Composable
private fun CreateTeamForm(onCancel: () -> Unit, onCreate: (name: String, location: String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "নতুন টিম", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("টিমের নাম") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("এলাকা / শহর (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = name.isNotBlank(),
            onClick = { onCreate(name.trim(), location.trim().ifBlank { null }) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("সেভ করো") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("বাতিল") }
    }
}
