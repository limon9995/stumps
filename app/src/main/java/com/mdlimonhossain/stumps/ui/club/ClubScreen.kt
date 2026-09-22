package com.mdlimonhossain.stumps.ui.club

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.local.db.club.ClubEntity
import com.mdlimonhossain.stumps.data.local.db.social.FollowTargetType
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.ListItemCard
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance
import kotlinx.coroutines.launch

/**
 * The "আমার ক্লাব" (my clubs) screen: a list of registered clubs, plus a form to register a new
 * one. `startWithRegisterForm` lets a caller (e.g. the "Register As Club" item in the side
 * drawer) jump straight into the registration form instead of showing the list first.
 */
@Composable
fun ClubScreen(uid: String, onBack: () -> Unit, startWithRegisterForm: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val viewModel: ClubViewModel = viewModel(factory = ClubViewModel.Factory(app.clubRepository, uid))
    val clubs by viewModel.clubs.collectAsState()
    var showRegister by remember { mutableStateOf(startWithRegisterForm) }

    if (showRegister) {
        RegisterClubForm(
            onCancel = { showRegister = false },
            onRegister = { name, city, year, ballType ->
                viewModel.registerClub(uid, name, city, year, ballType) { showRegister = false }
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "আমার ক্লাব", style = MaterialTheme.typography.headlineLarge)
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showRegister = true }, modifier = Modifier.fillMaxWidth()) {
            Text("নতুন ক্লাব রেজিস্টার করো")
        }
        Spacer(Modifier.height(16.dp))

        if (clubs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Place,
                title = "এখনো কোনো ক্লাব রেজিস্টার করা হয়নি",
                subtitle = "উপরের বাটনে চেপে তোমার ক্লাব রেজিস্টার করো।",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn {
                itemsIndexed(clubs, key = { _, c -> c.id }) { index, club ->
                    ClubRow(uid = uid, club = club, modifier = Modifier.staggeredEntrance(index))
                }
            }
        }
    }
}

/** One club card, with its own Follow/Unfollow button — same pattern as the Tournament Home tab's Follow button. */
@Composable
private fun ClubRow(uid: String, club: ClubEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    val isFollowing by app.followRepository.observeIsFollowing(uid, FollowTargetType.CLUB, club.id).collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val ballTypeLabel = if (club.ballType == "LEATHER") "লেদার বল" else "টেনিস বল"

    ListItemCard(
        modifier = modifier.padding(vertical = 6.dp),
        title = club.name,
        subtitle = "${club.city} • প্রতিষ্ঠিত ${club.establishedYear} • $ballTypeLabel",
        trailing = {
            TextButton(onClick = {
                scope.launch {
                    app.followRepository.toggleFollow(uid, FollowTargetType.CLUB, club.id, club.name, isFollowing)
                }
            }) { Text(if (isFollowing) "Following ✓" else "Follow") }
        }
    )
}

/** "ক্লাব/অর্গানাইজেশনের নাম, শহর, প্রতিষ্ঠার বছর, বলের ধরন" — matches the reference app's "Register as club" form fields. */
@Composable
private fun RegisterClubForm(onCancel: () -> Unit, onRegister: (name: String, city: String, year: Int, ballType: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var ballType by remember { mutableStateOf("LEATHER") }
    // Only starts showing red error text after the user has tapped "Register" at least once —
    // so a fresh, still-empty form doesn't look broken before they've even started typing.
    var attemptedSubmit by remember { mutableStateOf(false) }

    val year = yearText.toIntOrNull() ?: 0
    val nameMissing = name.isBlank()
    val cityMissing = city.isBlank()
    val yearInvalid = year !in 1800..2100
    val isValid = !nameMissing && !cityMissing && !yearInvalid

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Register as club", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "তোমার ক্লাব বা সংস্থাটি Stumps-এ রেজিস্টার করো — এতে তোমার সব টুর্নামেন্ট একই জায়গায় থাকবে।",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("ক্লাব/সংস্থার নাম") },
            isError = attemptedSubmit && nameMissing,
            supportingText = { if (attemptedSubmit && nameMissing) Text("ক্লাব/সংস্থার নাম লিখতে হবে") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("শহর") },
            isError = attemptedSubmit && cityMissing,
            supportingText = { if (attemptedSubmit && cityMissing) Text("শহরের নাম লিখতে হবে") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = yearText,
            onValueChange = { yearText = it },
            label = { Text("প্রতিষ্ঠার বছর") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = attemptedSubmit && yearInvalid,
            supportingText = { if (attemptedSubmit && yearInvalid) Text("সঠিক একটা সাল লিখো (১৮০০ থেকে ২১০০ এর মধ্যে)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "বলের ধরন", style = MaterialTheme.typography.titleMedium)
        Row {
            FilterChip(selected = ballType == "LEATHER", onClick = { ballType = "LEATHER" }, label = { Text("Leather Ball") })
            Spacer(Modifier.padding(4.dp))
            FilterChip(selected = ballType == "TENNIS", onClick = { ballType = "TENNIS" }, label = { Text("Tennis Ball") })
        }

        Spacer(Modifier.height(24.dp))
        if (attemptedSubmit && !isValid) {
            Text(
                text = "উপরে লাল করে দেখানো জায়গাগুলো ঠিক করো, তারপর আবার চেষ্টা করো।",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }
        // Always tappable now — tapping while something's missing just switches on the red
        // messages above instead of the button doing nothing with no explanation.
        Button(
            onClick = {
                attemptedSubmit = true
                if (isValid) onRegister(name, city, year, ballType)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("বাতিল") }
    }
}
