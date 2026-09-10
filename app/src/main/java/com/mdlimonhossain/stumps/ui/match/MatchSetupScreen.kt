package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.domain.model.MatchFormat

/** Everything typed in on the "নতুন ম্যাচ" (new match) form, bundled up to hand off once the form is complete. */
data class QuickMatchInput(
    val teamAName: String,
    val teamAPlayers: List<String>,
    val teamBName: String,
    val teamBPlayers: List<String>,
    val oversLimit: Int,
    val tossWinnerIsTeamA: Boolean,
    val tossDecisionIsBat: Boolean,
    val format: MatchFormat = MatchFormat.CUSTOM
)

/** Turns a MatchFormat enum value into its short display label — reused by every "pick a format" chip row. */
fun matchFormatLabel(format: MatchFormat): String = when (format) {
    MatchFormat.T10 -> "T10"
    MatchFormat.T20 -> "T20"
    MatchFormat.ODI -> "ODI"
    MatchFormat.CLUB -> "Club"
    MatchFormat.CUSTOM -> "Custom"
}

/** The "set up a quick standalone match" form: two teams, players, overs, and the toss result. */
@Composable
fun MatchSetupScreen(onStartMatch: (QuickMatchInput) -> Unit, onBack: () -> Unit) {
    var teamAName by remember { mutableStateOf("") }
    var teamAPlayersText by remember { mutableStateOf("") }
    var teamBName by remember { mutableStateOf("") }
    var teamBPlayersText by remember { mutableStateOf("") }
    var oversText by remember { mutableStateOf("20") }
    var tossWinnerIsTeamA by remember { mutableStateOf(true) }
    var tossDecisionIsBat by remember { mutableStateOf(true) }
    var format by remember { mutableStateOf(MatchFormat.T20) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // verticalScroll lets the whole form scroll up and down — needed because this form
            // has more content than fits on one phone screen at once.
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Text(text = "নতুন ম্যাচ", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))

        Text(text = "দল ১", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = teamAName, onValueChange = { teamAName = it }, label = { Text("দলের নাম") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        // Players are typed in as one big block of text, one name per line — simpler than
        // building a whole "add player" button/list UI for this quick, throwaway match form.
        OutlinedTextField(
            value = teamAPlayersText,
            onValueChange = { teamAPlayersText = it },
            label = { Text("প্লেয়ারদের নাম (এক লাইনে একজন)") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text(text = "দল ২", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = teamBName, onValueChange = { teamBName = it }, label = { Text("দলের নাম") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = teamBPlayersText,
            onValueChange = { teamBPlayersText = it },
            label = { Text("প্লেয়ারদের নাম (এক লাইনে একজন)") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = oversText,
            onValueChange = { oversText = it },
            label = { Text("কত ওভারের ম্যাচ") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text(text = "ফরম্যাট", style = MaterialTheme.typography.titleLarge)
        // Purely a label the organizer picks for their own record-keeping — it doesn't change any
        // scoring rules, just gets saved on the match so Profile Statistics can later group by it.
        Row {
            MatchFormat.entries.forEach { f ->
                FilterChip(selected = format == f, onClick = { format = f }, label = { Text(matchFormatLabel(f)) })
                Spacer(Modifier.padding(2.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(text = "টস", style = MaterialTheme.typography.titleLarge)
        // FilterChip = a small tappable pill-shaped button that shows whether it's selected —
        // used here like a pair of radio buttons for "who won the toss".
        Row {
            FilterChip(
                selected = tossWinnerIsTeamA,
                onClick = { tossWinnerIsTeamA = true },
                label = { Text(teamAName.ifBlank { "দল ১" } + " জিতেছে") }
            )
            Spacer(Modifier.padding(4.dp))
            FilterChip(
                selected = !tossWinnerIsTeamA,
                onClick = { tossWinnerIsTeamA = false },
                label = { Text(teamBName.ifBlank { "দল ২" } + " জিতেছে") }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            FilterChip(selected = tossDecisionIsBat, onClick = { tossDecisionIsBat = true }, label = { Text("ব্যাটিং") })
            Spacer(Modifier.padding(4.dp))
            FilterChip(selected = !tossDecisionIsBat, onClick = { tossDecisionIsBat = false }, label = { Text("বোলিং") })
        }

        Spacer(Modifier.height(28.dp))
        // Turn the raw multi-line text boxes into clean lists of names: split by line, trim
        // any extra spaces, and throw away any blank lines.
        val teamAPlayers = teamAPlayersText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val teamBPlayers = teamBPlayersText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val overs = oversText.toIntOrNull() ?: 0
        // The "ম্যাচ শুরু করো" button only becomes tappable once the form actually makes sense:
        // both team names filled in, at least 2 players each, and a real overs number.
        val isValid = teamAName.isNotBlank() && teamBName.isNotBlank() &&
            teamAPlayers.size >= 2 && teamBPlayers.size >= 2 && overs > 0

        Button(
            enabled = isValid,
            onClick = {
                onStartMatch(
                    QuickMatchInput(
                        teamAName, teamAPlayers, teamBName, teamBPlayers,
                        overs, tossWinnerIsTeamA, tossDecisionIsBat, format
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ম্যাচ শুরু করো")
        }
    }
}
