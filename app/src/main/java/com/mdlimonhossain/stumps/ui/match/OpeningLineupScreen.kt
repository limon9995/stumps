package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp

/**
 * Shown right before scoring starts (for both the 1st and 2nd innings) — picks who's opening
 * the batting (striker + non-striker) and who's bowling the first over.
 */
@Composable
fun OpeningLineupScreen(
    input: QuickMatchInput,
    onConfirm: (strikerName: String, nonStrikerName: String, bowlerName: String) -> Unit,
    onBack: () -> Unit
) {
    // Work out which team is batting vs bowling from the toss result, then pull the right player lists.
    val battingTeamPlayers = if (battingIsTeamA(input)) input.teamAPlayers else input.teamBPlayers
    val bowlingTeamPlayers = if (battingIsTeamA(input)) input.teamBPlayers else input.teamAPlayers
    val battingTeamName = if (battingIsTeamA(input)) input.teamAName else input.teamBName
    val bowlingTeamName = if (battingIsTeamA(input)) input.teamBName else input.teamAName

    // Pre-fill sensible defaults (first two batting players, first bowler) so the organizer
    // can just tap "শুরু করো" straight away if the obvious lineup is fine, or change them first.
    var striker by remember { mutableStateOf(battingTeamPlayers.getOrElse(0) { "" }) }
    var nonStriker by remember { mutableStateOf(battingTeamPlayers.getOrElse(1) { "" }) }
    var bowler by remember { mutableStateOf(bowlingTeamPlayers.getOrElse(0) { "" }) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Text(text = "শুরুর লাইনআপ", style = MaterialTheme.typography.headlineLarge)
        Text(text = "$battingTeamName ব্যাটিং করছে, $bowlingTeamName বোলিং", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        NameDropdown("স্ট্রাইকার ব্যাটসম্যান", battingTeamPlayers, striker) { striker = it }
        Spacer(Modifier.height(12.dp))
        // The non-striker dropdown excludes whoever is already picked as striker — one person
        // obviously can't bat at both ends at once.
        NameDropdown("নন-স্ট্রাইকার ব্যাটসম্যান", battingTeamPlayers.filter { it != striker }, nonStriker) { nonStriker = it }
        Spacer(Modifier.height(12.dp))
        NameDropdown("ওপেনিং বোলার", bowlingTeamPlayers, bowler) { bowler = it }

        Spacer(Modifier.height(28.dp))
        Button(
            enabled = striker.isNotBlank() && nonStriker.isNotBlank() && bowler.isNotBlank() && striker != nonStriker,
            onClick = { onConfirm(striker, nonStriker, bowler) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("স্কোরিং শুরু করো")
        }
    }
}

/**
 * Works out whether Team A is the one batting: true either if Team A won the toss and chose to
 * bat, OR if Team B won the toss but chose to BOWL (meaning Team A bats by default instead).
 * Used across the match-scoring screens, so keep this one function as the single source of truth.
 */
fun battingIsTeamA(input: QuickMatchInput): Boolean =
    (input.tossWinnerIsTeamA && input.tossDecisionIsBat) || (!input.tossWinnerIsTeamA && !input.tossDecisionIsBat)

/** A tap-to-pick dropdown for choosing one name from a list — used for all three player pickers above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameDropdown(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) } // is the dropdown list currently open?
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        // This text field LOOKS like a normal one, but readOnly = true means the user can't
        // type into it directly — tapping it just opens the dropdown below instead.
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}
