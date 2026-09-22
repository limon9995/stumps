package com.mdlimonhossain.stumps.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.domain.model.PlayerRole

/**
 * Collects a name and main playing role. Shown once, right after a brand new user's very first
 * login (with blank defaults, and no `onBack` — there's nowhere valid to go back to before a
 * name is set) — and ALSO reused for "Profile Edit" in Settings later on, this time pre-filled
 * with the user's CURRENT name/role via `initialName`/`initialRole`, AND given an `onBack` so
 * someone editing their profile can cancel out without saving anything.
 */
@Composable
fun ProfileSetupScreen(
    onComplete: (name: String, role: PlayerRole) -> Unit,
    initialName: String = "",
    initialRole: PlayerRole = PlayerRole.BATSMAN,
    onBack: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var role by remember { mutableStateOf(initialRole) }
    // Only shows the red "name is required" message after the user has tried to continue once.
    var attemptedSubmit by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Only shown when this screen is being used to EDIT an existing profile — the
        // very-first-login case has no sensible "back" destination, so onBack is null there.
        onBack?.let { TextButton(onClick = it) { Text("← ফিরে যাও") } }
        Text(text = "নিজের প্রোফাইল সেট করুন", style = MaterialTheme.typography.headlineLarge)
        Text(text = "এই তথ্য দিয়ে বাকি ইউজাররা তোমাকে চিনবে", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("তোমার নাম") },
            isError = attemptedSubmit && name.isBlank(),
            supportingText = { if (attemptedSubmit && name.isBlank()) Text("চালিয়ে যেতে তোমার নাম লিখতে হবে") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Text(text = "তোমার প্রধান role", style = MaterialTheme.typography.titleLarge)
        // PlayerRole.entries lists every value of the enum automatically — draw one selectable
        // row per role, so adding a new role later would show up here with no extra code needed.
        PlayerRole.entries.forEach { r ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = role == r, onClick = { role = r })
            ) {
                Row(role = r, selected = role == r)
            }
        }

        Spacer(Modifier.height(24.dp))
        // Always tappable now — tapping with a blank name just turns on the red message above
        // instead of the button silently refusing to do anything.
        Button(
            onClick = {
                attemptedSubmit = true
                if (name.isNotBlank()) onComplete(name, role)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("শুরু করি")
        }
    }
}

/** One row in the role picker: a radio dot plus the role's Bengali label. */
@Composable
private fun Row(role: PlayerRole, selected: Boolean) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // onClick = null here because the WHOLE row (the Column above) already handles taps
        // via .selectable — the radio button itself is just for show, not a second click target.
        RadioButton(selected = selected, onClick = null)
        Text(text = roleLabel(role))
    }
}

/** Turns a PlayerRole enum value into its Bengali display text. */
private fun roleLabel(role: PlayerRole): String = when (role) {
    PlayerRole.BATSMAN -> "ব্যাটসম্যান"
    PlayerRole.BOWLER -> "বোলার"
    PlayerRole.ALL_ROUNDER -> "অলরাউন্ডার"
    PlayerRole.WICKET_KEEPER -> "উইকেট-কিপার"
}
