package com.mdlimonhossain.stumps.ui.tournament

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance

/**
 * The tournament list screen: every tournament this user runs, plus a form to create a new one.
 * `startWithCreateForm` lets a caller (e.g. the "Create Tournament" item in the side drawer)
 * jump straight into the creation form instead of showing the list first.
 */
@Composable
fun TournamentListScreen(
    uid: String,
    onOpenTournament: (String) -> Unit,
    onBack: () -> Unit,
    onOpenCreateTeam: () -> Unit,
    startWithCreateForm: Boolean = false
) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: TournamentListViewModel = viewModel(factory = TournamentListViewModel.Factory(app.tournamentRepository, uid))
    val tournaments by viewModel.tournaments.collectAsState()
    // Saved teams are needed for the "pick participating teams" step of the create form —
    // reading them straight from the repository here rather than via a ViewModel, since this
    // screen only needs a simple read, not any special handling.
    val savedTeams by app.teamRepository.observeTeamsForUser(uid).collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(startWithCreateForm) }

    if (showCreate) {
        TournamentSetupForm(
            savedTeams = savedTeams,
            onOpenCreateTeam = onOpenCreateTeam,
            onCancel = { showCreate = false },
            onCreate = { name, overs, venue, teamIds ->
                viewModel.createTournament(uid, name, overs, venue, teamIds) { id ->
                    showCreate = false
                    onOpenTournament(id) // jump straight into the new tournament once it's created
                }
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "টুর্নামেন্ট", style = MaterialTheme.typography.headlineLarge)
        TextButton(onClick = onBack) { Text("হোমে ফিরে যাও") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
            Text("নতুন টুর্নামেন্ট")
        }
        Spacer(Modifier.height(16.dp))

        if (tournaments.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = "এখনো কোনো টুর্নামেন্ট নেই",
                subtitle = "উপরের বাটনে চেপে তোমার প্রথম টুর্নামেন্ট বানাও।",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn {
                itemsIndexed(tournaments, key = { _, t -> t.id }) { index, t ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .staggeredEntrance(index),
                        onClick = { onOpenTournament(t.id) } // tapping the whole card opens that tournament
                    ) {
                        Text(text = t.name, style = MaterialTheme.typography.titleLarge)
                        Text(text = "${t.oversPerMatch} ওভার প্রতি ম্যাচ", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** The "create a new tournament" form: name, overs, venue, and picking which saved teams take part. */
@Composable
private fun TournamentSetupForm(
    savedTeams: List<TeamEntity>,
    onOpenCreateTeam: () -> Unit,
    onCancel: () -> Unit,
    onCreate: (name: String, overs: Int, venue: String?, teamIds: List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var oversText by remember { mutableStateOf("20") }
    var venue by remember { mutableStateOf("") }
    // A Set of the currently-checked team ids — using a Set (not a List) makes "is this team
    // checked?" and "toggle this team" both quick and simple to write.
    val selected = remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "নতুন টুর্নামেন্ট", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("টুর্নামেন্টের নাম") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = oversText, onValueChange = { oversText = it }, label = { Text("প্রতি ম্যাচে কত ওভার") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("ভেন্যু (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        Text(text = "অংশগ্রহণকারী টিম বেছে নাও (কমপক্ষে ২টি)", style = MaterialTheme.typography.titleLarge)
        if (savedTeams.isEmpty()) {
            // The "তৈরি করো" button below stays disabled until at least 2 teams are picked —
            // but with ZERO saved teams to even pick from, that was easy to miss (just a small
            // grey line of text). A proper callout with its own button makes the blocker (and
            // the fix) obvious instead of leaving someone stuck wondering why the button won't
            // light up.
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(text = "এখনো কোনো টিম সেভ করা নেই", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "টুর্নামেন্টে অংশ নেওয়ার জন্য কমপক্ষে ২টি টিম লাগবে — আগে টিম বানিয়ে নাও।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenCreateTeam) { Text("টিম বানাও") }
            }
        }
        // One checkbox row per saved team — tapping either the checkbox or the row toggles
        // whether that team is included.
        savedTeams.forEach { team ->
            val isSelected = selected.value.contains(team.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, onClick = {
                        // Add or remove this team's id from the selected set.
                        selected.value = if (isSelected) selected.value - team.id else selected.value + team.id
                    })
            ) {
                Checkbox(checked = isSelected, onCheckedChange = null) // null here because the Row above already handles the tap
                Text(text = team.name, modifier = Modifier.padding(top = 12.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        val overs = oversText.toIntOrNull() ?: 0
        Button(
            enabled = name.isNotBlank() && overs > 0 && selected.value.size >= 2, // need a name, real overs, and at least 2 teams
            onClick = { onCreate(name, overs, venue.ifBlank { null }, selected.value.toList()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("টুর্নামেন্ট তৈরি করো") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("বাতিল") }
    }
}
