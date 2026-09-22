package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.domain.model.MatchFormat
import kotlinx.coroutines.launch

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

/**
 * Which of the two team slots the "pick a saved team" dialog is currently filling in for —
 * null means the dialog is closed.
 */
private enum class TeamSlot { A, B }

/** The "set up a quick standalone match" form: two teams, players, overs, and the toss result. */
@Composable
fun MatchSetupScreen(uid: String, onStartMatch: (QuickMatchInput) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val scope = rememberCoroutineScope()
    // Every team this user has already saved under "আমার টিম" — offered as a shortcut so they
    // don't have to retype a whole squad's names by hand every time they start a match.
    val savedTeams by app.teamRepository.observeTeamsForUser(uid).collectAsState(initial = emptyList())

    var teamAName by remember { mutableStateOf("") }
    var teamAPlayersText by remember { mutableStateOf("") }
    var teamBName by remember { mutableStateOf("") }
    var teamBPlayersText by remember { mutableStateOf("") }
    var oversText by remember { mutableStateOf("20") }
    var tossWinnerIsTeamA by remember { mutableStateOf(true) }
    var tossDecisionIsBat by remember { mutableStateOf(true) }
    var format by remember { mutableStateOf(MatchFormat.T20) }
    // Which slot's "pick a saved team" dialog is open right now, if any.
    var pickerFor by remember { mutableStateOf<TeamSlot?>(null) }
    // Before the user taps "ম্যাচ শুরু করো" (start match) even once, we don't want to yell at
    // them with red error text on a brand-new, still-empty form. So we only start showing the
    // red "this is wrong" messages AFTER they've tried to submit at least once. This flag flips
    // to true the first time they tap the button while something is still missing.
    var attemptedSubmit by remember { mutableStateOf(false) }

    // Turn the raw multi-line text boxes into clean lists of names: split by line, trim
    // any extra spaces, and throw away any blank lines. We work these out up here (instead of
    // down near the button) because the error messages under each text field, further down,
    // need to know these numbers too.
    val teamAPlayers = teamAPlayersText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val teamBPlayers = teamBPlayersText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val overs = oversText.toIntOrNull() ?: 0
    // Each of these is "is this one thing wrong on its own" — used to decide which field gets
    // a red error message. A team is "too short" if it has fewer than 2 players, since you
    // can't really play cricket with just one person on a side.
    val teamANameMissing = teamAName.isBlank()
    val teamBNameMissing = teamBName.isBlank()
    val teamATooShort = teamAPlayers.size < 2
    val teamBTooShort = teamBPlayers.size < 2
    val oversInvalid = overs <= 0
    // The whole form only makes sense once none of the problems above are true.
    val isValid = !teamANameMissing && !teamBNameMissing && !teamATooShort && !teamBTooShort && !oversInvalid

    pickerFor?.let { slot ->
        SavedTeamPickerDialog(
            teams = savedTeams,
            onDismiss = { pickerFor = null },
            onPick = { team ->
                pickerFor = null
                // Fetching a team's roster is a suspend (database) call, so it has to run
                // inside a coroutine — rememberCoroutineScope gives us one tied to this screen.
                scope.launch {
                    val players = app.teamRepository.getPlayersOnce(team.id)
                    val namesText = players.joinToString("\n") { it.name }
                    if (slot == TeamSlot.A) {
                        teamAName = team.name
                        teamAPlayersText = namesText
                    } else {
                        teamBName = team.name
                        teamBPlayersText = namesText
                    }
                }
            }
        )
    }

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
        // Lets the organizer reuse a squad they already built under "আমার টিম" instead of
        // typing every name again — tapping it opens SavedTeamPickerDialog for slot A.
        OutlinedButton(onClick = { pickerFor = TeamSlot.A }, modifier = Modifier.fillMaxWidth()) {
            Text("সেভ করা টিম থেকে বেছে নাও")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = teamAName,
            onValueChange = { teamAName = it },
            label = { Text("দলের নাম") },
            // isError just turns the field's outline and label red — Compose does that part for us.
            // We only turn it on once the user has tried to submit, so a fresh empty form doesn't
            // look broken before they've even started typing.
            isError = attemptedSubmit && teamANameMissing,
            supportingText = {
                if (attemptedSubmit && teamANameMissing) {
                    Text("দল ১ এর নাম লিখতে হবে")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        // Players are typed in as one big block of text, one name per line — simpler than
        // building a whole "add player" button/list UI for this quick, throwaway match form.
        // Picking a saved team above fills this box in automatically, but it can still be
        // hand-edited afterwards (e.g. to drop a player who isn't playing today).
        OutlinedTextField(
            value = teamAPlayersText,
            onValueChange = { teamAPlayersText = it },
            label = { Text("প্লেয়ারদের নাম (এক লাইনে একজন)") },
            minLines = 4,
            isError = attemptedSubmit && teamATooShort,
            supportingText = {
                // This message tells them exactly how many more names they need to add, instead
                // of just saying "something's wrong" — much easier to fix.
                if (attemptedSubmit && teamATooShort) {
                    Text("অন্তত ২ জন প্লেয়ার লাগবে (এখন আছে ${teamAPlayers.size} জন)")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text(text = "দল ২", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { pickerFor = TeamSlot.B }, modifier = Modifier.fillMaxWidth()) {
            Text("সেভ করা টিম থেকে বেছে নাও")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = teamBName,
            onValueChange = { teamBName = it },
            label = { Text("দলের নাম") },
            isError = attemptedSubmit && teamBNameMissing,
            supportingText = {
                if (attemptedSubmit && teamBNameMissing) {
                    Text("দল ২ এর নাম লিখতে হবে")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = teamBPlayersText,
            onValueChange = { teamBPlayersText = it },
            label = { Text("প্লেয়ারদের নাম (এক লাইনে একজন)") },
            minLines = 4,
            isError = attemptedSubmit && teamBTooShort,
            supportingText = {
                if (attemptedSubmit && teamBTooShort) {
                    Text("অন্তত ২ জন প্লেয়ার লাগবে (এখন আছে ${teamBPlayers.size} জন)")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = oversText,
            onValueChange = { oversText = it },
            label = { Text("কত ওভারের ম্যাচ") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = attemptedSubmit && oversInvalid,
            supportingText = {
                if (attemptedSubmit && oversInvalid) {
                    Text("কত ওভারের ম্যাচ হবে সেটা ০ এর বেশি একটা সংখ্যায় লিখো")
                }
            },
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
        // If they've already tried to submit once and the form is still broken, show one more
        // plain-language summary above the button — so it's obvious at a glance that something
        // still needs fixing, on top of the specific red messages under each field.
        if (attemptedSubmit && !isValid) {
            Text(
                text = "উপরে লাল করে দেখানো জায়গাগুলো ঠিক করো, তারপর আবার চেষ্টা করো।",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            // The button is now ALWAYS tappable — no more silently greyed-out button that gives
            // no clue why it won't work. Tapping it while the form is incomplete just switches on
            // "attemptedSubmit", which lights up the red error messages above so the user can see
            // exactly what to fix.
            onClick = {
                attemptedSubmit = true
                if (isValid) {
                    onStartMatch(
                        QuickMatchInput(
                            teamAName, teamAPlayers, teamBName, teamBPlayers,
                            overs, tossWinnerIsTeamA, tossDecisionIsBat, format
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ম্যাচ শুরু করো")
        }
    }
}

/**
 * A small popup list of every team this user has saved, so they can pick one instead of typing
 * a whole squad's names by hand. Tapping a row hands that team back via `onPick` and the caller
 * closes the dialog; tapping outside or the "বাতিল" button just closes it with no change.
 */
@Composable
private fun SavedTeamPickerDialog(teams: List<TeamEntity>, onDismiss: () -> Unit, onPick: (TeamEntity) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সেভ করা টিম বেছে নাও") },
        text = {
            if (teams.isEmpty()) {
                // No saved teams yet — nothing to pick from, so just explain why the list is empty.
                Text("এখনো কোনো সেভ করা টিম নেই। আগে \"আমার টিম\" থেকে একটা টিম বানাও।")
            } else {
                // heightIn caps how tall the dialog can grow — if someone has a LOT of saved
                // teams, the list scrolls inside this fixed area instead of pushing the dialog
                // off the bottom of the screen.
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(teams, key = { it.id }) { team ->
                        TextButton(onClick = { onPick(team) }, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(team.name, style = MaterialTheme.typography.titleMedium)
                                team.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}
