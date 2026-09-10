package com.mdlimonhossain.stumps.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.remote.sync.PublicLiveMatch

/**
 * The "watch a live match without logging in" screen — a viewer types in a share code, and if
 * it matches a currently-live match, they see a real-time scoreboard. See
 * LiveBroadcastRepository.kt for how the actual live data gets from the scorer's phone to here.
 */
@Composable
fun WatchLiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var code by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) } // true while we're looking the code up in Firestore
    var notFound by remember { mutableStateOf(false) }
    var match by remember { mutableStateOf<PublicLiveMatch?>(null) } // set once a matching live match is found

    // Once a match is found, show the actual live scoreboard instead of the code-entry form.
    if (match != null) {
        LiveScoreboardScreen(match = match!!, repository = app.liveBroadcastRepository, onBack = { match = null })
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "লাইভ ম্যাচ দেখো", style = MaterialTheme.typography.headlineLarge)
        Text(text = "স্কোরার যে কোড শেয়ার করেছে সেটা লিখো — লগইন লাগবে না", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase(); notFound = false }, // always keep the code uppercase, matching how it was generated
            label = { Text("শেয়ার কোড") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = code.isNotBlank() && !searching,
            onClick = {
                searching = true
                notFound = false
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("দেখো") }

        // Only actually perform the lookup while `searching` is true — this LaunchedEffect
        // re-runs any time `code` changes while `searching` is true.
        if (searching) {
            LaunchedEffect(code) {
                val result = app.liveBroadcastRepository.findByShareCode(code)
                searching = false
                if (result == null) notFound = true else match = result
            }
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        if (notFound) {
            Spacer(Modifier.height(12.dp))
            Text(text = "এই কোডে কোনো লাইভ ম্যাচ পাওয়া যায়নি।", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) { Text("ফিরে যাও") }
    }
}

/** The actual live, auto-updating scoreboard once a valid share code has been found. */
@Composable
private fun LiveScoreboardScreen(
    match: PublicLiveMatch,
    repository: com.mdlimonhossain.stumps.data.remote.sync.LiveBroadcastRepository,
    onBack: () -> Unit
) {
    // produceState turns a Flow (repository.watchLatestInnings) into simple Compose state we
    // can read like any other variable — every time Firestore sends a new value, `innings`
    // updates and this screen automatically redraws itself with the fresh scoreboard.
    val innings by produceState(initialValue = null as com.mdlimonhossain.stumps.data.remote.sync.PublicLiveInnings?, match.matchId) {
        repository.watchLatestInnings(match.matchId).collect { value = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "${match.teamAName} vs ${match.teamBName}", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        val i = innings
        if (i == null) {
            CircularProgressIndicator()
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = i.battingTeamName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${i.state.totalRuns}/${i.state.totalWickets}  (${i.state.oversDisplay} ov)",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(text = "RR ${"%.2f".format(i.state.runRate)}", style = MaterialTheme.typography.bodyMedium)
                    i.targetRuns?.let { target ->
                        Spacer(Modifier.height(4.dp))
                        Text(text = "টার্গেট: $target", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "স্ট্রাইকার: ${i.state.strikerId ?: "?"}")
                Text(text = "${i.state.batsmanFigures[i.state.strikerId]?.runs ?: 0}")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "নন-স্ট্রাইকার: ${i.state.nonStrikerId ?: "?"}")
                Text(text = "${i.state.batsmanFigures[i.state.nonStrikerId]?.runs ?: 0}")
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "বোলার: ${i.state.currentBowlerId ?: "?"}", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) { Text("অন্য কোড দিয়ে দেখো") }
    }
}
